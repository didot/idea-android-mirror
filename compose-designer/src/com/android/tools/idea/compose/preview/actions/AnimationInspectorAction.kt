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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.common.actions.ActionButtonWithToolTipDescription
import com.android.tools.idea.compose.ComposeExperimentalConfiguration
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_ELEMENT
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.PreviewElementInstance
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.ui.AnActionButton
import icons.StudioIcons.Compose.Toolbar.ANIMATION_INSPECTOR
import javax.swing.JComponent

/**
 * Action to open the Compose Animation Preview to analyze animations of a Compose Preview in details.
 *
 * @param isAvailable returns whether the action is available given a [DataContext]. Actions that are not available must be disabled.
 * @param dataContextProvider returns the [DataContext] containing the Compose Preview associated information.
 */
internal class AnimationInspectorAction(private val isAvailable: (DataContext) -> Boolean = { true },
                                        private val dataContextProvider: () -> DataContext) : AnActionButton(
  message("action.animation.inspector.title"), message("action.animation.inspector.description"),
  ANIMATION_INSPECTOR), CustomComponentAction {

  private fun getPreviewElement() = dataContextProvider().getData(COMPOSE_PREVIEW_ELEMENT) as? PreviewElementInstance

  override fun updateButton(e: AnActionEvent) {
    super.updateButton(e)
    e.presentation.apply {
      isEnabled = isAvailable(e.dataContext)
      // Only display the animation inspector icon if there are animations to be inspected.
      isVisible = ComposeExperimentalConfiguration.getInstance().isAnimationPreviewEnabled && getPreviewElement()?.hasAnimations == true
      description =
        if (isEnabled) message("action.animation.inspector.description") else message("action.animation.inspector.unavailable.title")
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    dataContextProvider().getData(COMPOSE_PREVIEW_MANAGER)?.let { it.animationInspectionPreviewElementInstance = getPreviewElement() }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return ActionButtonWithToolTipDescription(this, presentation, place)
  }
}