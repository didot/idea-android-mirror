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
package com.android.tools.idea.gradle.structure.model.java

import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileTreeDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependencyCollection
import com.android.tools.idea.gradle.structure.model.PsDependencyCollection
import com.android.tools.idea.gradle.structure.model.PsJarDependency
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsModuleDependency
import com.android.tools.idea.gradle.structure.model.PsResolvedDependencyCollection
import com.android.tools.idea.gradle.structure.model.matchJarDeclaredDependenciesIn
import com.android.tools.idea.gradle.structure.model.relativeFile
import com.jetbrains.rd.util.first
import org.jetbrains.plugins.gradle.model.ExternalLibraryDependency
import org.jetbrains.plugins.gradle.model.ExternalMultiLibraryDependency
import org.jetbrains.plugins.gradle.model.ExternalProjectDependency
import org.jetbrains.plugins.gradle.model.FileCollectionDependency
import org.jetbrains.plugins.gradle.model.UnresolvedExternalDependency
import java.io.File

interface PsJavaDependencyCollection<out LibraryDependencyT, out JarDependencyT, out ModuleDependencyT>
  : PsDependencyCollection<PsJavaModule, LibraryDependencyT, JarDependencyT, ModuleDependencyT>
  where LibraryDependencyT : PsJavaDependency,
        LibraryDependencyT : PsLibraryDependency,
        JarDependencyT : PsJavaDependency,
        JarDependencyT : PsJarDependency,
        ModuleDependencyT : PsJavaDependency,
        ModuleDependencyT : PsModuleDependency {
  override val items: List<PsJavaDependency> get() = modules + libraries + jars
}

class PsDeclaredJavaDependencyCollection(parent: PsJavaModule)
  : PsDeclaredDependencyCollection<PsJavaModule, PsDeclaredLibraryJavaDependency,
  PsDeclaredJarJavaDependency, PsDeclaredModuleJavaDependency>(parent),
    PsJavaDependencyCollection<PsDeclaredLibraryJavaDependency, PsDeclaredJarJavaDependency, PsDeclaredModuleJavaDependency> {

  override fun createOrUpdateLibraryDependency(
    existing: PsDeclaredLibraryJavaDependency?,
    artifactDependencyModel: ArtifactDependencyModel
  ): PsDeclaredLibraryJavaDependency =
    (existing ?: PsDeclaredLibraryJavaDependency(parent)).apply {init(artifactDependencyModel)}

  override fun createOrUpdateJarFileDependency(
    existing: PsDeclaredJarJavaDependency?,
    fileDependencyModel: FileDependencyModel
  ): PsDeclaredJarJavaDependency =
    (existing ?: PsDeclaredJarJavaDependency(parent)).apply {init(fileDependencyModel)}

  override fun createOrUpdateJarFileTreeDependency(
    existing: PsDeclaredJarJavaDependency?,
    fileTreeDependencyModel: FileTreeDependencyModel
  ): PsDeclaredJarJavaDependency =
    (existing ?: PsDeclaredJarJavaDependency(parent)).apply {init(fileTreeDependencyModel)}

  override fun createOrUpdateModuleDependency(
    existing: PsDeclaredModuleJavaDependency?,
    moduleDependencyModel: ModuleDependencyModel
  ): PsDeclaredModuleJavaDependency =
    (existing ?: PsDeclaredModuleJavaDependency(parent)).apply {init(moduleDependencyModel)}
}

class PsResolvedJavaDependencyCollection(module: PsJavaModule)
  : PsResolvedDependencyCollection<PsJavaModule, PsJavaModule, PsResolvedLibraryJavaDependency,
  PsResolvedJarJavaDependency, PsResolvedModuleJavaDependency>(
  container = module,
  module = module
),
    PsJavaDependencyCollection<PsResolvedLibraryJavaDependency, PsResolvedJarJavaDependency, PsResolvedModuleJavaDependency> {
  override fun collectResolvedDependencies(container: PsJavaModule) {
    val gradleModel = parent.resolvedModel

    fun processFile(file: File) {
      val artifactCanonicalFile = file?.canonicalFile ?: return
      val matchingDeclaredDependencies =
        matchJarDeclaredDependenciesIn(parent.dependencies, artifactCanonicalFile)
      val path = parent.relativeFile(artifactCanonicalFile)
      val jarDependency = PsResolvedJarJavaDependency(parent, this, path.path.orEmpty(), matchingDeclaredDependencies)
      addJarDependency(jarDependency)
    }

    gradleModel?.sourceSets?.filter { it.key == "main" }?.first()?.also {
      (_, sourceSet) ->
      sourceSet.dependencies.filter { it.scope == "COMPILE" || it.scope == "PROVIDED" }.forEach { dependency ->
        when (dependency) {
          is ExternalLibraryDependency -> {
            addLibrary(dependency)
          }
          is ExternalMultiLibraryDependency -> {
            dependency.files.forEach(::processFile)
          }
          is FileCollectionDependency -> {
            dependency.files.forEach(::processFile)
          }
          is ExternalProjectDependency -> {
            val module = parent.parent.findModuleByGradlePath(dependency.projectPath);
            if (module != null) {
              addModule(module, dependency.scope)
            }
          }
          is UnresolvedExternalDependency -> Unit
        }
      }
    }
  }

  private fun addLibrary(library: ExternalLibraryDependency) {
    val parsedDependencies = parent.dependencies
    val group = library.id.group
    val name = library.id.name
    val version = library.id.version
    val coordinates = if (group != null && version != null) GradleCoordinate(group, name, version) else null
    if (coordinates != null) {
      val matchingDeclaredDependencies = parsedDependencies
        .findLibraryDependencies(coordinates.groupId, coordinates.artifactId)
        // TODO(b/110774403): Support Java module dependency scopes.
      addLibraryDependency(PsResolvedLibraryJavaDependency(parent, library, matchingDeclaredDependencies).also {
        library.file?.let { file ->
          it.setDependenciesFromPomFile(parent.parent.pomDependencyCache.getPomDependencies(coordinates.toString(), file))
        }
      })
    }
  }


  private fun addModule(module: PsModule, scope: String) {
    val gradlePath = module.gradlePath!!
    val matchingParsedDependencies =
      parent
        .dependencies
        .findModuleDependencies(gradlePath)
    addModuleDependency(PsResolvedModuleJavaDependency(parent, gradlePath, scope, module, matchingParsedDependencies))
  }
}