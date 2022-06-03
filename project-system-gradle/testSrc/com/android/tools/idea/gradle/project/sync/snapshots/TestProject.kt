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
package com.android.tools.idea.gradle.project.sync.snapshots

import com.android.SdkConstants.FN_SETTINGS_GRADLE
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.FileSubject.file
import com.android.tools.idea.testing.ModelVersion
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.utils.FileUtils.writeToFile
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.android.AndroidTestBase.refreshProjectFiles
import java.io.File

/**
 * Defines test projects used in [SyncedProjectTest].
 *
 * When adding a new entry to this file add a new test method to [SyncedProjectTest].
 */
enum class TestProject(
  val template: String,
  val pathToOpen: String = "",
  val testName: String? = null,
  val isCompatibleWith: (AgpVersionSoftwareEnvironmentDescriptor) -> Boolean = { true },
  val patch: AgpVersionSoftwareEnvironmentDescriptor.(projectRoot: File) -> Unit = {}
) {
  SIMPLE_APPLICATION(TestProjectToSnapshotPaths.SIMPLE_APPLICATION),
  SIMPLE_APPLICATION_WITH_ADDITIONAL_GRADLE_SOURCE_SETS(
    TestProjectToSnapshotPaths.SIMPLE_APPLICATION, testName = "additionalGradleSourceSets", patch = { root ->
      val buildFile = root.resolve("app").resolve("build.gradle")
      buildFile.writeText(
        buildFile.readText() + """
          sourceSets {
            test.resources.srcDirs += 'src/test/resources'
          }
        """.trimIndent()
      )
    }),
  WITH_GRADLE_METADATA(TestProjectToSnapshotPaths.WITH_GRADLE_METADATA),
  BASIC_CMAKE_APP(TestProjectToSnapshotPaths.BASIC_CMAKE_APP),
  PSD_SAMPLE_GROOVY(TestProjectToSnapshotPaths.PSD_SAMPLE_GROOVY),
  COMPOSITE_BUILD(
    TestProjectToSnapshotPaths.COMPOSITE_BUILD,
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_70 },
    patch = { projectRoot ->
      if (modelVersion == ModelVersion.V2) {
        truncateForV2(projectRoot.resolve("settings.gradle"))
      }
    }),
  NON_STANDARD_SOURCE_SETS(TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SETS, "/application"),
  NON_STANDARD_SOURCE_SET_DEPENDENCIES(
    TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SET_DEPENDENCIES,
    isCompatibleWith = { it.modelVersion == ModelVersion.V2 }
  ),
  LINKED(TestProjectToSnapshotPaths.LINKED, "/firstapp"),
  KOTLIN_KAPT(TestProjectToSnapshotPaths.KOTLIN_KAPT),
  LINT_CUSTOM_CHECKS(
    TestProjectToSnapshotPaths.LINT_CUSTOM_CHECKS,
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_71 }
  ),
  TEST_FIXTURES(
    TestProjectToSnapshotPaths.TEST_FIXTURES,
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_72 }
  ),
  TEST_ONLY_MODULE(TestProjectToSnapshotPaths.TEST_ONLY_MODULE),
  KOTLIN_MULTIPLATFORM(
    TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM,
    isCompatibleWith = { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_70 }
  ),
  MULTI_FLAVOR(TestProjectToSnapshotPaths.MULTI_FLAVOR),
  NAMESPACES(TestProjectToSnapshotPaths.NAMESPACES),
  INCLUDE_FROM_LIB(TestProjectToSnapshotPaths.INCLUDE_FROM_LIB),
  LOCAL_AARS_AS_MODULES(TestProjectToSnapshotPaths.LOCAL_AARS_AS_MODULES),
  BASIC(TestProjectToSnapshotPaths.BASIC);

  val projectName: String get() = "${template.removePrefix("projects/")}$pathToOpen${if (testName == null) "" else " - $testName"}"
}

private fun truncateForV2(settingsFile: File) {
  val patchedText = settingsFile.readLines().takeWhile { !it.contains("//-v2:truncate-from-here") }.joinToString("\n")
  Truth.assertThat(patchedText.trim()).isNotEqualTo(settingsFile.readText().trim())
  settingsFile.writeText(patchedText)
}
  
private fun AgpVersionSoftwareEnvironmentDescriptor.updateProjectJdk(projectRoot: File) {
  val jdk = IdeSdks.getInstance().jdk ?: error("${SyncedProjectTest::class} requires a valid JDK")
  val miscXml = projectRoot.resolve(".idea").resolve("misc.xml")
  miscXml.writeText(miscXml.readText().replace("""project-jdk-name="1.8"""", """project-jdk-name="${jdk.name}""""))
}

private fun createEmptyGradleSettingsFile(projectRootPath: File) {
  val settingsFilePath = File(projectRootPath, FN_SETTINGS_GRADLE)
  assertThat(FileUtil.delete(settingsFilePath)).isTrue()
  writeToFile(settingsFilePath, " ")
  assertAbout(file()).that(settingsFilePath).isFile()
  refreshProjectFiles()
}

fun AgpVersionSoftwareEnvironmentDescriptor.agpSuffix(): String = when (this) {
  AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT -> "_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT_V1 -> "_NewAgp_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_35 -> "_Agp_3.5_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_40 -> "_Agp_4.0_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_41 -> "_Agp_4.1_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_42 -> "_Agp_4.2_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_70 -> "_Agp_7.0_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_71 -> "_Agp_7.1_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_72_V1 -> "_Agp_7.2_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_72 -> "_Agp_7.2_"
}

fun AgpVersionSoftwareEnvironmentDescriptor.gradleSuffix(): String {
  return gradleVersion?.let { "Gradle_${it}_" }.orEmpty()
}

class SnapshotContext(
  projectName: String,
  agpVersion: AgpVersionSoftwareEnvironmentDescriptor,
  private val workspace: String,
) : SnapshotComparisonTest {

  private val name: String =
    "$projectName${agpVersion.agpSuffix()}${agpVersion.gradleSuffix()}${agpVersion.modelVersion}"

  override val snapshotDirectoryWorkspaceRelativePath: String = workspace
  override fun getName(): String = name
}
