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
import com.android.tools.idea.layoutinspector.model.InspectorView
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

class DeviceViewPanelModel(val model: InspectorModel) {
  @VisibleForTesting
  var xOff = 0.0
  @VisibleForTesting
  var yOff = 0.0

  var rootDimension: Dimension = Dimension()

  @VisibleForTesting
  val hitRects = mutableListOf<Triple<Shape, AffineTransform, InspectorView>>()

  fun findTopRect(x: Double, y: Double): InspectorView? {
    return hitRects.findLast {
      it.first.contains(x, y)
    }?.third
  }

  fun rotateX(increment: Double) {
    xOff = (xOff + increment).coerceIn(-1.0, 1.0)
  }

  fun rotateY(increment: Double) {
    yOff = (yOff + increment).coerceIn(-1.0, 1.0)
  }

  @VisibleForTesting
  fun refresh() {
    rootDimension = Dimension(model.root.width, model.root.height)
    hitRects.clear()
    val transform = AffineTransform()
    transform.translate(-model.root.width / 2.0, -model.root.height / 2.0)

    val magnitude = min(1.0, hypot(xOff, yOff))
    val angle = if (abs(xOff) < 0.00001) PI / 2.0 else atan(yOff / xOff)

    transform.translate(rootDimension.width / 2.0, rootDimension.height / 2.0)
    transform.rotate(angle)
    val maxDepth = findMaxDepth(model.root)
    rebuildOneRect(transform, magnitude, 0, maxDepth, angle, model.root)
  }

  private fun findMaxDepth(view: InspectorView): Int {
    return 1 + (view.children.map { findMaxDepth(it) }.max() ?: 0)
  }

  private fun rebuildOneRect(transform: AffineTransform,
                             magnitude: Double,
                             depth: Int,
                             maxDepth: Int,
                             angle: Double,
                             view: InspectorView) {
    val viewTransform = AffineTransform(transform)

    val sign = if (xOff < 0) -1 else 1
    viewTransform.translate(magnitude * (depth - maxDepth / 2) * LAYER_SPACING * sign, 0.0)
    viewTransform.scale(sqrt(1.0 - magnitude * magnitude), 1.0)
    viewTransform.rotate(-angle)
    viewTransform.translate(-rootDimension.width / 2.0, -rootDimension.height / 2.0)

    val rect = viewTransform.createTransformedShape(Rectangle(view.x, view.y, view.width, view.height))
    hitRects.add(Triple(rect, viewTransform, view))
    view.children.forEach { rebuildOneRect(transform, magnitude, depth + 1, maxDepth, angle, it) }
  }

  fun resetRotation() {
    xOff = 0.0
    yOff = 0.0
  }

}