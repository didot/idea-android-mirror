/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.imports

import com.android.tools.idea.projectsystem.DependencyType

/**
 * Registry provides lookup service for Google Maven Artifacts when asked.
 */
abstract class MavenClassRegistryBase {
  /**
   * Library for each of the GMaven artifact.
   *
   * @property artifact maven coordinate: groupId:artifactId, please note version is not included here.
   * @property packageName fully qualified package name which is used for the following import purposes.
   * @property version the version of the [artifact].
   */
  data class Library(val artifact: String, val packageName: String, val version: String? = null)

  /**
   * Coordinate for Google Maven artifact.
   */
  data class Coordinate(val groupId: String, val artifactId: String, val version: String)

  /**
   * Given a class name, returns the likely collection of [Library] objects for the following quick fixes purposes.
   */
  abstract fun findLibraryData(className: String, useAndroidX: Boolean): Collection<Library>

  /**
   * For the given runtime artifact, if Kotlin is the adopted language, the corresponding ktx library is provided.
   */
  abstract fun findKtxLibrary(artifact: String): String?

  /**
   * Returns a collection of [Coordinate].
   */
  abstract fun getCoordinates(): Collection<Coordinate>

  /**
   * For the given runtime artifact, if it also requires an annotation processor, provide it.
   */
  fun findAnnotationProcessor(artifact: String): String? {
    return when (artifact) {
      "androidx.room:room-runtime",
      "android.arch.persistence.room:runtime" -> "android.arch.persistence.room:compiler"
      "androidx.remotecallback:remotecallback" -> "androidx.remotecallback:remotecallback-processor"
      else -> null
    }
  }

  /**
   * For the given artifact, if it also requires extra artifacts for proper functionality, provide it.
   *
   * This is to handle those special cases. For example, for an unresolved symbol "@Preview",
   * "androidx.compose.ui:ui-tooling-preview" is one of the suggested artifacts to import based on the extracted
   * contents from the GMaven index file. However, this is not enough -"androidx.compose.ui:ui-tooling" should be added
   * on instead. So we just provide both in the end.
   */
  fun findExtraArtifacts(artifact: String): Map<String, DependencyType> {
    return when (artifact) {
      "androidx.compose.ui:ui-tooling-preview" -> mapOf("androidx.compose.ui:ui-tooling" to DependencyType.DEBUG_IMPLEMENTATION)
      else -> emptyMap()
    }
  }
}