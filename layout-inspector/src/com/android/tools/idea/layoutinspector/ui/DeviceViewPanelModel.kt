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

import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.google.common.annotations.VisibleForTesting
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.AffineTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

private const val LAYER_SPACING = 150

data class ViewDrawInfo(val bounds: Shape, val transform: AffineTransform, val node: ViewNode, val clip: Rectangle)

class DeviceViewPanelModel(private val model: InspectorModel) {
  @VisibleForTesting
  internal var xOff = 0.0
  @VisibleForTesting
  internal var yOff = 0.0

  private var rootDimension: Dimension = Dimension()
  private var maxDepth: Int = 0

  internal val maxWidth
    get() = hypot((maxDepth * LAYER_SPACING).toFloat(), rootDimension.width.toFloat()).toInt()

  internal val maxHeight
    get() = hypot((maxDepth * LAYER_SPACING).toFloat(), rootDimension.height.toFloat()).toInt()

  @VisibleForTesting
  internal var hitRects = listOf<ViewDrawInfo>()

  init {
    refresh()
  }

  fun findTopRect(x: Double, y: Double): ViewNode? {
    return hitRects.findLast {
      it.bounds.contains(x, y)
    }?.node
  }

  fun rotate(xRotation: Double, yRotation: Double) {
    xOff = (xOff + xRotation).coerceIn(-1.0, 1.0)
    yOff = (yOff + yRotation).coerceIn(-1.0, 1.0)
    refresh()
  }

  @VisibleForTesting
  fun refresh() {
    val root = model.root
    rootDimension = Dimension(root.width, root.height)
    val newHitRects = mutableListOf<ViewDrawInfo>()
    val transform = AffineTransform()
    transform.translate(-root.width / 2.0, -root.height / 2.0)

    val magnitude = min(1.0, hypot(xOff, yOff))
    val angle = if (abs(xOff) < 0.00001) PI / 2.0 else atan(yOff / xOff)

    transform.translate(rootDimension.width / 2.0, rootDimension.height / 2.0)
    transform.rotate(angle)
    maxDepth = findMaxDepth(root)
    val rootBounds = Rectangle(root.x, root.y, root.width, root.height)
    rebuildOneRect(transform, magnitude, 0, angle, root, rootBounds, newHitRects)
    hitRects = newHitRects.toList()
  }

  private fun findMaxDepth(view: ViewNode): Int {
    return 1 + (view.children.values.map { findMaxDepth(it) }.max() ?: 0)
  }

  private fun rebuildOneRect(transform: AffineTransform,
                             magnitude: Double,
                             depth: Int,
                             angle: Double,
                             view: ViewNode,
                             parentClip: Rectangle,
                             newHitRects: MutableList<ViewDrawInfo>) {
    val viewTransform = AffineTransform(transform)

    val sign = if (xOff < 0) -1 else 1
    viewTransform.translate(magnitude * (depth - maxDepth / 2) * LAYER_SPACING * sign, 0.0)
    viewTransform.scale(sqrt(1.0 - magnitude * magnitude), 1.0)
    viewTransform.rotate(-angle)
    viewTransform.translate(-rootDimension.width / 2.0, -rootDimension.height / 2.0)

    val rect = viewTransform.createTransformedShape(Rectangle(view.x, view.y, view.width, view.height))
    val clip = parentClip.intersection(Rectangle(view.x, view.y, view.width, view.height))
    newHitRects.add(ViewDrawInfo(rect, viewTransform, view, clip))
    view.children.values.forEach { rebuildOneRect(transform, magnitude, depth + 1, angle, it, clip, newHitRects) }
  }

  fun resetRotation() {
    xOff = 0.0
    yOff = 0.0
    refresh()
  }
}