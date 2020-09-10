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
package com.android.tools.idea.ui.designer.overlays;

import com.intellij.ide.util.ChooseElementsDialog;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Dialog for choosing overlays
 */
public class ChooseOverlayDialog extends ChooseElementsDialog<OverlayData> {
  /**
   * Creates a dialog populated with the current overlays
   */
  ChooseOverlayDialog(List<? extends OverlayData> items, String title, String description) {
    super(null, items, title, description);
  }

  /**
   * Returns the name of the overlay as the item text
   * @param data
   */
  @Override
  protected String getItemText(@NotNull OverlayData data) {
    return data.getOverlayName();
  }

  /**
   * Returns the plugin icon of the {@link OverlayProvider}
   */
  @Override
  @Nullable
  protected Icon getItemIcon(@NotNull OverlayData data) {
    return data.getOverlayEntry().getOverlayProvider().getPluginIcon();
  }
}
