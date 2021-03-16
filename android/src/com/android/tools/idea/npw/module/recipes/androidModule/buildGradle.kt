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
package com.android.tools.idea.npw.module.recipes.androidModule

import com.android.repository.Revision.parseRevision
import com.android.tools.idea.gradle.npw.project.GradleBuildSettings.needsExplicitBuildToolsVersion
import com.android.tools.idea.npw.module.recipes.androidConfig
import com.android.tools.idea.npw.module.recipes.emptyPluginsBlock
import com.android.tools.idea.wizard.template.CppStandardType
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.has
import com.android.tools.idea.wizard.template.renderIf

fun buildGradle(
  isKts: Boolean,
  isLibraryProject: Boolean,
  isDynamicFeature: Boolean,
  packageName: String,
  buildApiString: String,
  buildToolsVersion: String,
  minApi: String,
  targetApi: String,
  useAndroidX: Boolean,
  isCompose: Boolean = false,
  baseFeatureName: String = "base",
  wearProjectName: String = "wear",
  formFactorNames: Map<FormFactor, List<String>>,
  hasTests: Boolean = true,
  addLintOptions: Boolean = false,
  enableCpp: Boolean = false,
  cppStandard: CppStandardType = CppStandardType.`Toolchain Default`
): String {
  val explicitBuildToolsVersion = needsExplicitBuildToolsVersion(parseRevision(buildToolsVersion))

  val androidConfigBlock = androidConfig(
    buildApiString = buildApiString,
    explicitBuildToolsVersion = explicitBuildToolsVersion,
    buildToolsVersion = buildToolsVersion,
    minApi = minApi,
    targetApi = targetApi,
    useAndroidX = useAndroidX,
    isLibraryProject = isLibraryProject,
    explicitApplicationId = !isLibraryProject,
    applicationId = packageName,
    hasTests = hasTests,
    canUseProguard = true,
    addLintOptions = addLintOptions,
    enableCpp = enableCpp,
    cppStandard = cppStandard
  )

  if (isDynamicFeature) {
    return """
$androidConfigBlock

dependencies {
    implementation project("${baseFeatureName}")
}
""".gradleToKtsIfKts(isKts)
  }

  val composeDependenciesBlock = renderIf(isCompose) { "kotlinPlugin \"androidx.compose:compose-compiler:+\"" }

  val wearProjectBlock = when {
    wearProjectName.isNotBlank() && formFactorNames.has(FormFactor.Mobile) && formFactorNames.has(FormFactor.Wear) ->
      """wearApp project (":${wearProjectName}")"""
    else -> ""
  }

  val dependenciesBlock = """
  dependencies {
    $composeDependenciesBlock
    $wearProjectBlock
  }
  """

  val allBlocks =
    """
    ${emptyPluginsBlock()}
    $androidConfigBlock
    $dependenciesBlock
    """

  return allBlocks.gradleToKtsIfKts(isKts)
}

private fun String.toKtsFunction(funcName: String): String = if (this.contains("$funcName ")) {
  this.replace("$funcName ", "$funcName(") + ")"
}
else {
  this
}

private fun String.toKtsProperty(funcName: String): String = this.replace("$funcName ", "$funcName = ")

internal fun String.gradleToKtsIfKts(isKts: Boolean): String = if (isKts) {
  split("\n").joinToString("\n") {
    it.replace("'", "\"")
      .toKtsFunction("compileSdkVersion")
      .toKtsProperty("buildToolsVersion")
      .toKtsProperty("applicationId")
      .toKtsFunction("minSdkVersion")
      .toKtsFunction("targetSdkVersion")
      .toKtsProperty("versionCode")
      .toKtsProperty("versionName")
      .toKtsProperty("testInstrumentationRunner")
      .toKtsProperty("minifyEnabled")
      .toKtsFunction("proguardFiles")
      .toKtsFunction("consumerProguardFiles")
      .toKtsFunction("wearApp")
      .toKtsFunction("implementation") // For dynamic app: implementation project(":app") -> implementation(project(":app"))
      .replace("minifyEnabled", "isMinifyEnabled")
      .replace("release {", "getByName(\"release\") {")
      .replace("debug {", "getByName(\"debug\") {")
      // The followings are for externalNativeBuild
      .toKtsFunction("cppFlags")
      .toKtsFunction("path")
      .toKtsProperty("version")
  }
}
else {
  this
}
