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
package com.android.tools.idea.lang.aidl;

import com.android.tools.idea.lang.LangTestDataKt;
import com.intellij.testFramework.ParsingTestCase;

public class AidlParserTest extends ParsingTestCase {
  public AidlParserTest() {
    super("lang/aidl/parser", AidlFileType.DEFAULT_ASSOCIATED_EXTENSION, new AidlParserDefinition());
  }

  @Override
  protected String getTestDataPath() {
    return LangTestDataKt.getTestDataPath();
  }

  @Override
  protected boolean skipSpaces() {
    return true;
  }

  public void testIAidlInterface() {
    doTest(true);
  }

  public void testParcelable() {
    doTest(true);
  }

  public void testEmptyMethodParameters() {
    doTest(true);
  }

  public void testImportRecover() {
    // test recover when import statement is incomplete
    doTest(true);
  }

  public void testDeclarationRecover() {
    // test recover when declaration is incomplete
    doTest(true);
  }

  public void testMethodRecover() {
    // test recover when method definition is incomplete
    doTest(true);
  }

  public void testParameterRecover() {
    // test recover when method parameter is incomplete
    doTest(true);
  }
}
