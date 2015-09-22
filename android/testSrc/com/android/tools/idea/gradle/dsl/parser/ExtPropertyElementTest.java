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
package com.android.tools.idea.gradle.dsl.parser;

import java.io.IOException;

/**
 * Tests for {@link ExtPropertyElement}.
 */
public class ExtPropertyElementTest extends GradleBuildModelParserTestCase {

  public void testParsingSimplePropertyPerLine() throws IOException {
    String text = "ext.COMPILE_SDK_VERSION = 21\n" +
                  "ext.srcDirName = 'src/java'";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    ExtPropertyElement compileSdkVersion = buildModel.getExtProperty("COMPILE_SDK_VERSION");
    assertNotNull(compileSdkVersion);
    assertEquals(21, compileSdkVersion.getValue());

    ExtPropertyElement srcDirName = buildModel.getExtProperty("srcDirName");
    assertNotNull(srcDirName);
    assertEquals("src/java", srcDirName.getValue());
  }

  public void testParsingSimplePropertyInExtBlock() throws IOException {
    String text = "ext {\n" +
                  "   COMPILE_SDK_VERSION = 21\n" +
                  "   srcDirName = 'src/java'\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    ExtPropertyElement compileSdkVersion = buildModel.getExtProperty("COMPILE_SDK_VERSION");
    assertNotNull(compileSdkVersion);
    assertEquals(21, compileSdkVersion.getValue());

    ExtPropertyElement srcDirName = buildModel.getExtProperty("srcDirName");
    assertNotNull(srcDirName);
    assertEquals("src/java", srcDirName.getValue());
  }

  public void testParsingListOfProperties() throws IOException {
    String text = "ext.libraries = [\n" +
                  "    guava: \"com.google.guava:guava:19.0-rc1\",\n" +
                  "    design :  \"com.android.support:design:22.2.1\"\n" +
                  "]";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    ExtPropertyElement guava = buildModel.getExtProperty("libraries.guava");
    assertNotNull(guava);
    assertEquals("com.google.guava:guava:19.0-rc1", guava.getValue());
  }
}