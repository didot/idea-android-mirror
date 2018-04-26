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
package com.android.tools.idea.common.property2.impl.support

import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.common.property2.impl.model.*
import com.android.tools.idea.common.property2.impl.ui.*
import javax.swing.JComponent

/**
 * A standard provider for a property editor.
 *
 * For a given property this class will provide a model and a UI for an editor of that property.
 * This implementation is computing the [ControlType] using the specified providers.
 * @param P a client defined property class that must implement the interface: [PropertyItem]
 * @property enumSupportProvider provides an [EnumSupport] for a property or null if there isn't any
 * @property controlTypeProvider provides the [ControlType] given a property and its [EnumSupport]
 */
class EditorProviderImpl<in P : PropertyItem>(
  private val enumSupportProvider: EnumSupportProvider<P>,
  private val controlTypeProvider: ControlTypeProvider<P>
) : EditorProvider<P> {

  override fun invoke(property: P): Pair<PropertyEditorModel, JComponent> {
    val enumSupport = enumSupportProvider(property)
    val controlType = controlTypeProvider(property, enumSupport)

    when (controlType) {
      ControlType.COMBO_BOX ->
        return createComboBoxEditor(property, true, enumSupport!!)

      ControlType.DROPDOWN ->
        return createComboBoxEditor(property, false, enumSupport!!)

      ControlType.TEXT_EDITOR -> {
        val model = TextFieldPropertyEditorModel(property, true)
        val editor = PropertyTextField(model)
        return Pair(model, addActionButtonBinding(model, editor))
      }

      ControlType.THREE_STATE_BOOLEAN -> {
        val model = ThreeStateBooleanPropertyEditorModel(property)
        val editor = PropertyThreeStateCheckBox(model)
        return Pair(model, editor)
      }

      ControlType.FLAG_EDITOR -> {
        val model = FlagPropertyEditorModel(property as FlagsPropertyItem<*>)
        val editor = FlagPropertyEditor(model)
        return Pair(model, editor)
      }
    }
  }

  private fun createComboBoxEditor(property: P, editable: Boolean, enumSupport: EnumSupport): Pair<PropertyEditorModel, JComponent> {
    val model = ComboBoxPropertyEditorModel(property, enumSupport, editable)
    val comboBox = PropertyComboBox(model)
    comboBox.renderer = enumSupport.renderer
    return Pair(model, addActionButtonBinding(model, comboBox))
  }

  private fun addActionButtonBinding(model: PropertyEditorModel, editor: JComponent): JComponent {
    if ((model.property as? ActionButtonSupport)?.showActionButton == true) {
      return ActionButtonBinding(model, editor)
    }
    return editor
  }
}
