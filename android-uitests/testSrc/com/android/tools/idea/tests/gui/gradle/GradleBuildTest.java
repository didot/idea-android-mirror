/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.SelectSdkDialogFixture;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static junit.framework.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class GradleBuildTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testBuildWithInvalidJavaHome() throws IOException {
    String jdkPathValue = System.getProperty("jdk.path");
    assumeTrue("jdk.path system property missing", !isEmpty(jdkPathValue));

    File jdkPath = new File(jdkPathValue);

    guiTest.importSimpleApplication();
    guiTest.ideFrame().invokeProjectMakeAndSimulateFailure("Supplied javaHome is not a valid folder.");

    // Find message dialog explaining the source of the error.
    guiTest.ideFrame().findMessageDialog("Gradle Running").clickOk();

    // Find the dialog to select the path of the JDK.
    SelectSdkDialogFixture selectSdkDialog = SelectSdkDialogFixture.find(guiTest.robot());
    selectSdkDialog.setJdkPath(jdkPath)
                   .clickOk();

    assertEquals(jdkPath.getPath(), IdeSdks.getInstance().getJdkPath().getPath());
  }

  /**
   * Verifies that a simple app is configured to build using Jack and Jill.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: d6dc23f3-33ff-4ffc-af80-6ab822388274
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import the JackAndJillApp project.
   *   2. Build the project.
   *   Verify:
   *   The project builds.
   *   </pre>
   * <p>
   * This test does not try and run the project.
   */
  @RunIn(TestGroup.QA)
  @Test
  public void compileWithJack() throws IOException {
    GradleInvocationResult buildResult = guiTest.importProjectAndWaitForProjectSyncToFinish("JackAndJillApp")
      .invokeProjectMake();

    assertThat(buildResult.isBuildSuccessful()).named("Gradle build successful").isTrue();
  }
}
