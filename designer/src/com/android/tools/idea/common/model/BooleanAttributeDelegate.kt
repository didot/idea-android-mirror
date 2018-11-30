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
package com.android.tools.idea.common.model

import com.android.SdkConstants
import kotlin.reflect.KProperty

open class BooleanAttributeDelegate(private val namespace: String?, private val propertyName: String) {
  operator fun getValue(thisRef: NlComponent, property: KProperty<*>): Boolean? {
    return thisRef.resolveAttribute(namespace, propertyName)?.toBoolean()
  }

  operator fun setValue(thisRef: NlComponent, property: KProperty<*>, value: Boolean?) {
    thisRef.setAttribute(namespace, propertyName, value?.toString())
  }
}

class BooleanAutoAttributeDelegate(propertyName: String) : BooleanAttributeDelegate(SdkConstants.AUTO_URI, propertyName)