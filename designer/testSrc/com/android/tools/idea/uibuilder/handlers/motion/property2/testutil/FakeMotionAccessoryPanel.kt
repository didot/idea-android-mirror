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
package com.android.tools.idea.uibuilder.handlers.motion.property2.testutil

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface
import com.android.tools.idea.uibuilder.api.AccessorySelectionListener
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionDesignSurfaceEdits
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector
import com.android.tools.idea.uibuilder.handlers.motion.property2.MotionSelection
import com.android.tools.idea.uibuilder.surface.AccessoryPanel
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import javax.swing.JPanel

class FakeMotionAccessoryPanel: AccessoryPanelInterface, MotionDesignSurfaceEdits {
  private val listeners = mutableListOf<AccessorySelectionListener>()
  private var type: MotionEditorSelector.Type? = null
  private var tags: Array<MTag>? = null

  override fun getPanel(): JPanel {
    throw Error("should not be called")
  }

  override fun createPanel(type: AccessoryPanel.Type?): JPanel {
    throw Error("should not be called")
  }

  override fun updateAccessoryPanelWithSelection(type: AccessoryPanel.Type, selection: MutableList<NlComponent>) {
    throw Error("should not be called")
  }

  override fun deactivate() {
    throw Error("should not be called")
  }

  override fun updateAfterModelDerivedDataChanged() {
    throw Error("should not be called")
  }

  override fun handlesWriteForComponent(id: String?): Boolean {
    throw Error("should not be called")
  }

  override fun getSelectedConstraint(): SmartPsiElementPointer<XmlTag> {
    throw Error("should not be called")
  }

  override fun getSelectedConstraintSet(): String {
    throw Error("should not be called")
  }

  override fun getTransitionFile(component: NlComponent?): XmlFile {
    throw Error("should not be called")
  }

  override fun getConstraintSet(file: XmlFile?, s: String?): XmlTag {
    throw Error("should not be called")
  }

  override fun getConstrainView(set: XmlTag?, id: String?): XmlTag {
    throw Error("should not be called")
  }

  override fun getKeyframes(file: XmlFile?, id: String?): MutableList<XmlTag> {
    throw Error("should not be called")
  }

  fun select(selection: MotionSelection) {
    type = selection.type
    tags = selection.tags
    listeners.forEach { it.selectionChanged(this, selection.components) }
  }

  override fun getSelectedAccessoryType(): Any? {
    return type
  }

  override fun getSelectedAccessory(): Any? {
    return tags
  }

  val listenerCount: Int
    get() = listeners.size

  override fun addListener(listener: AccessorySelectionListener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: AccessorySelectionListener) {
    listeners.remove(listener)
  }
}
