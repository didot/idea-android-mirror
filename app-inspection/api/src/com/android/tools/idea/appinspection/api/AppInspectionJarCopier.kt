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
package com.android.tools.idea.appinspection.api

import com.android.tools.idea.transport.DeployableFile

/**
 * Defines the method through which an [AppInspectorJar] could be copied to device.
 */
interface AppInspectionJarCopier {
  /**
   * Copies the provided [AppInspectorJar] to a device.
   */
  fun copyFileToDevice(jar: AppInspectorJar): List<String>
}

/**
 * Represents an instance of an inspector Jar.
 *
 * To be used with [AppInspectionJarCopier] to copy inspector jar from studio to device.
 */
data class AppInspectorJar(
  /**
   * Name of the jar.
   */
  val name: String,

  /**
   * The directory in the studio release in which this jar is located.
   *
   * It should look like: plugins/android/resources/inspection
   */
  val releaseDirectory: String,

  /**
   * The development path of the jar relative to tools/idea.
   *
   * For example: ../../prebuilts/tools/common/m2/repository/androidx/inspection/inspection/1.0.0-SNAPSHOT
   */
  val developmentDirectory: String
)

fun AppInspectorJar.toDeployableFile() =
  DeployableFile.Builder(name).setReleaseDir(releaseDirectory).setDevDir(developmentDirectory).build()