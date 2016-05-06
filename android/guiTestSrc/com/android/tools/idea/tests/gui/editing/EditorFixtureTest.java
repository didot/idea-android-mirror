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
package com.android.tools.idea.tests.gui.editing;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlEditorFixture;
import com.google.common.base.Strings;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunIn(TestGroup.EDITING)
@RunWith(GuiTestRunner.class)
public class EditorFixtureTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void moveToLine_scrollsWhenNeeded() throws Exception {
    int lineNumber = guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/java/google/simpleapplication/MyActivity.java")
      .enterText(Strings.repeat("\n", 100))
      .moveToLine(1)
      .getCurrentLineNumber();
    assertThat(lineNumber).isEqualTo(1);
  }

  @Test
  public void open_selectsEditorTab() throws Exception {
    NlEditorFixture layoutEditor = guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(false);  // false: don't select the editor; expect it to be selected already
    assertThat(layoutEditor).isNotNull();  // non-null means an AndroidDesignerEditor is selected
  }
}
