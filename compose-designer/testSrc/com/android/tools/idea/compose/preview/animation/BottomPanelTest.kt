/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation

import com.android.SdkConstants
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.runInEdtAndGet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomPanelTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var surface: DesignSurface

  @Before
  fun setUp() {
    val model = runInEdtAndGet {
      NlModelBuilderUtil.model(
        projectRule,
        "layout",
        "layout.xml",
        ComponentDescriptor(SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER)
      ).build()
    }
    surface = NlDesignSurface.builder(projectRule.project, projectRule.fixture.testRootDisposable).build()
    surface.addModelWithoutRender(model)
  }

  private val minimumSize = Dimension(10, 10)

  @Test
  fun `reset button is visible and clickable`(): Unit = invokeAndWaitIfNeeded {
    val panel = createBottomPanel()
    val ui = FakeUi(panel.parent).apply {
      updateToolbars()
      layout()
    }
    (panel.components[0] as Container).components[2].also {
      // Reset button.
      assertTrue(it.isVisible)
      assertTrue(it.isEnabled)
      TestUtils.assertBigger(minimumSize, it.size)
      // After clicking button callback is called.
      var resetCalls = 0
      panel.addResetListener { resetCalls++ }
      ui.clickOn(it)
      ui.updateToolbars()
      assertEquals(1, resetCalls)
    }
  }

  @Test
  fun `reset button is disabled if coordination is not available`(): Unit = invokeAndWaitIfNeeded {
    val panel = createBottomPanel(false)
    val ui = FakeUi(panel.parent).apply {
      updateToolbars()
      layout()
    }
    (panel.components[0] as Container).components[2].also {
      // Reset button.
      assertTrue(it.isVisible)
      assertFalse(it.isEnabled)
      TestUtils.assertBigger(minimumSize, it.size)
    }
  }


  @Test
  fun `label is visible`(): Unit = invokeAndWaitIfNeeded {
    val panel = createBottomPanel().apply {
      clockTimeMs = 1234
    }
    FakeUi(panel.parent).apply {
      updateToolbars()
      layout()
    }
    val labelComponent = (panel.components[0] as Container).components[0]
    assertTrue(labelComponent.isVisible)
    TestUtils.assertBigger(minimumSize, labelComponent.size)
    panel.clockTimeMs = 1234567890
    TestUtils.assertBigger(Dimension(40, 10), labelComponent.size)

  }


  @Test
  fun `ui preview renders correctly`(): Unit = invokeAndWaitIfNeeded {
    val panel = createBottomPanel().apply {
      clockTimeMs = 1234
    }
    FakeUi(panel.parent).apply {
      updateToolbars()
      layout()
      // Uncomment to preview ui.
      //render()
    }
  }


  /** Create [BottomPanel] with 300x500 size. */
  private fun createBottomPanel(withCoordination: Boolean = true): BottomPanel {
    val panel = BottomPanel(TestUtils.testPreviewState(withCoordination), surface) {}
    JPanel(BorderLayout()).apply {
      setSize(300, 500)
      add(panel, BorderLayout.SOUTH)
    }
    return panel
  }
}