/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.model

sealed interface IdeDependency<T: IdeLibrary>

sealed interface IdeArtifactDependency<T: IdeArtifactLibrary> : IdeDependency<T> {
  val target: T
  /**
   * Returns whether the dependency is on the compile class path but is not on the runtime class
   * path.
   */
  val isProvided: Boolean
}

interface IdeAndroidLibraryDependency: IdeArtifactDependency<IdeAndroidLibrary>
interface IdeJavaLibraryDependency: IdeArtifactDependency<IdeJavaLibrary>

interface IdeModuleDependency: IdeDependency<IdeModuleLibrary> {
  val target: IdeModuleLibrary
}

/**
 * Returns the gradle path.
 */
val IdeModuleDependency.projectPath: String get() = target.projectPath

/**
 * Returns an optional variant name if the consumed artifact of the library is associated to
 * one.
 */
val IdeModuleDependency.variant: String? get() = target.variant

/**
 * Returns the build id.
 */
val IdeModuleDependency.buildId: String get() = target.buildId

/**
 * Returns the sourceSet associated with the library.
 */
val IdeModuleDependency.sourceSet: IdeModuleSourceSet get() = target.sourceSet
