/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.model

import com.android.ide.common.rendering.api.ResourceReference
import java.awt.Shape

/**
 * A view node represents a composable in the view hierarchy as seen on the device.
 */
class ComposeViewNode(
  drawId: Long,
  qualifiedName: String,
  layout: ResourceReference?,
  x: Int,
  y: Int,
  width: Int,
  height: Int,
  transformedBounds: Shape?,
  viewId: ResourceReference?,
  textValue: String,
  layoutFlags: Int,
  var composeFilename: String,
  var composePackageHash: Int,
  var composeOffset: Int,
  var composeLineNumber: Int
): ViewNode(drawId, qualifiedName, layout, x, y, width, height, transformedBounds, viewId, textValue, layoutFlags)
