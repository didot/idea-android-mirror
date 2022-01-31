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
package com.android.tools.idea.npw.module.recipes.androidModule.res.values_v29

fun androidModuleThemesMaterial3V29(themeName: String) =
  // When the contents are modified, need to modify
  // com.android.tools.idea.wizard.template.impl.activities.common.generateMaterial3Themes
  """<resources xmlns:tools="http://schemas.android.com/tools">
  <style name="$themeName" parent="Base.${themeName}">
    <!-- Transparent system bars for edge-to-edge. -->
    <item name="android:navigationBarColor">@android:color/transparent</item>
    <item name="android:statusBarColor">@android:color/transparent</item>
    <item name="android:windowLightStatusBar">?attr/isLightTheme</item>
  </style>
</resources>"""