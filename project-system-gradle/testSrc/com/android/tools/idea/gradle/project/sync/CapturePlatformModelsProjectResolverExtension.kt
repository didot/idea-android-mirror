/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.registerExtension
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

class CapturePlatformModelsProjectResolverExtension : AbstractProjectResolverExtension() {
  companion object {
    private val kotlinModels = mutableMapOf<String, KotlinGradleModel>()
    private val kaptModels = mutableMapOf<String, KaptGradleModel>()
    private val externalProjectModels = mutableMapOf<String, ExternalProject>()

    fun getKotlinModel(module: Module): KotlinGradleModel? = kotlinModels[getGradleProjectPath(module)]
    fun getKaptModel(module: Module): KaptGradleModel? = kaptModels[getGradleProjectPath(module)]
    fun getExternalProjectModel(module: Module): ExternalProject? = externalProjectModels[getGradleProjectPath(module)]

    private fun getGradleProjectPath(module: Module): String? {
      return ExternalSystemApiUtil.getExternalProjectPath(module)
        .takeIf { ExternalSystemApiUtil.getExternalModuleType(module) == null } // Return models for holder modules only.
    }

    fun reset() {
      kotlinModels.clear()
      kaptModels.clear()
      externalProjectModels.clear()
    }

    fun registerTestHelperProjectResolver(disposable: Disposable) {
      ApplicationManager.getApplication().registerExtension(
        @Suppress("UnstableApiUsage")
        EP_NAME,
        CapturePlatformModelsProjectResolverExtension(), // Note: a new instance is created by the external system.
        disposable
      )
      Disposer.register(disposable, object : Disposable {
        override fun dispose() { reset() }
      })
    }
  }

  override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
    val gradleProjectPath = ideModule.data.linkedExternalProjectPath
    resolverCtx.getExtraProject(gradleModule, KotlinGradleModel::class.java)?.let { kotlinModels[gradleProjectPath] = it }
    // The KaptGradleModel is present for Java modules
    resolverCtx.getExtraProject(gradleModule, KaptGradleModel::class.java)?.let {
      kaptModels[gradleProjectPath] = it
    }
    // For Android modules it is contained within the IdeAndroidModels class
    resolverCtx.getExtraProject(gradleModule, IdeAndroidModels::class.java)?.let {
      it.kaptGradleModel?.let { kaptModel ->
        kaptModels[gradleProjectPath] = kaptModel
      }
    }
    resolverCtx.getExtraProject(gradleModule, ExternalProject::class.java)?.let {
      externalProjectModels[gradleProjectPath] = it
    }
    super.populateModuleExtraModels(gradleModule, ideModule)
  }
}

fun KotlinGradleModel.testSourceSetNames(): Collection<String> = cachedCompilerArgumentsBySourceSet.keys
fun KaptGradleModel.testSourceSetNames(): Collection<String> = sourceSets.map { it.sourceSetName }
