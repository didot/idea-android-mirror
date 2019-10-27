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
package com.android.tools.idea.gradle.project.sync

import com.android.testutils.TestUtils
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.createAndroidProjectBuilder
import com.android.tools.idea.testing.createAndroidProjectBuilderForDefaultTestProjectStructure
import com.android.tools.idea.testing.saveAndDump
import com.android.tools.idea.testing.setupTestProjectFromAndroidModel
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.AndroidTestCase
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import java.io.File

/**
 * A test case that ensures the correct behavior of [AndroidProjectRule.withAndroidModel] way to set up test projects.
 *
 * See [AndroidProjectRule.withAndroidModel] for more details.
 */
@RunsInEdt
class LightSyncBasedTestsWithGradleLikeStructureTest : SnapshotComparisonTest {
  @get:Rule
  var testName = TestName()

  val projectRule = AndroidProjectRule.withAndroidModel(createAndroidProjectBuilder())

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  override fun getName(): String = testName.methodName

  override val snapshotDirectoryName: String = "syncedProjectSnapshots"

  @Test
  fun testLightTestsWithGradleLikeStructure() {
    assertThat(ModuleManager.getInstance(projectRule.project).modules).asList().containsExactly(projectRule.module)
    val dump = projectRule.project.saveAndDump()
    assertIsEqualToSnapshot(dump)
  }
}

@RunsInEdt
class LightSyncBasedTestsWithDefaultTestProjectStructureTest : SnapshotComparisonTest {
  @get:Rule
  var testName = TestName()

  val projectRule = AndroidProjectRule.withAndroidModel(createAndroidProjectBuilderForDefaultTestProjectStructure())

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  override fun getName(): String = testName.methodName

  override val snapshotDirectoryName: String = "syncedProjectSnapshots"

  @Test
  fun testLightTestsWithDefaultTestProjectStructure() {
    assertThat(ModuleManager.getInstance(projectRule.project).modules).asList().containsExactly(projectRule.module)
    val dump = projectRule.project.saveAndDump()
    assertIsEqualToSnapshot(dump)
  }
}

class LightSyncForAndroidTestCaseTest : AndroidTestCase(), SnapshotComparisonTest {
  override val snapshotDirectoryName: String = "syncedProjectSnapshots"

  override fun setUp() {
    super.setUp()
    AndroidGradleTests.setUpSdks(myFixture, TestUtils.getSdk())
  }

  @Test
  fun testLightTestsWithDefaultTestProjectStructureForAndroidTestCase() {
    setupTestProjectFromAndroidModel(project, File(myFixture.tempDirPath), createAndroidProjectBuilderForDefaultTestProjectStructure())
    assertThat(ModuleManager.getInstance(project).modules).asList().hasSize(1)
    assertThat(ModuleManager.getInstance(project).modules).asList().contains(myModule)
    val dump = project.saveAndDump(additionalRoots = mapOf("TEMP" to File(myFixture.tempDirPath)))
    assertIsEqualToSnapshot(dump)
  }
}

