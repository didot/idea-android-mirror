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
package com.android.tools.idea.tests.gui.theme;

import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.fixture.ChooseResourceDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.*;
import org.fest.swing.data.TableCell;
import org.fest.swing.fixture.FontFixture;
import org.fest.swing.fixture.JTableCellFixture;
import org.junit.Test;

import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.TestGroup.THEME;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.data.TableCell.row;
import static org.junit.Assert.*;

/**
 * UI tests regarding the ChooseResourceDialog
 */
@BelongsToTestGroups({THEME})
public class ChooseResourceDialogTest extends GuiTestCase {

  @Test
  public void testColorStateList() throws IOException {
    importProjectAndWaitForProjectSyncToFinish("StateListApplication");
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(getIdeFrame());
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    TableCell cell = row(7).column(0);

    FontFixture cellFont = themeEditorTable.fontAt(cell);
    cellFont.requireBold();
    assertEquals("android:textColorPrimary", themeEditorTable.attributeNameAt(cell));
    assertEquals("@color/text_color", themeEditorTable.valueAt(cell));

    JTableCellFixture stateListCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(robot(), (ResourceComponent)stateListCell.editor());
    stateListCell.startEditing();
    resourceComponent.getSwatchButton().click();

    final ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(robot());

    StateListPickerFixture stateListPicker = dialog.getStateListPicker();
    java.util.List<StateListComponentFixture> states = stateListPicker.getStateComponents();
    assertThat(states).hasSize(4);

    final StateListComponentFixture state0 = states.get(0);
    assertEquals("Not enabled", state0.getStateName());
    assertEquals("?android:attr/colorForeground", state0.getValue());
    assertFalse(state0.getValueComponent().hasWarningIcon());
    assertTrue(state0.isAlphaVisible());
    assertEquals("@dimen/text_alpha", state0.getAlphaValue());
    assertFalse(state0.getAlphaComponent().hasWarningIcon());

    final StateListComponentFixture state1 = states.get(1);
    assertEquals("Checked", state1.getStateName());
    assertEquals("#5034FAB2", state1.getValue());
    assertFalse(state1.getValueComponent().hasWarningIcon());
    assertFalse(state1.isAlphaVisible());

    final StateListComponentFixture state2 = states.get(2);
    assertEquals("Pressed", state2.getStateName());
    assertEquals("@color/invalidColor", state2.getValue());
    assertTrue(state2.getValueComponent().hasWarningIcon());
    assertFalse(state2.isAlphaVisible());

    final StateListComponentFixture state3 = states.get(3);
    assertEquals("Default", state3.getStateName());
    assertEquals("?attr/myColorAttribute", state3.getValue());
    assertFalse(state3.getValueComponent().hasWarningIcon());
    assertFalse(state3.isAlphaVisible());

    dialog.clickCancel();
    stateListCell.stopEditing();
  }

  @Test
  public void testEditColorReference() throws IOException {
    importProjectAndWaitForProjectSyncToFinish("StateListApplication");
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(getIdeFrame());
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    TableCell cell = row(1).column(0);

    FontFixture cellFont = themeEditorTable.fontAt(cell);
    cellFont.requireBold();
    assertEquals("android:colorPrimary", themeEditorTable.attributeNameAt(cell));
    assertEquals("@color/ref_color", themeEditorTable.valueAt(cell));

    JTableCellFixture stateListCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(robot(), (ResourceComponent)stateListCell.editor());
    stateListCell.startEditing();
    resourceComponent.getSwatchButton().click();

    ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(robot());

    SwatchComponentFixture state1 = dialog.getEditReferencePanel().getSwatchComponent();
    assertEquals("@color/myColor", state1.getText());
    assertFalse(state1.hasWarningIcon());

    dialog.clickCancel();
    stateListCell.stopEditing();
  }
}
