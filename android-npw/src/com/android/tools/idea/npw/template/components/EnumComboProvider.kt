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
package com.android.tools.idea.npw.template.components

import com.android.tools.idea.observable.AbstractProperty
import com.android.tools.idea.wizard.template.EnumParameter
import com.android.tools.idea.wizard.template.Parameter
import com.intellij.openapi.ui.ComboBox
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.DefaultComboBoxModel

/**
 * Provides a [ComboBox] well suited for handling [EnumParameter] parameters.
 */
class EnumComboProvider(parameter: EnumParameter<*>) : ParameterComponentProvider<ComboBox<String>>(parameter) {

  override fun createComponent(parameter: Parameter<*>): ComboBox<String> {
    val options = (parameter as EnumParameter<*>).options
    val comboBoxModel = DefaultComboBoxModel<String>()

    assert(options.isNotEmpty())
    options.forEach {
      comboBoxModel.addElement(it.name)
    }
    return ComboBox(comboBoxModel)
  }

  override fun createProperty(component: ComboBox<String>): AbstractProperty<*> = ApiComboBoxTextProperty(component)

  /**
   * Swing property which interacts with [ApiComboBoxItem]s.
   *
   * NOTE: This is currently only needed here but, we can promote it to ui.wizard.properties if it's ever needed in more places.
   */
  private class ApiComboBoxTextProperty(private val comboBox: ComboBox<String>) : AbstractProperty<String>(), ActionListener {
    init {
      comboBox.addActionListener(this)
    }

    override fun setDirectly(value: String) {
      val model = comboBox.model

      for (i in 0 until model.size) {
        val item = model.getElementAt(i)
        if (value == item) {
          comboBox.selectedIndex = i
          return
        }
      }

      comboBox.selectedIndex = -1
    }

    override fun get(): String {
      return comboBox.selectedItem as? String ?: return ""
    }

    override fun actionPerformed(e: ActionEvent) {
      notifyInvalidated()
    }
  }
}
