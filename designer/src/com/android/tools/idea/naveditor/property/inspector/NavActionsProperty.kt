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

import com.android.SdkConstants
import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlComponent.stripId
import com.android.tools.idea.common.property.NlProperty
import org.jetbrains.android.dom.navigation.NavigationSchema

/**
 * Property representing all the actions (possibly zero) for a destinations.
 */
class NavActionsProperty(components: List<NlComponent>) : NlProperty by PropertyAdapter("Actions", components) {

  val actions: MutableMap<String, NavActionPropertyItem> = mutableMapOf()

  init {
    refreshActionList()
  }

  fun refreshActionList() {
    actions.clear()

    for (component in components) {
      for (child in component.children ?: listOf()) {
        if (child.tagName == NavigationSchema.TAG_ACTION) {
          child.resolveAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION)?.let {
            actions.put(it, NavActionPropertyItem(stripId(it) ?: it, listOf(child)))
          }
        }
      }
    }
  }

  override fun getChildProperty(name: String) = actions[name]!!

  /**
   * Property representing a single action
   */
  class NavActionPropertyItem(name: String, components : List<NlComponent>) : NlProperty by PropertyAdapter(name, components) {
    // Nothing?
  }

  /**
   * Dummy property with simple implementations for some methods.
   */
  private class PropertyAdapter(val myName: String, val myComponents: List<NlComponent>) : NlProperty {
    override fun getNamespace() = null

    override fun getResolvedValue() = null

    override fun isDefaultValue(value: String?) = true

    override fun getName() = myName

    override fun getValue() = null

    override fun setValue(value: Any?) {}

    override fun resolveValue(value: String?) = null

    override fun getTooltipText() = ""

    override fun getDefinition() = null

    override fun getComponents() = myComponents

    override fun getResolver(): ResourceResolver? = model.configuration.resourceResolver

    override fun getModel() = myComponents[0].model

    override fun getTag() = if (myComponents.size > 1) null else myComponents[0].tag

    override fun getTagName() = tag?.localName

    override fun getChildProperty(name: String) = throw IllegalStateException()

    override fun getDesignTimeProperty() = throw IllegalStateException()
  }
}