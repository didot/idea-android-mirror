/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors;

import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.MISSING_BUILD_TOOLS;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncQuickFix.FIX_ANDROID_GRADLE_PLUGIN_VERSION_HYPERLINK;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncQuickFix.INSTALL_BUILD_TOOLS_HYPERLINK;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.sync.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallBuildToolsHyperlink;
import com.android.tools.idea.gradle.project.sync.issues.TestSyncIssueUsageReporter;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * Tests for {@link MissingBuildToolsErrorHandler}.
 */
public class MissingBuildToolsErrorHandlerTest extends AndroidGradleTestCase {

  private GradleSyncMessagesStub mySyncMessagesStub;
  private TestSyncIssueUsageReporter myUsageReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject(), getTestRootDisposable());
    myUsageReporter = TestSyncIssueUsageReporter.replaceSyncMessagesService(getProject(), getTestRootDisposable());
}

  public void testHandleError() throws Exception {
    String errMsg = "Failed to find Build Tools revision 24.0.0 rc4";
    Throwable cause = new IllegalStateException(errMsg);
    registerSyncErrorToSimulate(cause);

    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText()).isEqualTo(errMsg);

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(2);
    assertThat(quickFixes.get(0)).isInstanceOf(InstallBuildToolsHyperlink.class);
    assertThat(quickFixes.get(1)).isInstanceOf(FixAndroidGradlePluginVersionHyperlink.class);

    assertEquals(MISSING_BUILD_TOOLS, myUsageReporter.getCollectedFailure());
    assertEquals(ImmutableList.of(INSTALL_BUILD_TOOLS_HYPERLINK, FIX_ANDROID_GRADLE_PLUGIN_VERSION_HYPERLINK), myUsageReporter.getCollectedQuickFixes());
  }

  public void testHandleErrorOlderPlugin()  {
    List<NotificationHyperlink> quickFixes =
      MissingBuildToolsErrorHandler.getQuickFixHyperlinks("27.0.1", GradleVersion.parse("3.0.0"), GradleVersion.parse("3.1.0"), false);

    assertThat(quickFixes).hasSize(2);
    assertThat(quickFixes.get(0)).isInstanceOf(InstallBuildToolsHyperlink.class);
    assertThat(quickFixes.get(1)).isInstanceOf(FixAndroidGradlePluginVersionHyperlink.class);
  }

  public void testHandleErrorCurrentPlugin() {
    List<NotificationHyperlink> quickFixes =
      MissingBuildToolsErrorHandler.getQuickFixHyperlinks("27.0.1", GradleVersion.parse("3.1.0"), GradleVersion.parse("3.1.0"), false);

    assertThat(quickFixes).hasSize(1);
    assertThat(quickFixes.get(0)).isInstanceOf(InstallBuildToolsHyperlink.class);
  }

  public void testHandleErrorNewerPlugin() {
    List<NotificationHyperlink> quickFixes =
      MissingBuildToolsErrorHandler.getQuickFixHyperlinks("27.0.1", GradleVersion.parse("3.2.0"), GradleVersion.parse("3.1.0"), false);

    assertThat(quickFixes).hasSize(1);
    assertThat(quickFixes.get(0)).isInstanceOf(InstallBuildToolsHyperlink.class);
  }
}