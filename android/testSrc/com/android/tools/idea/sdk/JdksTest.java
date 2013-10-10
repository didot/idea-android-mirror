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
package com.android.tools.idea.sdk;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Tests for {@link Jdks}.
 */
public class JdksTest extends IdeaTestCase {
  private String myJdk6HomePath;
  private String myJdk7HomePath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myJdk6HomePath = getSystemPropertyOrEnvironmentVariable("JAVA6_HOME");
    myJdk7HomePath = getSystemPropertyOrEnvironmentVariable("JAVA7_HOME");
  }

  @NotNull
  private static String getSystemPropertyOrEnvironmentVariable(@NotNull String name) {
    String s = System.getProperty(name);
    if (Strings.isNullOrEmpty(s)) {
      s = System.getenv(name);
    }
    if (Strings.isNullOrEmpty(s)) {
      fail(String.format("Please set the system property or environment variable '%1$s'", name));
    }
    return s;
  }

  public void testGetBestJdkHomePathWithLangLevel1dot6() {
    List<String> jdkHomePaths = Lists.newArrayList(myJdk6HomePath, myJdk7HomePath);
    String best = Jdks.getBestJdkHomePath(jdkHomePaths, LanguageLevel.JDK_1_6);
    assertEquals(myJdk6HomePath, best);
  }

  public void testGetBestJdkHomePathWithLangLevel1dot7() {
    List<String> jdkHomePaths = Lists.newArrayList(myJdk6HomePath, myJdk7HomePath);
    String best = Jdks.getBestJdkHomePath(jdkHomePaths, LanguageLevel.JDK_1_7);
    assertEquals(myJdk7HomePath, best);
  }

  public void testHasMatchingLangLevelWithLangLevel1dot6AndJdk7() {
    assertTrue(Jdks.hasMatchingLangLevel(JavaSdkVersion.JDK_1_7, LanguageLevel.JDK_1_6));
  }

  public void testHasMatchingLangLevelWithLangLevel1dot7AndJdk7() {
    assertTrue(Jdks.hasMatchingLangLevel(JavaSdkVersion.JDK_1_7, LanguageLevel.JDK_1_7));
  }

  public void testHasMatchingLangLevelWithLangLevel1dot7AndJdk6() {
    assertFalse(Jdks.hasMatchingLangLevel(JavaSdkVersion.JDK_1_6, LanguageLevel.JDK_1_7));
  }
}
