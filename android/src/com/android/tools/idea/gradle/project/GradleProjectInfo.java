/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.project.build.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.intellij.openapi.module.ModuleUtilCore.findModuleForFile;

public class GradleProjectInfo {
  @NotNull private final Project myProject;
  @NotNull private final AndroidProjectInfo myProjectInfo;
  @NotNull private final Application myApplication;

  private volatile boolean myIsNewlyCreatedProject;

  @NotNull
  public static GradleProjectInfo getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleProjectInfo.class);
  }

  public GradleProjectInfo(@NotNull Project project, @NotNull AndroidProjectInfo projectInfo) {
    this(project, projectInfo, ApplicationManager.getApplication());
  }

  @VisibleForTesting
  GradleProjectInfo(@NotNull Project project, @NotNull AndroidProjectInfo projectInfo, @NotNull Application application) {
    myProject = project;
    myProjectInfo = projectInfo;
    myApplication = application;
  }

  public boolean canUseLocalMavenRepo() {
    return myIsNewlyCreatedProject || myApplication.isInternal();
  }

  public boolean isNewlyCreatedProject() {
    return myIsNewlyCreatedProject;
  }

  public void setNewlyCreatedProject(boolean newlyCreatedProject) {
    myIsNewlyCreatedProject = newlyCreatedProject;
  }

  /**
   * Indicates whether the IDE should build the project by invoking Gradle directly, or through IDEA's JPS. By default Android Studio
   * invokes Gradle directly.
   *
   * @return {@code true} if the IDE should build the project by invoking Gradle directly; {@code false} otherwise.
   */
  public boolean isDirectGradleBuildEnabled() {
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(myProject);
    return buildConfiguration.USE_EXPERIMENTAL_FASTER_BUILD;
  }

  /**
   * Indicates whether Gradle is used to build at least one module in this project.
   * Note: {@link AndroidProjectInfo#requiresAndroidModel())} indicates whether a project requires an {@link AndroidModel}.
   * That method should be preferred in almost all cases. Use this method only if you explicitly need to check whether the model is
   * Gradle-specific.
   */
  public boolean isBuildWithGradle() {
    return ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() -> {
      ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      for (Module module : moduleManager.getModules()) {
        if (GradleFacet.isAppliedTo(module)) {
          return true;
        }
      }
      // See https://code.google.com/p/android/issues/detail?id=203384
      // This could be a project without modules. Check that at least it synced with Gradle.
      if (GradleSyncState.getInstance(myProject).getSummary().getSyncTimestamp() != -1L) {
        return true;
      }
      return hasTopLevelGradleBuildFile();
    });
  }

  /**
   * Indicates whether the project has a build.gradle file in the project's root folder.
   *
   * @return {@code true} if the project has a build.gradle file in the project's root folder; {@code false} otherwise.
   */
  public boolean hasTopLevelGradleBuildFile() {
    if (myProject.isDefault()) {
      return false;
    }
    File projectFolderPath = getBaseDirPath(myProject);
    File buildFilePath = new File(projectFolderPath, FN_BUILD_GRADLE);
    return buildFilePath.isFile();
  }

  /**
   * @return the modules in a Gradle-based project that contain an {@code AndroidFacet}.
   */
  @NotNull
  public List<Module> getAndroidModules() {
    ImmutableList.Builder<Module> modules = ImmutableList.builder();

    ReadAction.run(() -> {
      if (myProject.isDisposed()) {
        return;
      }

      for (Module module : ModuleManager.getInstance(myProject).getModules()) {
        if (AndroidFacet.getInstance(module) != null && GradleFacet.getInstance(module) != null) {
          modules.add(module);
        }
      }
    });
    return modules.build();
  }

  /**
   * Applies the given {@link Consumer} to each module in the project that contains an {@code AndroidFacet}.
   *
   * @param consumer the {@code Consumer} to apply.
   */
  public void forEachAndroidModule(@NotNull Consumer<AndroidFacet> consumer) {
    ReadAction.run(() -> {
      if (myProject.isDisposed()) {
        return;
      }

      for (Module module : ModuleManager.getInstance(myProject).getModules()) {
        AndroidFacet androidFacet = AndroidFacet.getInstance(module);
        if (androidFacet != null && GradleFacet.getInstance(module) != null) {
          consumer.consume(androidFacet);
        }
      }
    });
  }

  /**
   * Attempts to retrieve the {@link AndroidModuleModel} for the module containing the given file. If the module is not an Android module,
   * this method will return {@code null}.
   *
   * @param file the given file.
   * @return the {@code AndroidModuleModel} for the module containing the given file, or {@code null} if the module is not an Android
   * module.
   */
  @Nullable
  public AndroidModuleModel findAndroidModelInModule(@NotNull VirtualFile file) {
    Module module = findModuleForFile(file, myProject);
    if (module == null) {
      if (myProjectInfo.requiresAndroidModel()) {
        // You've edited a file that does not correspond to a module in a Gradle project; you are most likely editing a file in an excluded
        // folder under the build directory
        VirtualFile rootFolder = myProject.getBaseDir();
        if (rootFolder != null) {
          VirtualFile parent = file.getParent();
          while (parent != null && parent.equals(rootFolder)) {
            module = findModuleForFile(parent, myProject);
            if (module != null) {
              break;
            }
            parent = parent.getParent();
          }
        }
      }

      if (module == null) {
        return null;
      }
    }

    if (module.isDisposed()) {
      getLog().warn("Attempted to get an Android Facet from a disposed module");
      return null;
    }

    return AndroidModuleModel.get(module);
  }

  @NotNull
  private Logger getLog() {
    return Logger.getInstance(getClass());
  }
}
