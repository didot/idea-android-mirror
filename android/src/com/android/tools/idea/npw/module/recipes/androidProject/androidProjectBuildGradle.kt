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
package com.android.tools.idea.npw.module.recipes.androidProject

import com.android.tools.idea.wizard.template.GradlePluginVersion
import com.android.tools.idea.wizard.template.renderIf

private fun isEap(kotlinVersion: String) = setOf("rc", "eap", "-M").any { it in kotlinVersion }

fun kotlinEapRepoBlock(kotlinVersion: String) = renderIf(isEap(kotlinVersion)) {
  """maven { url = uri("https://dl.bintray.com/kotlin/kotlin-eap") }"""
}

fun androidProjectBuildGradle(
  generateKotlin: Boolean,
  kotlinVersion: String,
  gradlePluginVersion: GradlePluginVersion
): String {
  return """
    // Top-level build file where you can add configuration options common to all sub-projects/modules.
    buildscript {
        repositories {
            google()
            mavenCentral()
            ${kotlinEapRepoBlock(kotlinVersion)}
        }
        dependencies {
            classpath "com.android.tools.build:gradle:$gradlePluginVersion"
            ${renderIf(generateKotlin) { "classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion\"" }}

            // NOTE: Do not place your application dependencies here; they belong
            // in the individual module build.gradle files
        }
    }

    task clean (type: Delete) {
        delete rootProject.buildDir
    }
    """
}

fun androidProjectBuildGradleKts(
  generateKotlin: Boolean,
  kotlinVersion: String,
  gradlePluginVersion: GradlePluginVersion
): String {
  return """
    // Top-level build file where you can add configuration options common to all sub-projects/modules.
    buildscript {
        repositories {
            google()
            mavenCentral()
            ${kotlinEapRepoBlock(kotlinVersion)}
        }
        dependencies {
            classpath("com.android.tools.build:gradle:$gradlePluginVersion")
            ${renderIf(generateKotlin) { "classpath(\"org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion\")" }}

            // NOTE: Do not place your application dependencies here; they belong
            // in the individual module build.gradle.kts files
        }
    }

    tasks.register("clean", Delete::class) {
        delete(rootProject.buildDir)
    }
    """
}