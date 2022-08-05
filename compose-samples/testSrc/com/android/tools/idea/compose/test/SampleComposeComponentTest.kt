/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.test

import com.android.tools.adtui.swing.FakeUi
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension


@RunsInEdt
class SampleComposeComponentTest {
  @get:Rule
  val edtRule = EdtRule()

  @Test
  fun instantiateSampleComponent() {
    val composePanel = SampleComposeComponent().also {
      it.size = Dimension(800, 600)
      it.preferredSize = Dimension(800, 600)
    }

    // TODO: This test does not yet render the Compose component since FakeUi does not support the Skia rendering.
    val fakeUi = FakeUi(composePanel, 1.0, false)
    fakeUi.render()
  }
}