/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.templates.recipe

import com.android.SdkConstants.ATTR_CONTEXT
import com.android.SdkConstants.DOT_FTL
import com.android.SdkConstants.DOT_GRADLE
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.SdkConstants.TOOLS_URI
import com.android.ide.common.repository.GradleVersion
import com.android.resources.ResourceFolderType
import com.android.support.AndroidxNameUtils
import com.android.tools.idea.Projects.getBaseDirPath
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel
import com.android.tools.idea.gradle.dsl.api.util.LanguageLevelUtil
import com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile
import com.android.tools.idea.templates.FmGetConfigurationNameMethod
import com.android.tools.idea.templates.FreemarkerUtils.TemplateProcessingException
import com.android.tools.idea.templates.FreemarkerUtils.TemplateUserVisibleException
import com.android.tools.idea.templates.FreemarkerUtils.processFreemarkerTemplate
import com.android.tools.idea.templates.RenderingContextAdapter
import com.android.tools.idea.templates.RepositoryUrlManager
import com.android.tools.idea.templates.TemplateAttributes.ATTR_ANDROIDX_SUPPORT
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APPLICATION_PACKAGE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BASE_FEATURE_DIR
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_API
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_API_STRING
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_DYNAMIC_FEATURE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_NEW_MODULE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MODULE_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_PACKAGE_NAME
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DEPENDENCIES_MULTIMAP
import com.android.tools.idea.templates.TemplateUtils.hasExtension
import com.android.tools.idea.templates.TemplateUtils.readTextFromDisk
import com.android.tools.idea.templates.TemplateUtils.readTextFromDocument
import com.android.tools.idea.templates.mergeXml
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor2.DryRunRecipeIO
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor2.RecipeIO
import com.android.tools.idea.templates.resolveDependency
import com.android.utils.XmlUtils.XML_PROLOG
import com.android.utils.findGradleBuildFile
import com.google.common.base.Strings.isNullOrEmpty
import com.google.common.base.Strings.nullToEmpty
import com.google.common.collect.SetMultimap
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.XmlElementFactory
import com.intellij.util.LineSeparator
import freemarker.template.Configuration
import java.io.File
import java.io.IOException

/**
 * Executor support for recipe instructions.
 */
class DefaultRecipeExecutor(private val context: RenderingContext, dryRun: Boolean) : RecipeExecutor {
  private val referencesExecutor: FindReferencesRecipeExecutor = FindReferencesRecipeExecutor(context)
  private val io: RecipeIO = if (dryRun) DryRunRecipeIO() else RecipeIO()
  private val readonlyStatusHandler: ReadonlyStatusHandler = ReadonlyStatusHandler.getInstance(context.project)

  private val paramMap: Map<String, Any>
    get() = context.paramMap

  internal val freemarker: Configuration
    get() = context.freemarkerConfiguration

  override fun applyPlugin(plugin: String) {
    val name = plugin.trim()
    referencesExecutor.applyPlugin(name)

    val project = context.project
    val buildFile = getBuildFilePath(context)
    val buildModel = getBuildModel(buildFile, project)
    if (buildModel != null) {
      if (buildModel.plugins().stream().noneMatch { x -> x.name().forceString() == name }) {
        buildModel.applyPlugin(name)
        io.applyChanges(buildModel)
      }
      return
    }

    // The attempt above to add the plugin using the GradleBuildModel failed, now attempt to add the plugin by appending the string.
    val destinationContents = if (buildFile.exists()) nullToEmpty(readTextFile(buildFile)) else ""
    val applyPluginStatement = "apply plugin: '$name'"
    val result = io.mergeBuildFiles(applyPluginStatement, destinationContents, project, "")
    try {
      io.writeFile(this, result, buildFile)
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  override fun addSourceSet(type: String, name: String, dir: String) {
    val buildFile = getBuildFilePath(context)
    val buildModel = getBuildModel(buildFile, context.project) ?: return
    val sourceSet = buildModel.android().addSourceSet(name)

    if (type == "manifest") {
      sourceSet.manifest().srcFile().setValue(dir)
      io.applyChanges(buildModel)
      return
    }

    val srcDirsModel = with(sourceSet) {
      when (type) {
        "aidl" -> aidl()
        "assets" -> assets()
        "java" -> java()
        "jni" -> jni()
        "renderscript" -> renderscript()
        "res" -> res()
        "resources" -> resources()
        else -> throw IllegalArgumentException("Unknown source set category $type")
      }
    }.srcDirs()

    val dirExists = srcDirsModel.toList().orEmpty().any { it.toString() == dir }

    if (dirExists) {
      return
    }

    srcDirsModel.addListValue().setValue(dir)
    io.applyChanges(buildModel)
  }

  override fun setExtVar(name: String, value: String) {
    val rootBuildFile = findGradleBuildFile(getBaseDirPath(context.project))
    val buildModel = getBuildModel(rootBuildFile, context.project) ?: return
    val property = buildModel.buildscript().ext().findProperty(name)
    if (property.valueType != GradlePropertyModel.ValueType.NONE) {
      return
    }
    property.setValue(value)
    io.applyChanges(buildModel)
  }

  override fun addClasspath(mavenUrl: String) {
    val mavenUrl = resolveDependency(RepositoryUrlManager.get(), mavenUrl.trim())

    referencesExecutor.addClasspath(mavenUrl)

    val toBeAddedDependency = ArtifactDependencySpec.create(mavenUrl) ?:
                              throw RuntimeException("$mavenUrl is not a valid classpath dependency")

    val project = context.project
    val rootBuildFile = findGradleBuildFile(getBaseDirPath(project))
    val buildModel = getBuildModel(rootBuildFile, project)
    if (buildModel != null) {
      val buildscriptDependencies = buildModel.buildscript().dependencies()
      var targetDependencyModel: ArtifactDependencyModel? = null
      for (dependencyModel in buildscriptDependencies.artifacts(CLASSPATH_CONFIGURATION_NAME)) {
        if (toBeAddedDependency.equalsIgnoreVersion(ArtifactDependencySpec.create(dependencyModel))) {
          targetDependencyModel = dependencyModel
        }
      }
      if (targetDependencyModel == null) {
        buildscriptDependencies.addArtifact(CLASSPATH_CONFIGURATION_NAME, toBeAddedDependency)
      }
      else {
        val toBeAddedDependencyVersion = GradleVersion.parse(nullToEmpty(toBeAddedDependency.version))
        val existingDependencyVersion = GradleVersion.parse(nullToEmpty(targetDependencyModel.version().toString()))
        if (toBeAddedDependencyVersion > existingDependencyVersion) {
          targetDependencyModel.version().setValue(nullToEmpty(toBeAddedDependency.version))
        }
      }
      io.applyChanges(buildModel)
      return
    }

    val sep = LineSeparator.getSystemLineSeparator().separatorString
    fun formatClasspath(dependency: String) =
      "buildscript {" + sep +
      "  dependencies {" + sep +
      "    classpath '" + dependency + "'" + sep +
      "  }" + sep +
      "}" + sep

    // The attempt above to merge the classpath using the GradleBuildModel failed, now attempt to merge the classpaths by merging the files.
    val destinationContents = if (rootBuildFile.exists()) nullToEmpty(readTextFile(rootBuildFile)) else ""
    val result = io.mergeBuildFiles(formatClasspath(mavenUrl), destinationContents, project, "")
    try {
      io.writeFile(this, result, rootBuildFile)
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  /**
   * Add a library dependency into the project.
   */
  override fun addDependency(configuration: String, mavenUrl: String) {
    // Translate from "configuration" to "implementation" based on the parameter map context
    val configuration = FmGetConfigurationNameMethod.convertConfiguration(paramMap, configuration)

    referencesExecutor.addDependency(configuration, mavenUrl)

    val dependencyList = paramMap[ATTR_DEPENDENCIES_MULTIMAP] as SetMultimap<String, String>
    dependencyList.put(configuration, mavenUrl)
  }

  override fun addModuleDependency(configuration: String, moduleName: String, toModule: String) {
    require(moduleName.isNotEmpty() && moduleName.first() != ':') {
      "incorrect module name (it should not be empty or include first ':')"
    }
    // Translate from "configuration" to "implementation" based on the parameter map context
    val configuration = FmGetConfigurationNameMethod.convertConfiguration(paramMap, configuration)

    val buildFile = findGradleBuildFile(File(toModule))
    val buildModel = getBuildModel(buildFile, context.project) ?: return
    buildModel.dependencies().addModule(configuration, ":$moduleName")
    io.applyChanges(buildModel)
  }

  override fun addFilesToOpen(file: File) {
    referencesExecutor.addFilesToOpen(file)
  }

  private fun addWarning(warning: String) {
    context.warnings.add(warning)
  }

  /**
   * Copies the given source file into the given destination file (where the source
   * is allowed to be a directory, in which case the whole directory is copied recursively)
   */
  override fun copy(from: File, to: File) {
    try {
      copyTemplateResource(from, to)
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  /**
   * Instantiates the given template file into the given output file (running the Freemarker engine over it)
   */
  @Throws(TemplateProcessingException::class)
  override fun instantiate(from: File, to: File) {
    try {
      // For now, treat extension-less files as directories... this isn't quite right
      // so I should refine this! Maybe with a unique attribute in the template file?
      val isFile = from.name.contains('.')
      if (!isFile) {
        copyTemplateResource(from, to)
        return
      }
      val sourceFile = context.loader.getSourceFile(from)
      val targetFile = getTargetFile(to)
      var content = processFreemarkerTemplate(context, sourceFile, null)
      content = extractFullyQualifiedNames(to, content)

      if (targetFile.exists()) {
        if (!compareTextFile(targetFile, content)) {
          addFileAlreadyExistWarning(targetFile)
        }
      }
      else {
        io.writeFile(this, content, targetFile)
        referencesExecutor.addSourceFile(sourceFile)
        referencesExecutor.addTargetFile(targetFile)
      }
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  /**
   * Merges the given source file into the given destination file (or it just copies it over if
   * the destination file does not exist).
   *
   *
   * Only XML and Gradle files are currently supported.
   */
  @Throws(TemplateProcessingException::class)
  override fun merge(from: File, to: File) {
    try {
      var targetText: String? = null

      val sourceFile = context.loader.getSourceFile(from)
      val targetFile = getTargetFile(to)
      if (!(hasExtension(targetFile, DOT_XML) || hasExtension(targetFile, DOT_GRADLE))) {
        throw RuntimeException("Only XML or Gradle files can be merged at this point: $targetFile")
      }

      if (targetFile.exists()) {
        if (context.project.isInitialized) {
          val toFile = findFileByIoFile(targetFile, true)
          val status = readonlyStatusHandler.ensureFilesWritable(listOf(toFile!!))
          if (status.hasReadonlyFiles()) {
            throw TemplateUserVisibleException("Attempt to update file that is readonly: ${targetFile.absolutePath}")
          }
        }
        targetText = readTextFile(targetFile)
      }

      if (targetText == null) {
        // The target file doesn't exist: don't merge, just copy
        val instantiate = hasExtension(from, DOT_FTL)
        if (instantiate) {
          instantiate(from, targetFile)
        }
        else {
          copyTemplateResource(from, targetFile)
        }
        return
      }

      val sourceText: String = if (hasExtension(from, DOT_FTL))
        processFreemarkerTemplate(context, from, null) // Perform template substitution of the template prior to merging
      else
        readTextFromDisk(sourceFile) ?: return

      val contents: String = when {
        targetFile.name == FN_BUILD_GRADLE -> {
          val compileSdkVersion = paramMap[ATTR_BUILD_API_STRING] as String
          io.mergeBuildFiles(sourceText, targetText, context.project, compileSdkVersion)
        }
        hasExtension(targetFile, DOT_XML) -> mergeXml(RenderingContextAdapter(context), sourceText, targetText, targetFile)
        else -> throw RuntimeException("Only XML or Gradle settings files can be merged at this point: $targetFile")
      }

      io.writeFile(this, contents, targetFile)
      referencesExecutor.addSourceFile(sourceFile)
      referencesExecutor.addTargetFile(targetFile)
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  override fun addGlobalVariable(id: String, value: Any) {
    context.paramMap[id] = value
  }

  /**
   * Create a directory at the specified location (if not already present). This will also create
   * any parent directories that don't exist, as well.
   */
  override fun mkDir(at: File) {
    try {
      io.mkDir(getTargetFile(at))
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  /**
   * Adds a module dependency to global settings.gradle[.kts] file.
   */
  override fun addIncludeToSettings(moduleName: String?) {
    ProjectBuildModel.get(context.project).projectSettingsModel?.apply {
      addModulePath(moduleName ?: context.paramMap[ATTR_MODULE_NAME] as String)
      io.applyChanges(this)
    }
  }

  /**
   * Adds a new build feature to android block. For example, may enable compose.
   */
  override fun setBuildFeature(name: String, value: String) {
    val buildFile = getBuildFilePath(context)
    val buildModel = getBuildModel(buildFile, context.project) ?: return

    val feature = when(name) {
      "compose" -> buildModel.android().buildFeatures().compose()
      else -> throw IllegalArgumentException("currently only compose build feature is supported")
    }

    if (feature.valueType != GradlePropertyModel.ValueType.NONE) {
      return
    }

    val boolValue = parseBoolean(value, "buildFeature")

    feature.setValue(boolValue)
    io.applyChanges(buildModel)
  }

  /**
   * Sets sourceCompatibility and targetCompatibility in compileOptions and (if needed) jvmTarget in kotlinOptions.
   */
  override fun requireJavaVersion(version: String, kotlinSupport: String) {
    infix fun String.compareWith(other: String): Int {
      val validTargets = listOf("1.6", "1.7", "1.8", "9", "10", "11", "12", "13")

      return validTargets.indexOf(this) - validTargets.indexOf(other)
    }

    val buildFile = getBuildFilePath(context)
    val buildModel = getBuildModel(buildFile, context.project) ?: return

    val compileOptions = buildModel.android().compileOptions()
    val kotlinOptions = buildModel.android().kotlinOptions()

    val currentSourceCompatibility = compileOptions.sourceCompatibility()
    val currentTargetCompatibility = compileOptions.targetCompatibility()

    val languageLevel = LanguageLevelUtil.parseFromGradleString(version)!!

    fun updateCompatibility(current: LanguageLevelPropertyModel) {
      if (current.valueType == GradlePropertyModel.ValueType.NONE ||
          languageLevel.isAtLeast(LanguageLevelUtil.parseFromGradleString(current.getValue(STRING_TYPE)!!)!!)) {
        current.setLanguageLevel(languageLevel)
      }
    }

    updateCompatibility(currentSourceCompatibility)
    updateCompatibility(currentTargetCompatibility)

    if (!parseBoolean(kotlinSupport, "kotlinSupport") || context.paramMap[ATTR_IS_DYNAMIC_FEATURE] == true) {
      io.applyChanges(buildModel)
      return
    }

    val currentKotlinJvmTarget = kotlinOptions.jvmTarget()

    if (currentKotlinJvmTarget.valueType == GradlePropertyModel.ValueType.NONE ||
        currentKotlinJvmTarget.getValue(STRING_TYPE)!! compareWith version <= 0) {
      currentKotlinJvmTarget.setValue(version)
    }

    io.applyChanges(buildModel)
  }

  override fun addDynamicFeature(name: String, toModule: String) {
    require(name.isNotEmpty() && toModule.isNotEmpty()) {
      "Module name cannot be empty"
    }
    // Translate from "configuration" to "implementation" based on the parameter map context
    val buildFile = findGradleBuildFile(File(toModule))

    val gradleName = ':' + name.trimStart(':')
    // TODO(qumeric) handle it in a better way?
    val buildModel = getBuildModel(buildFile, context.project) ?: return
    buildModel.android().dynamicFeatures().addListValue().setValue(gradleName)
    io.applyChanges(buildModel)
  }

  /**
   * Update the project's gradle build file and sync, if necessary. This should only be called
   * once and after all dependencies are already added.
   */
  override fun updateAndSync() {
    if (context.dependencies.isEmpty) {
      return
    }
    try {
      mergeDependenciesIntoGradle()
    }
    catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  override fun pushFolder(folder: String) {
    try {
      context.loader.pushTemplateFolder(folder)
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  override fun popFolder() {
    context.loader.popTemplateFolder()
  }

  /**
   * Returns the absolute path to the file which will get written to.
   */
  private fun getTargetFile(file: File): File {
    return if (file.isAbsolute)
      file
    else
      File(context.outputRoot, file.path)
  }

  /**
   * Merge the URLs from our gradle template into the target module's build.gradle file
   */
  @Throws(Exception::class)
  private fun mergeDependenciesIntoGradle() {
    // Note: ATTR_BASE_FEATURE_DIR has a value set for Instant App/Dynamic Feature modules.
    val baseFeatureRoot = paramMap.getOrDefault(ATTR_BASE_FEATURE_DIR, "") as String
    val featureBuildFile = getBuildFilePath(context)
    if (isNullOrEmpty(baseFeatureRoot)) {
      writeDependencies(featureBuildFile) { true }
    }
    else {
      // The new gradle API deprecates the "compile" keyword by two new keywords: "implementation" and "api"
      var configName = FmGetConfigurationNameMethod.convertConfiguration(paramMap, "compile")
      if ("implementation" == configName) {
        // For the base module, we want to use "api" instead of "implementation"
        for (apiDependency in context.dependencies.removeAll("implementation")) {
          context.dependencies.put("api", apiDependency)
        }
        configName = "api"
      }

      // If a Library (e.g. Google Maps) Manifest references its own resources, it needs to be added to the Base, otherwise aapt2 will fail
      // during linking. Since we don't know the libraries Manifest references, we declare this libraries in the base as "api" dependencies.
      val baseBuildFile = findGradleBuildFile(File(baseFeatureRoot))
      val configuration = configName
      writeDependencies(baseBuildFile) { it == configuration }
      writeDependencies(featureBuildFile) { it != configuration }
    }
  }

  @Throws(IOException::class)
  private fun writeDependencies(buildFile: File, configurationFilter: (String) -> Boolean) {
    val destinationContents = if (buildFile.exists()) nullToEmpty(readTextFile(buildFile)) else ""
    val buildApi = paramMap[ATTR_BUILD_API]
    val supportLibVersionFilter = buildApi?.toString() ?: ""
    val result = io.mergeBuildFiles(formatDependencies(configurationFilter), destinationContents, context.project, supportLibVersionFilter)
    io.writeFile(this, result, buildFile)
  }

  private fun formatDependencies(configurationFilter: (String) -> Boolean): String =
    context.dependencies.entries().asSequence()
      .filter { (configuration, _) -> configurationFilter(configuration) }
      .joinToString("\n", "dependencies {\n", "\n}\n") { (configuration, mavenUrl) ->
        val dependencyValue = convertToAndroidX(mavenUrl)
        // Interpolated values need to be in double quotes
        val delimiter = if (dependencyValue.contains("$")) '"' else '\''
        "  $configuration $delimiter$dependencyValue$delimiter"
      }

  private fun convertToAndroidX(dep: String): String =
    if (paramMap[ATTR_ANDROIDX_SUPPORT] as Boolean? == true)
      AndroidxNameUtils.getVersionedCoordinateMapping(dep)
    else
      dep

  /**
   * VfsUtil#copyDirectory messes up the undo stack, most likely by trying to
   * create a directory even if it already exists. This is an undo-friendly replacement.
   */
  @Throws(IOException::class)
  private fun copyDirectory(src: VirtualFile, dest: File) {
    VfsUtilCore.visitChildrenRecursively(src, object : VirtualFileVisitor<Any>() {
      override fun visitFile(file: VirtualFile): Boolean {
        try {
          return copyFile(file, src, dest)
        }
        catch (e: IOException) {
          throw VisitorException(e)
        }
      }
    }, IOException::class.java)
  }

  @Throws(IOException::class)
  private fun copyTemplateResource(from: File, to: File) {
    val source = context.loader.getSourceFile(from)
    val target = getTargetFile(to)

    val sourceFile = findFileByIoFile(source, true) ?: error(source)
    sourceFile.refresh(false, false)
    val destPath = if (source.isDirectory) target else target.parentFile
    if (source.isDirectory) {
      copyDirectory(sourceFile, destPath)
    }
    else if (target.exists()) {
      if (!compareFile(sourceFile, target)) {
        addFileAlreadyExistWarning(target)
      }
    }
    else {
      val document = FileDocumentManager.getInstance().getDocument(sourceFile)
      if (document != null) {
        io.writeFile(this, document.text, target)
      }
      else {
        io.copyFile(this, sourceFile, destPath, target.name)
      }
      referencesExecutor.addSourceFile(source)
      referencesExecutor.addTargetFile(target)
    }
  }

  @Throws(IOException::class)
  private fun copyFile(file: VirtualFile, src: VirtualFile, destinationFile: File): Boolean {
    val relativePath = VfsUtilCore.getRelativePath(file, src, File.separatorChar) ?: throw RuntimeException(
      "${file.path} is not a child of $src")
    if (file.isDirectory) {
      io.mkDir(File(destinationFile, relativePath))
      return true
    }
    val target = File(destinationFile, relativePath)
    if (target.exists()) {
      if (!compareFile(file, target)) {
        addFileAlreadyExistWarning(target)
      }
    }
    else {
      io.copyFile(this, file, target)
      referencesExecutor.addSourceFile(virtualToIoFile(file))
      referencesExecutor.addTargetFile(target)
    }
    return true
  }

  private fun readTextFile(file: File): String? =
    if (java.lang.Boolean.TRUE == context.paramMap[ATTR_IS_NEW_MODULE])
      readTextFromDisk(file)
    else
      readTextFromDocument(context.project, file)

  private fun readTextFile(file: VirtualFile): String? =
    if (java.lang.Boolean.TRUE == context.paramMap[ATTR_IS_NEW_MODULE])
      readTextFromDisk(virtualToIoFile(file))
    else
      readTextFromDocument(context.project, file)

  /**
   * Shorten all fully qualified Layout names that belong to the same package as the manifest's
   * package attribute value.
   *
   * @See [com.android.manifmerger.ManifestMerger2.extractFqcns]
   */
  private fun extractFullyQualifiedNames(to: File, content: String): String {
    if (ResourceFolderType.getFolderType(to.parentFile.name) != ResourceFolderType.LAYOUT) {
      return content
    }

    val packageName: String? = paramMap[ATTR_APPLICATION_PACKAGE] as String? ?: paramMap[ATTR_PACKAGE_NAME] as String?

    val factory = XmlElementFactory.getInstance(context.project)
    val root = factory.createTagFromText(content)

    // Note: At the moment only root "context:tools" atribute needs to be shorten
    val contextAttr = root.getAttribute(ATTR_CONTEXT, TOOLS_URI)
    if (packageName == null || contextAttr == null) {
      return content
    }

    val context = contextAttr.value
    if (context == null || !context.startsWith("$packageName.")) {
      return content
    }

    val newContext = context.substring(packageName.length)
    root.setAttribute(ATTR_CONTEXT, TOOLS_URI, newContext)

    return XML_PROLOG + root.text
  }

  /**
   * Return true if the content of `targetFile` is the same as the content of `sourceVFile`.
   */
  @Throws(IOException::class)
  fun compareFile(sourceVFile: VirtualFile, targetFile: File): Boolean {
    val targetVFile = findFileByIoFile(targetFile, true) ?: return false
    if (sourceVFile.fileType.isBinary) {
      val source = sourceVFile.contentsToByteArray()
      val target = targetVFile.contentsToByteArray()
      return source.contentEquals(target)
    }
    else {
      val source = readTextFile(sourceVFile)
      val target = readTextFile(targetVFile)
      val comparisonManager = ComparisonManager.getInstance()
      return comparisonManager.isEquals(source!!, target!!, ComparisonPolicy.IGNORE_WHITESPACES)
    }
  }

  /**
   * Return true if the content of `targetFile` is the same as `content`.
   */
  fun compareTextFile(targetFile: File, content: String): Boolean {
    val target = readTextFile(targetFile)
    val comparisonManager = ComparisonManager.getInstance()
    return comparisonManager.isEquals(content, target!!, ComparisonPolicy.IGNORE_WHITESPACES)
  }

  private fun addFileAlreadyExistWarning(targetFile: File) {
    addWarning("The following file could not be created since it already exists: $targetFile")
  }
}

private fun getBuildFilePath(context: RenderingContext): File {
  val module = context.module
  val moduleBuildFile = if (module == null) null else getGradleBuildFile(module)
  return moduleBuildFile?.let { virtualToIoFile(it) } ?: findGradleBuildFile(context.moduleRoot)
}

private fun parseBoolean(value: String, name: String = "value") = when(value) {
  "true" -> true
  "false" -> false
  else -> throw IllegalArgumentException("$name may be 'true' or 'false' but '$value' was given")
}
