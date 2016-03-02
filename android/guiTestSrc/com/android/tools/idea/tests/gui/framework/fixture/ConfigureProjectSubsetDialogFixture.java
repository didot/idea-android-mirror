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

import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.DialogMatcher;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.data.TableCell.row;
import static org.fest.swing.finder.WindowFinder.findDialog;

public class ConfigureProjectSubsetDialogFixture {
  @NotNull private DialogFixture myDialog;
  @NotNull private final JTableFixture myModulesTable;

  @NotNull
  public static ConfigureProjectSubsetDialogFixture find(@NotNull Robot robot) {
    DialogMatcher matcher = withTitle("Select Modules to Include in Project Subset").andShowing();
    DialogFixture dialog = findDialog(matcher).withTimeout(TimeUnit.MINUTES.toMillis(2)).using(robot);
    return new ConfigureProjectSubsetDialogFixture(dialog);
  }

  private ConfigureProjectSubsetDialogFixture(@NotNull DialogFixture dialog) {
    myDialog = dialog;
    Robot robot = dialog.robot();
    myModulesTable = new JTableFixture(robot, robot.finder().findByType(dialog.target(), JTable.class, true));
  }

  @NotNull
  public ConfigureProjectSubsetDialogFixture selectModule(@NotNull String moduleName, boolean selected) {
    JTableCellFixture cell = myModulesTable.cell(moduleName);
    myModulesTable.enterValue(row(cell.row()).column(0), String.valueOf(selected));
    return this;
  }

  public void clickOk() {
    findAndClickOkButton(myDialog);
  }
}
