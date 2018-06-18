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
package com.android.tools.idea.uibuilder.property2

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property2.api.NewPropertyItem
import com.android.tools.idea.common.property2.api.PropertiesTable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.psi.xml.XmlTag
import icons.StudioIcons
import javax.swing.Icon

/**
 * A [NelePropertyItem] where it is possible to edit the name of the property.
 *
 * The property is initially created with an empty name and an unknown type.
 * When a name is specified, it is matched against all known attributes. If
 * found this property item will act as a delegate to the matched property.
 */
class NeleNewPropertyItem(model: NelePropertiesModel,
                          var properties: PropertiesTable<NelePropertyItem>)
  : NelePropertyItem("", "", NelePropertyType.UNKNOWN, null, "", model, listOf()), NewPropertyItem {

  override var namespace: String = ""
    private set

  override var name: String = ""
    set(value) {
      val (propertyNamespace, propertyName) = parseName(value)
      namespace = propertyNamespace
      field = propertyName
      delegate = findDelegate()
    }

  /**
   * When the property name is set to something valid, the [delegate] will be not null.
   * All remaining properties and functions should delegate to this [delegate] if present.
   */
  var delegate: NelePropertyItem? = null
    private set

  override var value: String?
    get() = delegate?.value
    set(value) {
      delegate?.value = value
      if (value?.isNotEmpty() == true) {
        // An attribute value was specified.
        // The change was committed which eventually will cause the table to update
        // with the new value in the main part of the table.
        // Make this new property item ready for adding another attribute.
        name = ""
      }
    }

  override val resolvedValue: String?
    get() = delegate?.resolvedValue

  override val rawValue: String?
    get() = delegate?.rawValue

  override val isReference: Boolean
    get() = delegate?.isReference == true

  override val tooltipForName: String
    get() = delegate?.tooltipForName ?: ""

  override val tooltipForValue: String
    get() = delegate?.tooltipForValue ?: ""

  override fun validate(editedValue: String): String =
    delegate?.validate(editedValue) ?: ""

  override val showActionButton: Boolean
    get() = delegate?.showActionButton == true

  override val actionButtonFocusable: Boolean
    get() = delegate?.actionButtonFocusable == true

  override fun getActionIcon(focused: Boolean): Icon =
    delegate?.getActionIcon(focused) ?: StudioIcons.Common.ADD

  override fun getAction(): AnAction? =
    delegate?.getAction()

  private fun parseName(value: String): Pair<String, String> {
    val prefixIndex = value.indexOf(":")
    if (prefixIndex < 0) {
      return Pair(ANDROID_URI, value)
    }
    val prefix = value.substring(0, prefixIndex)
    val name = value.substring(prefixIndex + 1)

    val namespace = findNamespaceDeclarations()[prefix] ?: ANDROID_URI
    return Pair(namespace, name)
  }

  private fun findNamespaceDeclarations(): Map<String, String> {
    val component = properties.first?.components?.firstOrNull() ?: return emptyMap()
    return findRootTag(component).localNamespaceDeclarations
  }

  private fun findRootTag(component: NlComponent): XmlTag {
    var tag = component.tag
    var parent = tag.parent
    while (parent is XmlTag) {
      tag = parent
      parent = tag.parent
    }
    return tag
  }

  private fun findDelegate(): NelePropertyItem? {
    var property = properties.getOrNull(namespace, name)
    if (property != null) {
      return property
    }
    if (namespace == TOOLS_URI) {
      for (ns in properties.namespaces) {
        property = properties.getOrNull(ns, name)
        if (property != null) {
          property.designProperty
        }
      }
    }
    return null
  }
}
