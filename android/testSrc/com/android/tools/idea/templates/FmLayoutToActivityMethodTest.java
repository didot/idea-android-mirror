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
package com.android.tools.idea.templates;

import freemarker.template.SimpleScalar;
import freemarker.template.TemplateModelException;

import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class FmLayoutToActivityMethodTest extends TestCase {
  @SuppressWarnings("rawtypes")
  private static void check(String s, String expected) throws TemplateModelException {
    FmLayoutToActivityMethod method = new FmLayoutToActivityMethod();
    List list = Collections.singletonList(new SimpleScalar(s));
    assertEquals(expected, ((SimpleScalar)method.exec(list)).getAsString());
  }

  public void testConvertWithoutPrefix() throws Exception {
    check("foo", "FooActivity");
  }

  public void testConvertWithPrefix() throws Exception {
    check("activity_foo", "FooActivity");
  }

  public void testConvertPrefixOnly() throws Exception {
    check("activity_", "MainActivity");
  }

  public void testConvertNameIsSubsetOfActivity() throws Exception {
    check("activ", "ActivActivity");
  }

  public void testConvertEmptyString() throws Exception {
    check("", "MainActivity");
  }

  public void testConvertWithTrailingDigit() throws Exception {
    check("activity_foo2", "Foo2Activity");
  }

  public void testConvertWithTrailingDigits() throws Exception {
    check("activity_foo200", "Foo200Activity");
  }

  public void testConvertWithOnlyDigits() throws Exception {
    check("activity_200", "MainActivity");
  }
}
