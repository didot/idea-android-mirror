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
package com.android.tools.idea.gradle.model

import java.io.File

interface IdeBaseConfig {
  val name: String

  /**
   * The application id suffix applied to this base config.
   */
  val applicationIdSuffix: String?

  /**
   * The version name suffix of this flavor or null if none have been set.
   * This is only the value set on this product flavor, not necessarily the actual
   * version name suffix used.
   */
  val versionNameSuffix: String?

  /**
   * Map of generated res values where the key is the res name.
   */
  val resValues: Map<String, IdeClassField>

  /**
   * Specifies the ProGuard configuration files that the plugin should use.
   *
   * There are two ProGuard rules files that ship with the Android plugin and are used by
   * default:
   *
   *  * proguard-android.txt
   *  * proguard-android-optimize.txt
   *
   * `proguard-android-optimize.txt` is identical to `proguard-android.txt`,
   * except with optimizations enabled. You can use [getDefaultProguardFile(String)]
   * to return the full path of the files.
   *
   * @return a non-null collection of files.
   * @see .getTestProguardFiles
   */
  val proguardFiles: Collection<File>

  /** The collection of proguard rule files for consumers of the library to use. */
  val consumerProguardFiles: Collection<File>

  /**
   * The map of key value pairs for placeholder substitution in the android manifest file.
   *
   * This map will be used by the manifest merger.
   */
  val manifestPlaceholders: Map<String, String>

  /**
   * Returns whether multi-dex is enabled.
   *
   * This can be null if the flag is not set, in which case the default value is used.
   */
  val multiDexEnabled: Boolean?
}
