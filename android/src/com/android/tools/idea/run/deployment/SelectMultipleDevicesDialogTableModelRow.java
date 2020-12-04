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
package com.android.tools.idea.run.deployment;

import java.util.Optional;
import org.jetbrains.annotations.NotNull;

final class SelectMultipleDevicesDialogTableModelRow {
  private boolean mySelected;
  private final @NotNull Device myDevice;

  SelectMultipleDevicesDialogTableModelRow(@NotNull Device device) {
    myDevice = device;
  }

  boolean isSelected() {
    return mySelected;
  }

  void setSelected(boolean selected) {
    mySelected = selected;
  }

  @NotNull Device getDevice() {
    return myDevice;
  }

  @NotNull String getDeviceCellText() {
    return Optional.ofNullable(myDevice.getValidityReason())
      .map(reason -> "<html>" + myDevice + "<br>" + reason)
      .orElse(myDevice.getName());
  }
}
