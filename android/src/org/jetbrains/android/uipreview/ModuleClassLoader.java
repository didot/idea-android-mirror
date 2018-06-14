package org.jetbrains.android.uipreview;

import com.android.builder.model.AaptOptions;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.AndroidProjectModelUtils;
import com.android.tools.idea.editors.theme.ThemeEditorProvider;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.ClassJarProvider;
import com.android.tools.idea.rendering.RenderClassLoader;
import com.android.tools.idea.rendering.RenderSecurityManager;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceClassRegistry;
import com.android.tools.idea.res.ResourceIdManager;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.android.utils.SdkUtils;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.LogAnonymizerUtil.anonymize;
import static com.android.tools.idea.LogAnonymizerUtil.anonymizeClassName;

/**
 * Render class loader responsible for loading classes in custom views
 * and local and library classes used by those custom views (other than
 * the framework itself, which is loaded by a parent class loader via
 * layout lib.)
 */
public final class ModuleClassLoader extends RenderClassLoader {
  private static final Logger LOG = Logger.getInstance(ModuleClassLoader.class);

  /** The base module to use as a render context; the class loader will consult the module dependencies and library dependencies
   * of this class as well to find classes */
  private final WeakReference<Module> myModuleReference;

  /** The layout library to use as a root class loader (e.g. the place to obtain the layoutlib Android SDK view classes from */
  private final LayoutLibrary myLibrary;

  /** Map from fully qualified class name to the corresponding .class file for each class loaded by this class loader */
  private Map<String, VirtualFile> myClassFiles;
  /** Map from fully qualified class name to the corresponding last modified info for each class loaded by this class loader */
  private Map<String, ClassModificationTimestamp> myClassFilesLastModified;

  private static class ClassModificationTimestamp {
    public final long timestamp;
    public final long length;

    public ClassModificationTimestamp(long timestamp, long length) {
      this.timestamp = timestamp;
      this.length = length;
    }
  }

  private ModuleClassLoader(@NotNull LayoutLibrary library, @NotNull Module module) {
    super(library.getClassLoader(), library.getApiLevel());
    myLibrary = library;
    myModuleReference = new WeakReference<>(module);

    getLibraryJarFiles(getExternalLibraries(module))
      .forEach(jarFile -> registerLibraryResourceFiles(module, jarFile));
  }

  @NotNull
  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("findClass(%s)", name));
    }

    Module module = myModuleReference.get();
    try {
      if (!myInsideJarClassLoader) {
        if (module != null) {
          if (isResourceClassName(name)) {
            AndroidFacet facet = AndroidFacet.getInstance(module);
            if (facet != null) {
              ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getOrCreateInstance(facet);
              byte[] data = ResourceClassRegistry.get(module.getProject()).findClassDefinition(name, repositoryManager);
              if (data != null) {
                data = convertClass(data);
                if (LOG.isDebugEnabled()) {
                  LOG.debug("  Defining class from AAR registry");
                }
                return defineClassAndPackage(name, data, 0, data.length);
              }
            }
            else if (LOG.isDebugEnabled()) {
              LOG.debug("  LocalResourceRepositoryInstance not found");
            }
          }
        }
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("  super.findClass(%s)", anonymizeClassName(name)));
      }
      return super.findClass(name);
    } catch (ClassNotFoundException e) {
      byte[] clazz = null;
      if (RecyclerViewHelper.CN_CUSTOM_ADAPTER.equals(name)) {
        clazz = RecyclerViewHelper.getAdapterClass(DependencyManagementUtil.mapAndroidxName(module, CLASS_RECYCLER_VIEW_V7),
                                                   DependencyManagementUtil.mapAndroidxName(module, CLASS_RECYCLER_VIEW_VIEW_HOLDER),
                                                   DependencyManagementUtil.mapAndroidxName(module, CLASS_RECYCLER_VIEW_ADAPTER));
      }
      if (RecyclerViewHelper.CN_CUSTOM_VIEW_HOLDER.equals(name)) {
        clazz = RecyclerViewHelper.getViewHolder(DependencyManagementUtil.mapAndroidxName(module, CLASS_RECYCLER_VIEW_V7),
                                                 DependencyManagementUtil.mapAndroidxName(module, CLASS_RECYCLER_VIEW_VIEW_HOLDER),
                                                 DependencyManagementUtil.mapAndroidxName(module, CLASS_RECYCLER_VIEW_ADAPTER));
      }
      if (clazz != null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("  Defining RecyclerView helper class");
        }
        return defineClassAndPackage(name, clazz, 0, clazz.length);
      }
      LOG.debug(e);
      throw e;
    }
  }

  @Nullable
  public static ClassLoader create(IAndroidTarget target, Module module) throws Exception {
    AndroidPlatform androidPlatform = AndroidPlatform.getInstance(module);
    if (androidPlatform == null) {
      return null;
    }
    AndroidTargetData targetData = androidPlatform.getSdkData().getTargetData(target);
    LayoutLibrary library = targetData.getLayoutLibrary(module.getProject());
    if (library == null) {
      return null;
    }

    return get(library, module);
  }

  @NotNull
  @Override
  protected Class<?> load(String name) throws ClassNotFoundException {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("load(%s)", anonymizeClassName(name)));
    }

    Module module = myModuleReference.get();
    if (module == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("  ClassNotFoundException(%s)", name));
      }
      throw new ClassNotFoundException(name);
    }
    Class<?> aClass = loadClassFromModuleOrDependency(module, name, new HashSet<>());

    if (aClass == null) {
      aClass = loadClassFromJar(name);
    }

    if (aClass != null) {
      return aClass;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("  ClassNotFoundException(%s)", anonymizeClassName(name)));
    }
    throw new ClassNotFoundException(name);
  }

  @Override
  public Class<?> loadClass(String name) throws ClassNotFoundException {
    // We overload loadClass() to load a class defined in a project
    // rather than a version of it that may already be present in Studio.
    // This is an issue if Studio shares a library or classes with some android code
    // we are trying to preview; the class loaded would be the one already used by
    // Studio rather than the one packaged with the library.
    // If they end up being different (say the lib is more recent than the Studio version),
    // it will likely result in broken preview as functions would be different / not present.
    // The only known case of this at this point is ConstraintLayout (a solver library
    // is used both by the android implementation and by Android Studio).

    // FIXME: While testing this approach, we found an issue on some Windows machine where
    // class loading would be broken. Thus, we limit the fix to the impacted solver classes
    // for now, until we can investigate the problem more in depth on Windows.
    boolean loadFromProject = name.startsWith("android.support.constraint.solver");
    if (loadFromProject) {
      try {
        // Give priority to loading class from this Class Loader. This will avoid leaking classes from the plugin
        // into the project.
        Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass != null) {
          return loadedClass;
        }
        return load(name);
      }
      catch (Exception ignore) {
        // Catch-all, defer to the parent implementation
      }
    }
    return super.loadClass(name);
  }

  @Nullable
  private Class<?> loadClassFromModuleOrDependency(Module module, String name, Set<Module> visited) {
    if (!visited.add(module)) {
      return null;
    }

    if (module.isDisposed()) {
      return null;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("loadClassFromModuleOrDependency(%s, %s)", anonymize(module), anonymizeClassName(name)));
    }

    Class<?> aClass = loadClassFromModule(module, name);
    if (aClass != null) {
      return aClass;
    }

    for (Module depModule : ModuleRootManager.getInstance(module).getDependencies(false)) {
      aClass = loadClassFromModuleOrDependency(depModule, name, visited);
      if (aClass != null) {
        return aClass;
      }
    }
    return null;
  }

  @Nullable
  private Class<?> loadClassFromModule(Module module, String name) {
    if (module.isDisposed()) {
      return null;
    }

    final CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
    if (extension == null) {
      return null;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("loadClassFromModule(%s, %s)", anonymize(module), anonymizeClassName(name)));
    }
    VirtualFile vOutFolder = extension.getCompilerOutputPath();
    VirtualFile classFile = null;
    if (vOutFolder != null) {
      classFile = ClassJarProvider.findClassFileInPath(vOutFolder, name);
    }

    if (classFile == null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && facet.requiresAndroidModel()) {
        AndroidModel androidModel = facet.getConfiguration().getModel();
        if (androidModel != null) {
          classFile = androidModel.getClassJarProvider().findModuleClassFile(name, module);
        }
      }
    }

    if (classFile != null) {
      return loadClassFile(name, classFile);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("  Class not found");
    }
    return null;
  }

  /**
   * Determines whether the class specified by the given qualified name has a source file in the IDE that
   * has been edited more recently than its corresponding class file.
   * <p/>This method requires the indexing to have finished
   * <p/><b>Note that this method can only answer queries for classes that this class loader has previously
   * loaded!</b>
   *
   * @param fqcn the fully qualified class name
   * @param myCredential a render sandbox credential
   * @return true if the source file has been modified, or false if not (or if the source file cannot be found)
   */
  public boolean isSourceModified(@NotNull final String fqcn, @Nullable final Object myCredential) {
    // Don't flag R.java edits; not done by user
    if (isResourceClassName(fqcn)) {
      return false;
    }

    Module module = myModuleReference.get();
    if (module == null) {
      return false;
    }
    VirtualFile classFile = getClassFile(fqcn);

    // Make sure the class file is up to date and if not, log an error
    if (classFile == null) {
      return false;
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null || facet.getConfiguration().getModel() == null) {
      return false;
    }
    // Allow file system access for timestamps.
    boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
    try {
      return facet.getConfiguration().getModel().isClassFileOutOfDate(module, fqcn, classFile);
    } finally {
      RenderSecurityManager.exitSafeRegion(token);
    }
  }

  // matches foo.bar.R or foo.bar.R$baz
  private static final Pattern RESOURCE_CLASS_NAME = Pattern.compile(".+\\.R(\\$[^.]+)?$");

  private static boolean isResourceClassName(@NotNull String className) {
    return RESOURCE_CLASS_NAME.matcher(className).matches();
  }

  @Override
  @Nullable
  protected Class<?> loadClassFile(final String fqcn, @NotNull VirtualFile classFile) {
    if (myClassFiles == null) {
      myClassFiles = Maps.newHashMap();
      myClassFilesLastModified = Maps.newHashMap();
    }
    myClassFiles.put(fqcn, classFile);
    myClassFilesLastModified.put(fqcn,
                                 new ClassModificationTimestamp(classFile.getTimeStamp(), classFile.getLength()));

    return super.loadClassFile(fqcn, classFile);
  }

  /**
   * Returns a {@link Stream<File>} of the referenced libraries for the {@link Module} of this class loader.
   */
  @NotNull
  private static Stream<File> getExternalLibraries(Module module) {
    if (module == null) {
      return Stream.empty();
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null && facet.requiresAndroidModel()) {
      AndroidModel model = facet.getConfiguration().getModel();
      if (model != null) {
        return model.getClassJarProvider().getModuleExternalLibraries(module).stream();
      }
    }

    return AndroidRootUtil.getExternalLibraries(module).stream().map(VfsUtilCore::virtualToIoFile);
  }

  private static void registerLibraryResourceFiles(@NotNull Module module, @NotNull File jarFile) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return;
    }

    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getOrCreateInstance(facet);
    LocalResourceRepository appResources = repositoryManager.getAppResources(true);

    File resourcesDirectory = AndroidProjectModelUtils.findResFolder(jarFile);
    if (resourcesDirectory == null) return;

    ResourceClassRegistry registry = ResourceClassRegistry.get(module.getProject());

    // Choose which resources should be in the generated R class. This is described in the JavaDoc of ResourceClassGenerator.
    AbstractResourceRepository rClassContents;
    ResourceNamespace resourcesNamespace;
    String packageName;
    if (repositoryManager.getNamespacing() == AaptOptions.Namespacing.DISABLED) {
      try {
        packageName = AndroidManifestUtils.getAarPackageName(resourcesDirectory);
        if (packageName == null) {
          return;
        }
      }
      catch (IOException e) {
        return;
      }
      rClassContents = appResources;
      resourcesNamespace = ResourceNamespace.RES_AUTO;
    }
    else {
      AbstractResourceRepository aarResources = repositoryManager.findRepositoryFor(resourcesDirectory);
      if (!(aarResources instanceof SingleNamespaceResourceRepository)) {
        return;
      }

      rClassContents = aarResources;
      SingleNamespaceResourceRepository resources = (SingleNamespaceResourceRepository)aarResources;
      resourcesNamespace = resources.getNamespace();
      packageName = resources.getPackageName();
    }

    registry.addLibrary(rClassContents, ResourceIdManager.get(module), packageName, resourcesNamespace);
  }

  /**
   * Returns a {@link Stream<File>} of the JAR files for the given external libraries.
   */
  @NotNull
  private static Stream<File> getLibraryJarFiles(@NotNull Stream<File> externalLibraries) {
    return externalLibraries
      .filter(vFile -> EXT_JAR.equals(Files.getFileExtension(vFile.getName())))
      .filter(File::exists);
  }

  /**
   * Returns the list of external JAR files referenced by the class loader. This method will also register any resource files in the
   * referenced AARs that haven't been registered before.
   */
  @Override
  protected List<URL> getExternalJars() {
    final Module module = myModuleReference.get();
    if (module == null) {
      return Collections.emptyList();
    }

    final List<URL> result = new ArrayList<>();

    if (ThemeEditorProvider.THEME_EDITOR_ENABLE) {
      URL customWidgetsUrl = ThemeEditorUtils.getCustomWidgetsJarUrl();

      if (customWidgetsUrl != null) {
        result.add(customWidgetsUrl);
      }
    }

    getLibraryJarFiles(getExternalLibraries(module))
      .peek(jarFile -> {
        try {
          result.add(SdkUtils.fileToUrl(jarFile));
        }
        catch (MalformedURLException e) {
          LOG.error(e);
        }
      })
      .forEach(jarFile -> registerLibraryResourceFiles(module, jarFile));

    return result;
  }

  /** Returns the path to a class file loaded for the given class, if any */
  @Nullable
  private VirtualFile getClassFile(@NotNull String className) {
    if (myClassFiles == null) {
      return null;
    }
    VirtualFile file = myClassFiles.get(className);
    if (file == null) {
      return null;
    }
    return file.isValid() ? file : null;
  }

  /** Checks whether any of the .class files loaded by this loader have changed since the creation of this class loader */
  private boolean isUpToDate() {
    if (myClassFiles != null) {
      for (Map.Entry<String, VirtualFile> entry : myClassFiles.entrySet()) {
        String className = entry.getKey();
        VirtualFile classFile = entry.getValue();
        if (!classFile.isValid()) {
          return false;
        }
        ClassModificationTimestamp lastModifiedStamp = myClassFilesLastModified.get(className);
        if (lastModifiedStamp != null) {
          long loadedModifiedTime = lastModifiedStamp.timestamp;
          long loadedModifiedLength = lastModifiedStamp.length;
          long classFileModifiedTime = classFile.getTimeStamp();
          long classFileModifiedLength = classFile.getLength();
          if ((classFileModifiedTime > 0L && loadedModifiedTime > 0L && loadedModifiedTime < classFileModifiedTime)
             || loadedModifiedLength != classFileModifiedLength) {
            return false;
          }
        }
      }
    }

    return true;
  }

  /**
   * Returns a project class loader to use for rendering. May cache instances across render sessions.
   */
  @NotNull
  public static ModuleClassLoader get(@NotNull LayoutLibrary library, @NotNull Module module) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("ModuleClassLoader.get(%s)", anonymize(module)));
    }

    ModuleClassLoader loader = ourCache.get(module);
    if (loader != null) {
      if (library != loader.myLibrary) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("  Discarding loader because the layout library has changed");
        }
        loader = null;
      } else if (!loader.isUpToDate()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("  Discarding loader because some files have changed");
        }
        loader = null;
      } else {
        List<URL> updatedJarDependencies = loader.getExternalJars();
        if (loader.myJarClassLoader != null && !updatedJarDependencies.equals(loader.myJarClassLoader.getUrls())) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("  Recreating jar class loader because dependencies have changed.");
          }
          loader.myJarClassLoader = loader.createClassLoader(updatedJarDependencies);
        }
      }

      // To be correct we should also check that the dependencies have not changed. It's not
      // a problem when you add a new dependency with classes that haven't yet been resolved,
      // but if for some reason a class is defined in multiple dependencies, and we've already
      // resolved that class, changing the order could change the particular class that should
      // be loaded, and we would need to recreate the loader - but this won't detect that.
      // To solve this correctly we'd need to store a key in the cache recording the project
      // dependencies when the loader was created, and compare it to the current one. However,
      // that's a pretty unusual scenario so we won't worry about it until the loader situation
      // is solved more generally (e.g. separating out .jar loading from .class loading etc).
    }

    if (loader == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("  New class loader");
      }
      loader = new ModuleClassLoader(library, module);
      ourCache.put(module, loader);
    } else if (LOG.isDebugEnabled()) {
      LOG.debug("  Re-used class loader");
    }

    return loader;
  }

  /** Flush any cached class loaders */
  public static void clearCache() {
    ourCache.clear();
  }

  /** Remove the cached class loader for the module. */
  public static void clearCache(Module module) {
    ourCache.remove(module);
  }

  public boolean isClassLoaded(String className) {
    return findLoadedClass(className) != null;
  }

  /** Temporary hack: Store this in a weak hash map cached by modules. In the next version we should move this
   * into a proper persistent render service. */
  private static Map<Module,ModuleClassLoader> ourCache = ContainerUtil.createWeakMap();
}
