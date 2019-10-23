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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.api.DragType
import com.android.tools.idea.common.model.DnDTransferItem
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.model.NlDropEvent
import com.android.tools.idea.uibuilder.surface.DragDropInteraction
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTargetDragEvent

interface InteractionProvider {
  fun createInteractionOnClick(@SwingCoordinate mouseX: Int, @SwingCoordinate mouseY: Int): Interaction?

  fun createInteractionOnDrag(draggedSceneComponent: SceneComponent, primarySceneComponent: SceneComponent?): Interaction?

  fun createInteractionOnDragEnter(dragEvent: DropTargetDragEvent): Interaction?
}

abstract class InteractionProviderBase(private val surface: DesignSurface) : InteractionProvider {
  override fun createInteractionOnDragEnter(dragEvent: DropTargetDragEvent): Interaction? {
    val event = NlDropEvent(dragEvent)
    val location = dragEvent.location
    val mouseX = location.x
    val mouseY = location.y
    val sceneView = surface.getSceneView(mouseX, mouseY)
    if (sceneView == null) {
      event.reject()
      return null
    }

    val model = sceneView.model
    val item = DnDTransferItem.getTransferItem(event.getTransferable(), true /* allow placeholders */)
    if (item == null) {
      event.reject()
      return null
    }
    val dragType = if (event.dropAction == DnDConstants.ACTION_COPY) DragType.COPY else DragType.MOVE
    val insertType = model.determineInsertType(dragType, item, true /* preview */)

    val dragged: List<NlComponent>
    if (StudioFlags.NELE_DRAG_PLACEHOLDER.get() && !item.isFromPalette) {
      // When dragging from ComponentTree, it should reuse the existing NlComponents rather than creating the new ones.
      // This impacts some Handlers, using StudioFlag to protect for now.
      // Most of Handlers should be removed once this flag is removed.
      dragged = ArrayList<NlComponent>(surface.selectionModel.selection)
    }
    else {
      if (item.isFromPalette) {
        // remove selection when dragging from Palette.
        surface.selectionModel.clear()
      }
      dragged = model.createComponents(item, insertType, surface)
    }

    if (dragged.isEmpty()) {
      event.reject()
      return null
    }

    val interaction = DragDropInteraction(surface, dragged)
    interaction.setType(dragType)
    interaction.setTransferItem(item)
    // This determines the icon presented to the user while dragging.
    // If we are dragging a component from the palette then use the icon for a copy, otherwise show the icon
    // that reflects the users choice i.e. controlled by the modifier key.
    event.accept(insertType)
    return interaction
  }
}
