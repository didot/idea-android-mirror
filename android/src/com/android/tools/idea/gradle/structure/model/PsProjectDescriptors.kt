/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.structure.model.helpers.androidGradlePluginVersionValues
import com.android.tools.idea.gradle.structure.model.helpers.parseString
import com.android.tools.idea.gradle.structure.model.meta.ModelDescriptor
import com.android.tools.idea.gradle.structure.model.meta.ModelProperty
import com.android.tools.idea.gradle.structure.model.meta.SimpleProperty
import com.android.tools.idea.gradle.structure.model.meta.VariableMatchingStrategy
import com.android.tools.idea.gradle.structure.model.meta.asString
import com.android.tools.idea.gradle.structure.model.meta.property
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object PsProjectDescriptors : ModelDescriptor<PsProject, Nothing, ProjectBuildModel> {
  private const val AGP_GROUP_ID_NAME = "com.android.tools.build:gradle"
  override fun getResolved(model: PsProject): Nothing? = null
  override fun getParsed(model: PsProject): ProjectBuildModel? = model.parsedModel
  override fun prepareForModification(model: PsProject) = Unit
  override fun setModified(model: PsProject) {
    model.isModified = true
  }

  val androidGradlePluginVersion: SimpleProperty<PsProject, String> = run {
    fun ArtifactDependencyModel.isAgp() = configurationName() == "classpath" && compactNotation().startsWith("$AGP_GROUP_ID_NAME:")

    property(
      "Android Gradle Plugin Version",
      resolvedValueGetter = { null },
      parsedPropertyGetter = {
        projectBuildModel
          ?.buildscript()
          ?.dependencies()
          ?.all()
          ?.mapNotNull { it.safeAs<ArtifactDependencyModel>() }
          ?.singleOrNull { it.isAgp() }
          ?.version()
      },
      parsedPropertyInitializer = {
        projectBuildModel!!
          .buildscript()
          .dependencies()
          .let { dependencies ->
            dependencies.addArtifact("classpath", "$AGP_GROUP_ID_NAME:0.0")
            dependencies.all()
              .mapNotNull { it.safeAs<ArtifactDependencyModel>() }
              .single { it.isAgp() }
              .version()
          }
      },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString,
      knownValuesGetter = ::androidGradlePluginVersionValues,
      variableMatchingStrategy = VariableMatchingStrategy.WELL_KNOWN_VALUE
    )
  }

  override fun enumerateModels(model: PsProject): Collection<PsModel> = model.modules
  override val properties: Collection<ModelProperty<PsProject, *, *, *>> = listOf(androidGradlePluginVersion)
}