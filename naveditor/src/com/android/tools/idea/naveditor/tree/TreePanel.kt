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
package com.android.tools.idea.naveditor.tree

import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.ComponentTreeModel
import com.android.tools.componenttree.api.ComponentTreeSelectionModel
import com.android.tools.componenttree.api.ViewNodeType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.SelectionListener
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.naveditor.model.isAction
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.model.isNavigation
import com.android.tools.idea.naveditor.model.uiName
import com.intellij.openapi.application.ApplicationManager
import icons.StudioIcons
import javax.swing.Icon
import javax.swing.JComponent


class TreePanel : ToolContent<DesignSurface> {
  private var designSurface: DesignSurface? = null
  private val componentTree: JComponent
  private val componentTreeModel: ComponentTreeModel
  private val componentTreeSelectionModel: ComponentTreeSelectionModel
  private val contextSelectionListener = SelectionListener { _, _ -> contextSelectionChanged() }

  init {
    val builder = ComponentTreeBuilder()
      .withNodeType(NlComponentNodeType())
      .withInvokeLaterOption { ApplicationManager.getApplication().invokeLater(it) }

    val (tree, model, selectionModel) = builder.build()
    componentTree = tree
    componentTreeModel = model
    componentTreeSelectionModel = selectionModel
    selectionModel.addSelectionListener {
      designSurface?.let {
        val list = selectionModel.selection.filterIsInstance<NlComponent>()
        it.selectionModel.setSelection(list)
        it.scrollToCenter(list.filter { c -> c.isDestination && !c.isNavigation })
      }
    }
  }

  override fun setToolContext(toolContext: DesignSurface?) {
    designSurface?.selectionModel?.removeListener(contextSelectionListener)
    designSurface = toolContext
    designSurface?.selectionModel?.addListener(contextSelectionListener)
    componentTreeModel.treeRoot = designSurface?.sceneManager?.scene?.root?.nlComponent
  }

  private fun contextSelectionChanged() {
    componentTreeSelectionModel.selection = designSurface?.selectionModel?.selection ?: emptyList()
  }

  override fun getComponent() = componentTree

  override fun dispose() {
    setToolContext(null)
  }

  private class NlComponentNodeType : ViewNodeType<NlComponent>() {
    override val clazz = NlComponent::class.java

    override fun tagNameOf(node: NlComponent) = node.tagName

    override fun idOf(node: NlComponent) = node.id

    override fun textValueOf(node: NlComponent) = node.uiName

    override fun iconOf(node: NlComponent): Icon = node.mixin?.icon ?: StudioIcons.LayoutEditor.Palette.UNKNOWN_VIEW

    override fun isEnabled(node: NlComponent) = true

    override fun parentOf(node: NlComponent) = node.parent

    override fun childrenOf(node: NlComponent) = node.children.filter { it.isDestination || it.isAction }
  }
}