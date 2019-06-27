/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea;

import com.android.testutils.JarTestSuiteRunner;
import com.android.tools.idea.editors.layoutInspector.LayoutInspectorCaptureTaskTest;
import com.android.tools.idea.editors.layoutInspector.ui.PropertiesTablePanelTest;
import com.android.tools.tests.IdeaTestSuiteBase;
import org.junit.runner.RunWith;

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses({LayoutInspectorTestSuite.class, PropertiesTablePanelTest.class,
  LayoutInspectorCaptureTaskTest.class})
@SuppressWarnings("NewClassNamingConvention") // Not a test.
public class LayoutInspectorTestSuite extends IdeaTestSuiteBase {

  static {
    symlinkToIdeaHome(
      "tools/adt/idea/android/annotations",
      "tools/adt/idea/android/testData",
      "tools/adt/idea/android-layout-inspector/testData",
      "tools/base/templates",
      "tools/idea/java");
  }
}
