/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.decorator

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.naveditor.model.ActionType
import com.android.tools.idea.naveditor.model.effectiveDestination
import com.android.tools.idea.naveditor.model.getActionType
import com.android.tools.idea.naveditor.model.getEffectiveSource
import com.android.tools.idea.naveditor.model.popUpTo
import com.android.tools.idea.naveditor.scene.NavColors.ACTION
import com.android.tools.idea.naveditor.scene.NavColors.HIGHLIGHTED_ACTION
import com.android.tools.idea.naveditor.scene.NavColors.SELECTED
import com.android.tools.idea.naveditor.scene.draw.DrawAction
import com.android.tools.idea.naveditor.scene.draw.DrawHorizontalAction
import com.android.tools.idea.naveditor.scene.draw.DrawSelfAction
import java.awt.Color

const val HIGHLIGHTED_CLIENT_PROPERTY = "actionHighlighted"

/**
 * [ActionDecorator] responsible for creating draw commands for actions.
 */
object ActionDecorator : NavBaseDecorator() {
  override fun addContent(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    val nlComponent = component.nlComponent
    val color = actionColor(component)
    val view = component.scene.designSurface.focusedSceneView ?: return
    val actionType = nlComponent.getActionType(component.scene.root?.nlComponent)
    val isPopAction = nlComponent.popUpTo != null
    val scale = sceneContext.scale.toFloat()
    when (actionType) {
      ActionType.NONE -> return
      ActionType.GLOBAL, ActionType.EXIT -> {
        @SwingCoordinate val drawRect = Coordinates.getSwingRectDip(view, component.fillDrawRect2D(0, null))
        list.add(DrawHorizontalAction(drawRect, scale, color, isPopAction))
      }
      else -> {
        val scene = component.scene

        val sourceNlComponent = scene.root?.nlComponent?.let { nlComponent.getEffectiveSource(it) } ?: return
        val sourceSceneComponent = scene.getSceneComponent(sourceNlComponent) ?: return
        val sourceRect = Coordinates.getSwingRectDip(view, sourceSceneComponent.fillDrawRect2D(0, null))

        if (actionType == ActionType.SELF) {
          list.add(DrawSelfAction(sourceRect, scale, color, isPopAction))
        }
        else {
          val targetNlComponent = nlComponent.effectiveDestination ?: return
          val destinationSceneComponent = scene.getSceneComponent(targetNlComponent) ?: return
          val destRect = Coordinates.getSwingRectDip(view, destinationSceneComponent.fillDrawRect2D(0, null))

          DrawAction.buildDisplayList(list, view, isPopAction, sourceRect, destRect, color)
        }
      }
    }
  }

  private fun actionColor(component: SceneComponent): Color {
    return when {
      component.isSelected || component.nlComponent.getClientProperty(HIGHLIGHTED_CLIENT_PROPERTY) == true -> SELECTED
      component.drawState == SceneComponent.DrawState.HOVER || component.targets.any { it.isMouseHovered } -> HIGHLIGHTED_ACTION
      else -> ACTION
    }
  }
}