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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.surface.layout.TestPositionableContent
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.surface.layout.PositionableContent
import com.android.tools.idea.uibuilder.surface.layout.SingleDirectionLayoutManager

private class ForcedDirectoryLayoutManager(@SwingCoordinate private val horizontalPadding: Int,
                                           @SwingCoordinate private val verticalPadding: Int,
                                           @SwingCoordinate private val horizontalViewDelta: Int,
                                           @SwingCoordinate private val verticalViewDelta: Int,
                                           startBorderAlignment: Alignment,
                                           private val forceVertical: Boolean) : SingleDirectionLayoutManager(horizontalPadding,
                                                                                                              verticalPadding,
                                                                                                              horizontalViewDelta,
                                                                                                              verticalViewDelta,
                                                                                                              startBorderAlignment) {
  override fun isVertical(content: Collection<PositionableContent>,
                          @SwingCoordinate availableWidth: Int,
                          @SwingCoordinate availableHeight: Int): Boolean = forceVertical
}

class SingleDirectionLayoutManagerTest : LayoutTestCase() {

  fun testLayoutHorizontally() {
    val paddingX = 10
    val paddingY = 20
    val screenDeltaX = 30
    val screenDeltaY = 30

    val manager = SingleDirectionLayoutManager(paddingX, paddingY, screenDeltaX, screenDeltaY)

    val availableWidth = 1000
    val availableHeight = 300

    run {
      val w = 100
      val h = 150
      val sceneView1 = TestPositionableContent(width = w, height = h)
      manager.layout(listOf(sceneView1), availableWidth, availableHeight)
      assertEquals((availableWidth - w) / 2, sceneView1.x)
      assertEquals((availableHeight - 150) / 2, sceneView1.y)
    }

    run {
      val w = 100
      val h = 280
      val sceneView1 = TestPositionableContent(width = w, height = h)
      val sceneView2 = TestPositionableContent(width = w, height = h)
      manager.layout(listOf(sceneView1, sceneView2), availableWidth, availableHeight, false)
      assertEquals((availableWidth - w * 2 - screenDeltaX) / 2, sceneView1.x)
      assertEquals(paddingY, sceneView1.y)
      assertEquals((availableWidth - w * 2 - screenDeltaX) / 2 + w + screenDeltaX, sceneView2.x)
      assertEquals(paddingY, sceneView2.y)
    }

    run {
      val w = 500
      val h = 1000
      val sceneView1 = TestPositionableContent(width = w, height = h)
      val sceneView2 = TestPositionableContent(width = w, height = h)
      manager.layout(listOf(sceneView1, sceneView2), availableWidth, availableHeight, false)
      assertEquals(paddingX, sceneView1.x)
      assertEquals(paddingY, sceneView1.y)
      assertEquals(paddingX + w + screenDeltaX, sceneView2.x)
      assertEquals(paddingY, sceneView2.y)
    }
  }

  fun testLayoutVertically() {
    val paddingX = 10
    val paddingY = 20
    val screenDeltaX = 30
    val screenDeltaY = 30

    val manager = SingleDirectionLayoutManager(paddingX, paddingY, screenDeltaX, screenDeltaY)

    val availableWidth = 300
    val availableHeight = 1000

    run {
      val w = 100
      val h = 150
      val sceneView1 = TestPositionableContent(width = w, height = h)
      manager.layout(listOf(sceneView1), availableWidth, availableHeight)
      assertEquals((availableWidth - w) / 2, sceneView1.x)
      assertEquals((availableHeight - h) / 2, sceneView1.y)
    }

    run {
      val w = 100
      val h = 280
      val sceneView1 = TestPositionableContent(width = w, height = h)
      val sceneView2 = TestPositionableContent(width = w, height = h)
      manager.layout(listOf(sceneView1, sceneView2), availableWidth, availableHeight, false)
      assertEquals((availableWidth - w) / 2, sceneView1.x)
      assertEquals((availableHeight - h * 2 - screenDeltaY) / 2, sceneView1.y)
      assertEquals((availableWidth - w) / 2, sceneView2.x)
      assertEquals((availableHeight - h * 2 - screenDeltaY) / 2 + h + screenDeltaY, sceneView2.y)
    }

    run {
      val w = 100
      val h = 500
      val sceneView1 = TestPositionableContent(width = w, height = h)
      val sceneView2 = TestPositionableContent(width = w, height = h)
      manager.layout(listOf(sceneView1, sceneView2), availableWidth, availableHeight, false)
      assertEquals((availableWidth - w) / 2, sceneView1.x)
      assertEquals(paddingY, sceneView1.y)
      assertEquals((availableWidth - w) / 2, sceneView2.x)
      assertEquals(paddingY + h + screenDeltaY, sceneView2.y)
    }

    run {
      val w = 500
      val h = 1000
      val sceneView1 = TestPositionableContent(width = w, height = h)
      val sceneView2 = TestPositionableContent(width = w, height = h)
      manager.layout(listOf(sceneView1, sceneView2), availableWidth, availableHeight, false)
      assertEquals(paddingX, sceneView1.x)
      assertEquals(paddingY, sceneView1.y)
      assertEquals(paddingX, sceneView2.x)
      assertEquals(paddingY + h + screenDeltaY, sceneView2.y)
    }
  }

  fun testVerticalLayoutHorizontalAlignments() {
    val availableWidth = 300
    val availableHeight = 1000
    val paddingX = 10
    val paddingY = 20
    val screenDeltaX = 30
    val screenDeltaY = 30
    val sceneViews = listOf(
      TestPositionableContent(width = 50, height = 200),
      TestPositionableContent(width = 100, height = 150))

    val maxWidth = sceneViews.map { it.width }.maxOrNull()!!
    val minX = (availableWidth / 2) - (maxWidth / 2)

    SingleDirectionLayoutManager.Alignment.START.also { alignment ->
      val manager = ForcedDirectoryLayoutManager(paddingX, paddingY, screenDeltaX, screenDeltaY, alignment, true)
      manager.layout(sceneViews, availableWidth, availableHeight, false)
      assertEquals(minX, sceneViews[0].x)
      assertEquals(minX, sceneViews[1].x)
    }

    SingleDirectionLayoutManager.Alignment.CENTER.also { alignment ->
      val manager = ForcedDirectoryLayoutManager(paddingX, paddingY, screenDeltaX, screenDeltaY, alignment, true)
      manager.layout(sceneViews, availableWidth, availableHeight, false)
      assertEquals((availableWidth / 2) - (sceneViews[0].width / 2), sceneViews[0].x)
      assertEquals((availableWidth / 2) - (sceneViews[1].width / 2), sceneViews[1].x)
    }

    SingleDirectionLayoutManager.Alignment.END.also { alignment ->
      val manager = ForcedDirectoryLayoutManager(paddingX, paddingY, screenDeltaX, screenDeltaY, alignment, true)
      manager.layout(sceneViews, availableWidth, availableHeight, false)
      assertEquals(availableWidth - sceneViews[0].width, sceneViews[0].x)
      assertEquals(availableWidth - sceneViews[1].width, sceneViews[1].x)
    }
  }

  fun testHorizontalLayoutVerticalAlignments() {
    val availableWidth = 300
    val availableHeight = 1000
    val paddingX = 10
    val paddingY = 20
    val screenDeltaX = 30
    val screenDeltaY = 30
    val sceneViews = listOf(
      TestPositionableContent(width = 50, height = 200),
      TestPositionableContent(width = 100, height = 150))

    val maxHeight = sceneViews.map { it.height }.maxOrNull()!!
    val minY = (availableHeight / 2) - (maxHeight / 2)

    SingleDirectionLayoutManager.Alignment.START.also { alignment ->
      val manager = ForcedDirectoryLayoutManager(paddingX, paddingY, screenDeltaX, screenDeltaY, alignment, false)
      manager.layout(sceneViews, availableWidth, availableHeight, false)
      assertEquals(minY, sceneViews[0].y)
      assertEquals(minY, sceneViews[1].y)
    }

    SingleDirectionLayoutManager.Alignment.CENTER.also { alignment ->
      val manager = ForcedDirectoryLayoutManager(paddingX, paddingY, screenDeltaX, screenDeltaY, alignment, false)
      manager.layout(sceneViews, availableWidth, availableHeight, false)
      assertEquals((availableHeight / 2) - (sceneViews[0].height / 2), sceneViews[0].y)
      assertEquals((availableHeight / 2) - (sceneViews[1].height / 2), sceneViews[1].y)
    }

    SingleDirectionLayoutManager.Alignment.END.also { alignment ->
      val manager = ForcedDirectoryLayoutManager(paddingX, paddingY, screenDeltaX, screenDeltaY, alignment, false)
      manager.layout(sceneViews, availableWidth, availableHeight, false)
      assertEquals(availableHeight - sceneViews[0].height, sceneViews[0].y)
      assertEquals(availableHeight - sceneViews[1].height, sceneViews[1].y)
    }
  }
}
