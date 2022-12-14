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
package com.android.tools.idea.uibuilder.property.inspector

import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.property.panel.api.PropertiesTable

fun getTagName(properties: PropertiesTable<NlPropertyItem>): String? {
  val property = properties.first ?: return null
  val tagName = property.components.firstOrNull()?.tagName ?: return null
  return if (property.components.any { it.tagName == tagName }) tagName else null
}
