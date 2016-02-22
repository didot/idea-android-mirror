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
package org.jetbrains.android.dom;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;

public class AndroidColorsTest extends AndroidDomTest {
  public AndroidColorsTest() {
    super(true, "dom/color");
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "res/values/" + testFileName;
  }

  public void testColorNoTypos() throws Throwable {
    VirtualFile virtualFile = copyFileToProject("colors_value.xml");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.enableInspections(new SpellCheckingInspection());
    myFixture.checkHighlighting(true, true, true);
  }
}
