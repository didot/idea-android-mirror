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
package com.android.tools.idea.gradle.ui;

import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;

import com.intellij.ui.ComboboxWithBrowseButton;
import java.io.File;
import org.jetbrains.annotations.NotNull;

public class SdkUiUtils {
  @NotNull
  public static File getLocationFromComboBoxWithBrowseButton(@NotNull ComboboxWithBrowseButton comboboxWithBrowseButton) {
    Object item = comboboxWithBrowseButton.getComboBox().getEditor().getItem();
    if (item instanceof LabelAndFileForLocation) {
      return ((LabelAndFileForLocation)item).getFile();
    }
    String jdkLocation = item.toString();
    return toSystemDependentPath(jdkLocation);
  }
}
