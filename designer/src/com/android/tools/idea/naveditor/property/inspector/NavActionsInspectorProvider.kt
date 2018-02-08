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
package com.android.tools.idea.naveditor.property.inspector

import com.android.annotations.VisibleForTesting
import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.naveditor.property.NavActionsProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.scene.targets.ActionTarget
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.intellij.openapi.actionSystem.*
import com.intellij.ui.components.JBList
import icons.StudioIcons
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent

// Open for testing only
open class NavActionsInspectorProvider : NavListInspectorProvider<NavActionsProperty>(NavActionsProperty::class.java,
    StudioIcons.NavEditor.Properties.ACTION) {

  override fun doAddItem(existing: NlComponent?, parents: List<NlComponent>, resourceResolver: ResourceResolver?) {
    assert(parents.size == 1)
    showAndUpdateFromDialog(AddActionDialog(AddActionDialog.Defaults.NORMAL, existing, parents[0], resourceResolver))
  }

  @VisibleForTesting
  fun showAndUpdateFromDialog(actionDialog: AddActionDialog) {
    if (actionDialog.showAndGet()) {
      actionDialog.writeUpdatedAction()
    }
    inspector.refresh()
  }

  override fun getTitle(components: List<NlComponent>, surface: NavDesignSurface?) =
      if (components.size == 1 && components[0] == surface?.currentNavigation) {
        "Global Actions"
      }
      else {
        "Actions"
      }

  override fun createCustomInspector(components: List<NlComponent>,
                                     properties: Map<String, NlProperty>,
                                     propertiesManager: NavPropertiesManager): NavListInspectorComponent {
    val inspector: NavListInspectorComponent = super.createCustomInspector(components, properties, propertiesManager)
    val scene = propertiesManager.designSurface?.scene
    if (scene != null) {
      inspector.addAttachListener { list ->
        list.addListSelectionListener {
          updateSelection(scene, list)
        }
        list.addHierarchyListener {
          updateSelection(scene, list)
        }
      }
    }
    return inspector
  }

  private fun updateSelection(scene: Scene, list: JBList<NlProperty>) {
    val selected: Multimap<NlComponent, String> = HashMultimap.create()
    list.selectedValuesList
        .flatMap { it.components }
        .forEach { selected.put(it.parent, it.id) }
    for (component: SceneComponent in scene.sceneComponents) {
      component.targets
          .filterIsInstance(ActionTarget::class.java)
          .forEach {
            it.isHighlighted = selected.containsEntry(it.component.nlComponent, it.id)
          }
    }
    scene.needsRebuildList()
    scene.repaint()
  }

  override fun plusClicked(event: ActionEvent, parents: List<NlComponent>, resourceResolver: ResourceResolver?, surface: NavDesignSurface) {
    val actions: MutableList<AnAction> = getPopupActions(parents, resourceResolver, surface)

    val actionManager = ActionManager.getInstance()
    val popupMenu = actionManager.createActionPopupMenu("NavListInspector", DefaultActionGroup(actions))
    val mouseEvent: MouseEvent = event.source as? MouseEvent ?: return
    val invoker: Component = mouseEvent.source as? Component ?: return
    popupMenu.component.show(invoker, mouseEvent.x, mouseEvent.y)
  }

  @VisibleForTesting
  fun getPopupActions(parents: List<NlComponent>, resourceResolver: ResourceResolver?, surface: NavDesignSurface): MutableList<AnAction> {
    assert(parents.size == 1)
    val parent = parents[0]
    val actions: MutableList<AnAction> = mutableListOf(
        object : AnAction("Add Action...") {
          override fun actionPerformed(e: AnActionEvent?) {
            showAndUpdateFromDialog(AddActionDialog(AddActionDialog.Defaults.NORMAL, null, parent, resourceResolver))
          }
        },
        object : AnAction("Return to Source...") {
          override fun actionPerformed(e: AnActionEvent?) {
            showAndUpdateFromDialog(AddActionDialog(AddActionDialog.Defaults.RETURN_TO_SOURCE, null, parent, resourceResolver))
          }
        })
    if (parent != surface.currentNavigation) {
      actions.add(Separator.getInstance())
      actions.add(object : AnAction("Add Global...") {
        override fun actionPerformed(e: AnActionEvent?) {
          showAndUpdateFromDialog(AddActionDialog(AddActionDialog.Defaults.GLOBAL, null, parent, resourceResolver))
        }
      })
    }
    return actions
  }
}
