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
package com.android.tools.adtui.stdui

import com.intellij.util.ui.JBUI

/**
 * Standard UI Component Dimensions.
 */
object StandardDimensions {
  val VERTICAL_PADDING = 1
  val HORIZONTAL_PADDING = 6
  val INNER_BORDER_WIDTH = JBUI.scale(1f)
  val OUTER_BORDER_WIDTH = JBUI.scale(2f)
  val TEXT_FIELD_CORNER_RADIUS = JBUI.scale(1f)
  val DROPDOWN_CORNER_RADIUS = JBUI.scale(4f)
  val DROPDOWN_BUTTON_WIDTH = JBUI.scale(18f)
  val DROPDOWN_ARROW_WIDTH = JBUI.scale(8f)
  val DROPDOWN_ARROW_HEIGHT = JBUI.scale(5f)
}
