/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.gct.testrecorder.ui.RecordingDialog;
import javax.swing.JDialog;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

/** Fixture for {@link com.google.gct.testrecorder.ui.RecordingDialog}. */
public class RecordingDialogFixture extends IdeaDialogFixture<RecordingDialog> {
  @NotNull
  public static RecordingDialogFixture find(@NotNull Robot robot) {
    return new RecordingDialogFixture(robot, find(robot, RecordingDialog.class, Matchers.byTitle(JDialog.class, "Record Your Test")));
  }

  private RecordingDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<RecordingDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  public void clickOk() {
    findAndClickOkButton(this);
  }
}

