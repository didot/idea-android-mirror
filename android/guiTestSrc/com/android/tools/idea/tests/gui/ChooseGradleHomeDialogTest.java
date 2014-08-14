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
package com.android.tools.idea.tests.gui;

import com.android.tools.idea.gradle.project.ChooseGradleHomeDialog;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.ChooseGradleHomeDialogFixture;
import com.intellij.openapi.application.ApplicationManager;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;

import static com.android.tools.idea.tests.gui.framework.GuiTests.GRADLE_HOME_PATH_PROPERTY;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static junit.framework.Assert.fail;
import static org.fest.util.Strings.quote;

/**
 * UI Test for {@link com.android.tools.idea.gradle.project.ChooseGradleHomeDialog}.
 */
public class ChooseGradleHomeDialogTest extends GuiTestCase {
  private static final String OLD_GRADLE_HOME_PATH_PROPERTY = "old.gradle.home.path";
  private static final String MINIMUM_GRADLE_VERSION = "2.0";

  @Test @IdeGuiTest
  public void testValidationWithInvalidMinimumGradleVersion() {
    String oldGradleHome = System.getProperty(OLD_GRADLE_HOME_PATH_PROPERTY);
    if (isEmpty(oldGradleHome)) {
      fail("Please specify, in the system property " + quote(OLD_GRADLE_HOME_PATH_PROPERTY) +
           ", the path of an old, unsupported version of Gradle (e.g. 1.12)");
    }

    ChooseGradleHomeDialogFixture dialog = launchChooseGradleHomeDialog();
    dialog.chooseGradleHome(new File(oldGradleHome))
          .clickOK()
          .requireValidationError("Gradle " + MINIMUM_GRADLE_VERSION + " or newer is required")
          .close();

  }

  @Test
  public void testValidateWithValidMinimumGradleVersion() {
    String gradleHome = System.getProperty(GRADLE_HOME_PATH_PROPERTY);
    if (isEmpty(gradleHome)) {
      fail("Please specify, in the system property " + quote(GRADLE_HOME_PATH_PROPERTY) +
           ", the path of a supported version of Gradle (e.g. 2.0)");
    }

    ChooseGradleHomeDialogFixture dialog = launchChooseGradleHomeDialog();
    dialog.chooseGradleHome(new File(gradleHome))
          .clickOK()
          .requireNotShowing();  // if it is not showing on the screen, it means that there were no validation errors.
  }

  @NotNull
  private ChooseGradleHomeDialogFixture launchChooseGradleHomeDialog() {
    final ChooseGradleHomeDialog dialog = GuiActionRunner.execute(new GuiQuery<ChooseGradleHomeDialog>() {
      @Override
      protected ChooseGradleHomeDialog executeInEDT() throws Throwable {
        return new ChooseGradleHomeDialog(MINIMUM_GRADLE_VERSION);
      }
    });

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        dialog.setModal(false);
        dialog.show();
      }
    });

    return ChooseGradleHomeDialogFixture.find(myRobot);
  }
}
