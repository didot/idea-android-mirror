/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.tools.idea.gradle.project.build.output.TestMessageEventConsumer
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AgpVersionNotSupportedIssueCheckerTest {
  private val agpVersionNotSupportedIssueCheckerTest = AgpVersionNotSupportedIssueChecker()

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testCheckIssue() {

    val expectedNotificationMessage = "The project is using an incompatible version (AGP 3.1.4) of the Android Gradle plugin."
    val error = "The project is using an incompatible version (AGP 3.1.4) of the Android Gradle plugin. Minimum supported version is AGP 3.2."

    val issueData = GradleIssueData(":", Throwable(error), null, null)
    val buildIssue = agpVersionNotSupportedIssueCheckerTest.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.quickFixes.size).isEqualTo(1)
    assertThat(buildIssue.description).contains(expectedNotificationMessage)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(OpenLinkQuickFix::class.java)
  }

  @Test
  fun testIssueHandled() {
    assertThat(
      agpVersionNotSupportedIssueCheckerTest.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "The project is using an incompatible version of the Android Gradle plugin. Minimum supported version is AGP 3.2.",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }
}