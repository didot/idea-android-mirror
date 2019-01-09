/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.folding;

import org.jetbrains.android.AndroidTestCase;

public class ResourceFoldingBuilderTest extends AndroidTestCase {

  public void testJavaStrings() { performTest(".java"); }
  public void testJavaStrings2() { performTest(".java"); }
  public void testJavaDimens() { performTest(".java"); }
  public void testXmlString() { performTest(".xml"); }
  public void testPlurals() { performTest(".java"); }
  public void testStaticImports() { performTest(".java"); }

  private void performTest(String extension) {
    myFixture.copyFileToProject("/R.java", "src/p1/p2/R.java");
    myFixture.copyFileToProject("/folding/values.xml", "res/values/values.xml");

    final String fileName = getTestName(true) + extension;

    myFixture.testFoldingWithCollapseStatus(getTestDataPath() + "/folding/" + fileName,
                                            myFixture.getTempDirPath() + "/src/p1/p2/" + fileName);
  }
}
