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
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.inspector.InspectorPanel
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.google.common.collect.Table
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.util.IncorrectOperationException
import org.jetbrains.android.dom.navigation.NavigationSchema

/**
 * Panel shown in the nav editor properties inspector. Notably includes actions, deeplinks, and arguments.
 */
class NavInspectorPanel(parentDisposable: Disposable) : InspectorPanel<NavPropertiesManager>(parentDisposable, null) {

  var myAddActionDialogFactory: () -> AddActionDialog = { throw IncorrectOperationException() }

  override fun collectExtraProperties(components: List<NlComponent>,
                                      propertiesManager: NavPropertiesManager,
                                      propertiesByName: MutableMap<String, NlProperty>) {
    for (component in components) {
      for (child in component.getChildren()) {
        when (child.tagName) {
          NavigationSchema.TAG_ACTION -> child.resolveAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION)?.let {
            propertiesByName.put(it, NavActionPropertyItem(it, child))
          }
          NavigationSchema.TAG_DEEPLINK -> {
            // TODO
          }
          NavigationSchema.TAG_ARGUMENT -> {
            // TODO
          }
        }
      }
    }
  }

  override fun setComponent(components: List<NlComponent>, properties: Table<String, String, out NlProperty>, propertiesManager: NavPropertiesManager) {
    myAddActionDialogFactory = { AddActionDialog(components, propertiesManager.designSurface?.configuration?.resourceResolver) }
    super.setComponent(components, properties, propertiesManager)
  }

  fun addAction() {
    val addActionDialog = myAddActionDialogFactory()
    if (addActionDialog.showAndGet()) {
      WriteCommandAction.runWriteCommandAction(null, {
        val source = addActionDialog.source
        val tag = source.tag.createChildTag(NavigationSchema.TAG_ACTION, null, null, false)
        val newComponent = source.model.createComponent(tag, source, null)
        newComponent.ensureId()
        newComponent.setAttribute(
            SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, SdkConstants.ID_PREFIX + addActionDialog.destination.id!!)
      })
    }
  }
}
