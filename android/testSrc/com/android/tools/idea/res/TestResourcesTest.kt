/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import org.jetbrains.android.dom.inspections.AndroidDomInspection

class TestResourcesTest : AndroidGradleTestCase() {
  fun testResolveErrors() {
    loadProject(TestProjectPaths.TEST_RESOURCES)
    myFixture.enableInspections(AndroidDomInspection())

    myFixture.openFileInEditor(myFixture.project.baseDir.findFileByRelativePath("app/src/androidTest/AndroidManifest.xml")!!)
    myFixture.checkHighlighting()

    myFixture.openFileInEditor(myFixture.project.baseDir.findFileByRelativePath("app/src/androidTest/res/values/strings.xml")!!)
    myFixture.checkHighlighting()
  }
}
