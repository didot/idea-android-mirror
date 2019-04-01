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
package org.jetbrains.android.dom.drawable;

import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.DefinesXml;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.android.dom.converters.PackageClassConverter;
import org.jetbrains.android.dom.resources.ResourceValue;

/**
 * Defines attributes of "drawable" xml element in a drawable resource.
 */
@DefinesXml
public interface CustomDrawable extends DrawableDomElement {
  @Required
  @Attribute("class")
  @Convert(PackageClassConverter.class)
  @PackageClassConverter.Options(inheriting = "android.graphics.drawable.Drawable")
  GenericAttributeValue<ResourceValue> getImplementingClass();
}
