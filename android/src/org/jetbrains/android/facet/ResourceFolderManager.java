/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.facet;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.variant.view.BuildVariantView.BuildVariantSelectionChangeListener;

/**
 * The resource folder manager is responsible for returning the current set
 * of resource folders used in the project. It provides hooks for getting notified
 * when the set of folders changes (e.g. due to variant selection changes, or
 * the folder set changing due to the user editing the gradle files or after a
 * delayed project initialization), and it also provides some state caching between
 * IDE sessions such that before the gradle initialization is done, it returns
 * the folder set as it was before the IDE exited.
 */
public class ResourceFolderManager implements ModificationTracker {
  private final AndroidFacet myFacet;
  private List<VirtualFile> myResDirCache;
  private long myGeneration;
  private final List<ResourceFolderListener> myListeners = Lists.newArrayList();
  private boolean myVariantListenerAdded;
  private boolean myGradleInitListenerAdded;

  /**
   * Should only be constructed by {@link AndroidFacet}; others should obtain instance
   * via {@link AndroidFacet#getResourceFolderManager}
   */
  ResourceFolderManager(AndroidFacet facet) {
    myFacet = facet;
  }

  /** Notifies the resource folder manager that the resource folder set may have changed */
  public void invalidate() {
    List<VirtualFile> old = myResDirCache;
    myResDirCache = null;
    getFolders();
    if (!old.equals(myResDirCache)) {
      notifyChanged(old, myResDirCache);
    }
  }

  /**
   * Returns all resource directories, in the overlay order
   * <p>
   * TODO: This should be changed to be a {@code List<List<VirtualFile>>} in order to be
   * able to distinguish overlays (e.g. flavor directories) versus resource folders at
   * the same level where duplicates are NOT allowed: [[flavor1], [flavor2], [main1,main2]]
   *
   * @return a list of all resource directories
   */
  @NotNull
  public List<VirtualFile> getFolders() {
    if (myResDirCache == null) {
      myResDirCache = computeFolders();
    }

    return myResDirCache;
  }

  private List<VirtualFile> computeFolders() {
    if (myFacet.isGradleProject()) {
      JpsAndroidModuleProperties state = myFacet.getConfiguration().getState();
      IdeaAndroidProject ideaAndroidProject = myFacet.getIdeaAndroidProject();
      List<VirtualFile> resDirectories = new ArrayList<VirtualFile>();
      if (ideaAndroidProject == null) {
        // Read string property
        if (state != null) {
          String path = state.RES_FOLDERS_RELATIVE_PATH;
          if (path != null) {
            VirtualFileManager manager = VirtualFileManager.getInstance();
            // Deliberately using ';' instead of File.pathSeparator; see comment later in code below which
            // writes the property
            for (String url : Splitter.on(';').omitEmptyStrings().trimResults().split(path)) {
              VirtualFile dir = manager.findFileByUrl(url);
              if (dir != null) {
                resDirectories.add(dir);
              }
            }
          } else {
            // First time; have not yet computed the res folders
            // just try the default: src/main/res/ (from Gradle templates), res/ (from exported Eclipse projects)
            String mainRes = '/' + FD_SOURCES + '/' + FD_MAIN + '/' + FD_RES;
            VirtualFile dir =  AndroidRootUtil.getFileByRelativeModulePath(myFacet.getModule(), mainRes, true);
            if (dir != null) {
              resDirectories.add(dir);
            } else {
              String res = '/' + FD_RES;
              dir =  AndroidRootUtil.getFileByRelativeModulePath(myFacet.getModule(), res, true);
              if (dir != null) {
                resDirectories.add(dir);
              }
            }
          }
        }

        // Add notification listener for when the project is initialized so we can update the
        // resource set, if necessary
        if (!myGradleInitListenerAdded) {
          myGradleInitListenerAdded = true; // Avoid adding multiple listeners if we invalidate and call this repeatedly around startup
          myFacet.addListener(new AndroidFacet.GradleProjectAvailableListener() {
            @Override
            public void gradleProjectAvailable(@NotNull IdeaAndroidProject project) {
              myFacet.removeListener(this);
              invalidate();
            }
          });
        }
      } else {
        resDirectories.addAll(myFacet.getMainIdeaSourceSet().getResDirectories());
        List<IdeaSourceProvider> flavorSourceSets = myFacet.getIdeaFlavorSourceSets();
        if (flavorSourceSets != null) {
          for (IdeaSourceProvider provider : flavorSourceSets) {
            resDirectories.addAll(provider.getResDirectories());
          }
        }

        IdeaSourceProvider buildTypeSourceSet = myFacet.getIdeaBuildTypeSourceSet();
        if (buildTypeSourceSet != null) {
          resDirectories.addAll(buildTypeSourceSet.getResDirectories());
        }

        // Write string property such that subsequent restarts can look up the most recent list
        // before the gradle model has been initialized asynchronously
        if (state != null) {
          StringBuilder path = new StringBuilder(400);
          for (VirtualFile dir : resDirectories) {
            if (path.length() != 0) {
              // Deliberately using ';' instead of File.pathSeparator since on Unix File.pathSeparator is ":"
              // which is also used in URLs, meaning we could end up with something like "file://foo:file://bar"
              path.append(';');
            }
            path.append(dir.getUrl());
          }
          state.RES_FOLDERS_RELATIVE_PATH = path.toString();
        }

        // Also refresh the project resources whenever the variant changes
        if (!myVariantListenerAdded) {
          myVariantListenerAdded = true;
          BuildVariantView.getInstance(myFacet.getModule().getProject()).addListener(new BuildVariantSelectionChangeListener() {
            @Override
            public void buildVariantSelected(@NotNull AndroidFacet facet) {
              invalidate();
            }
          });
        }
      }

      return resDirectories;
    } else {
      return new ArrayList<VirtualFile>(myFacet.getMainIdeaSourceSet().getResDirectories());
    }
  }

  private void notifyChanged(@NotNull List<VirtualFile> before, @NotNull List<VirtualFile> after) {
    myGeneration++;
    Set<VirtualFile> added = new HashSet<VirtualFile>(after.size());
    added.addAll(after);
    added.removeAll(before);

    Set<VirtualFile> removed = new HashSet<VirtualFile>(before.size());
    removed.addAll(before);
    removed.removeAll(after);

    for (ResourceFolderListener listener : new ArrayList<ResourceFolderListener>(myListeners)) {
      listener.resourceFoldersChanged(myFacet, after, added, removed);
    }
  }

  @Override
  public long getModificationCount() {
    return myGeneration;
  }

  public synchronized void addListener(@NotNull ResourceFolderListener listener) {
    myListeners.add(listener);
  }

  public synchronized void removeListener(@NotNull ResourceFolderListener listener) {
    myListeners.remove(listener);
  }

  /** Adds in any AAR library resource directories found in the library definitions for the given facet */
  public static void addAarsFromModuleLibraries(@NotNull AndroidFacet facet, @NotNull Set<File> dirs) {
    Module module = facet.getModule();
    OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrSdkOrderEntry) {
        if (orderEntry.isValid() && orderEntry.getPresentableName().endsWith(DOT_AAR)) {
          final LibraryOrSdkOrderEntry entry = (LibraryOrSdkOrderEntry)orderEntry;
          final VirtualFile[] libClasses = entry.getRootFiles(OrderRootType.CLASSES);
          File res = null;
          for (VirtualFile root : libClasses) {
            if (root.getName().equals(FD_RES)) {
              res = VfsUtilCore.virtualToIoFile(root);
              break;
            }
          }

          if (res == null) {
            for (VirtualFile root : libClasses) {
              // Switch to file IO: The root may be inside a jar file system, where
              // getParent() returns null (and to get the real parent is ugly;
              // e.g. ((PersistentFSImpl.JarRoot)root).getParentLocalFile()).
              // Besides, we need the java.io.File at the end of this anyway.
              File file = new File(VfsUtilCore.virtualToIoFile(root).getParentFile(), FD_RES);
              if (file.exists()) {
                res = file;
                break;
              }
            }
          }

          if (res != null) {
            dirs.add(res);
          }
        }
      }
    }
  }

  /** Listeners for resource folder changes */
  public interface ResourceFolderListener {
    /** The resource folders in this project has changed */
    void resourceFoldersChanged(@NotNull AndroidFacet facet,
                                @NotNull List<VirtualFile> folders,
                                @NotNull Collection<VirtualFile> added,
                                @NotNull Collection<VirtualFile> removed);
  }
}
