/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JRadioButtonFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

public class SelectRefactoringDialogFixture extends IdeaDialogFixture<DialogWrapper> {
  @NotNull
  public static SelectRefactoringDialogFixture findByTitle(@NotNull Robot robot) {
    final Ref<DialogWrapper> wrapperRef = new Ref<>();
    JDialog dialog = GuiTests.waitUntilShowing(robot, Matchers.byTitle(JDialog.class, "SelectRefactoring").and(
      new GenericTypeMatcher<JDialog>(JDialog.class) {
        @Override
        protected boolean isMatching(@NotNull JDialog dialog) {
          DialogWrapper wrapper = getDialogWrapperFrom(dialog, DialogWrapper.class);
          if (wrapper != null) {
            wrapperRef.set(wrapper);
            return true;
          }
          return false;
        }
      }));
    return new SelectRefactoringDialogFixture(robot, dialog, wrapperRef.get());
  }

  public void selectRenameModule() {
    JRadioButton renameModuleRadioButton = robot().finder().find(target(), Matchers.byText(JRadioButton.class, "Rename module"));
    new JRadioButtonFixture(robot(), renameModuleRadioButton).select();
  }

  public void clickOk() {
    findAndClickOkButton(this);
  }

  private SelectRefactoringDialogFixture(@NotNull Robot robot, @NotNull JDialog target, @NotNull DialogWrapper dialogWrapper) {
    super(robot, target, dialogWrapper);
  }
}
