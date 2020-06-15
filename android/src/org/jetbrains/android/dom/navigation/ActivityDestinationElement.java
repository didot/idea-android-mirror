/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android.dom.navigation;

import static com.android.SdkConstants.CLASS_ACTIVITY;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.converters.PackageClassConverter;

public interface ActivityDestinationElement extends ConcreteDestinationElement {
  @Attribute("name")
  @Convert(PackageClassConverter.class)
  @PackageClassConverter.Options(inheriting = CLASS_ACTIVITY, includeDynamicFeatures = true)
  AndroidAttributeValue<PsiClass> getActivityName();
}
