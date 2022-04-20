/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.model.impl

import com.android.tools.idea.gradle.model.CodeShrinker
import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeBuildTasksAndOutputInformation
import com.android.tools.idea.gradle.model.IdeClassField
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.model.IdeTestOptions
import com.android.tools.idea.gradle.model.IdeUnresolvedDependency
import com.android.tools.idea.gradle.model.IdeModelSyncFile
import com.android.tools.idea.gradle.model.IdeAndroidArtifactCore
import com.android.tools.idea.gradle.model.IdeDependenciesCore
import java.io.File

data class IdeAndroidArtifactCoreImpl(
  override val name: IdeArtifactName,
  override val compileTaskName: String,
  override val assembleTaskName: String,
  override val classesFolder: Collection<File>,
  override val variantSourceProvider: IdeSourceProvider?,
  override val multiFlavorSourceProvider: IdeSourceProvider?,
  override val ideSetupTaskNames: Collection<String>,
  override val generatedSourceFolders: Collection<File>,
  override val isTestArtifact: Boolean,
  val compileClasspath: IdeDependenciesCore,
  val runtimeClasspath: IdeDependenciesCore,
  override val unresolvedDependencies: List<IdeUnresolvedDependency>,
  override val applicationId: String,
  override val signingConfigName: String?,
  override val isSigned: Boolean,
  override val generatedResourceFolders: Collection<File>,
  override val additionalRuntimeApks: List<File>,
  override val testOptions: IdeTestOptions?,
  override val abiFilters: Set<String>,
  override val buildInformation: IdeBuildTasksAndOutputInformation,
  override val codeShrinker: CodeShrinker?,
  override val modelSyncFiles: Collection<IdeModelSyncFile>
) : IdeAndroidArtifactCore {
  override val resValues: Map<String, IdeClassField> get() = emptyMap()
}

data class IdeAndroidArtifactImpl(
  private val core: IdeAndroidArtifactCoreImpl,
  private val resolver: IdeLibraryModelResolver
): IdeAndroidArtifact, IdeAndroidArtifactCore by core {
  override val compileClasspath: IdeDependencies = IdeDependenciesImpl(core.compileClasspath, resolver)
  override val runtimeClasspath: IdeDependencies = IdeDependenciesImpl(core.runtimeClasspath, resolver)
}