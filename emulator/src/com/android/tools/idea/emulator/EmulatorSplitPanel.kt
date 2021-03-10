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
package com.android.tools.idea.emulator

import com.android.tools.adtui.common.primaryPanelBackground
import com.intellij.ui.OnePixelSplitter
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A panel containing two subpanels separated by a [OnePixelSplitter].
 */
class EmulatorSplitPanel(layoutNode: SplitNode) : JPanel(BorderLayout()) {
  private val splitter = OnePixelSplitter(layoutNode.splitType == SplitType.VERTICAL, layoutNode.splitRatio.toFloat())

  val isVerticalSplit
    get() = splitter.orientation
  var firstComponent: JComponent
    get() = splitter.firstComponent
    set(value) { splitter.firstComponent = value }
  var secondComponent: JComponent
    get() = splitter.secondComponent
    set(value) { splitter.secondComponent = value }

  init {
    background = primaryPanelBackground
  }
}