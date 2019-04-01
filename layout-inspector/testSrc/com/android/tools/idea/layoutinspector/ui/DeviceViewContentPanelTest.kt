/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.testutils.TestUtils.getWorkspaceRoot
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.io.File

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"

class DeviceViewContentPanelTest {

  @Before
  fun setUp() {
    InspectorClient.currentInstance = mock(InspectorClient::class.java)
  }

  @After
  fun tearDown() {
    InspectorClient.currentInstance = null
  }

  @Test
  fun testSize() {
    val model = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 0, 0, 50, 50) {
          view(VIEW3, 30, 30, 10, 10)
        }
        view(VIEW2, 60, 160, 10, 20)
      }
    }
    val inspector = LayoutInspector(model)
    val panel = DeviceViewContentPanel(inspector, 0.3, DeviceViewPanel.ViewMode.X_ONLY)
    assertEquals(Dimension(188, 197), panel.preferredSize)

    panel.scale = 1.0
    assertEquals(Dimension(510, 542), panel.preferredSize)

    model.root = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 0, 0, 50, 50)
      }
    }.root
    assertEquals(Dimension(366, 410), panel.preferredSize)
  }

  @Test
  fun testPaint() {
    val model = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 25, 30, 50, 50)
      }
    }

    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(200, 300, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val inspector = LayoutInspector(model)
    val panel = DeviceViewContentPanel(inspector, 1.0, DeviceViewPanel.ViewMode.X_ONLY)
    panel.setSize(200, 300)

    panel.paint(graphics)

    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint.png"), generatedImage, 0.1)

    panel.scale = 0.5
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)

    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_scaled.png"), generatedImage, 0.1)
    panel.scale = 1.0

    panel.model.rotate(0.3, 0.2)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)

    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_rotated.png"), generatedImage, 0.1)
  }

  @Test
  fun testDrag() {
    val model = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 25, 30, 50, 50)
      }
    }

    val inspector = LayoutInspector(model)
    val panel = DeviceViewContentPanel(inspector, 1.0, DeviceViewPanel.ViewMode.X_ONLY)
    panel.setSize(200, 300)
    val fakeUi = FakeUi(panel)
    fakeUi.mouse.drag(10, 10, 10, 10)
    assertEquals(0.01, panel.model.xOff)
    assertEquals(0.0, panel.model.yOff)
    panel.model.resetRotation()

    panel.viewMode = DeviceViewPanel.ViewMode.XY
    fakeUi.mouse.drag(10, 10, 10, 10)
    assertEquals(0.01, panel.model.xOff)
    assertEquals(0.01, panel.model.yOff)

    panel.viewMode = DeviceViewPanel.ViewMode.FIXED
    fakeUi.mouse.drag(10, 10, 10, 10)
    assertEquals(0.0, panel.model.xOff)
    assertEquals(0.0, panel.model.yOff)
  }
}