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
package com.android.tools.idea.run.deployment;

import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Devices {
  private Devices() {
  }

  static boolean containsAnotherDeviceWithSameName(@NotNull Collection<Device> devices, @NotNull Device device) {
    Object key = device.getKey();
    Object name = device.getName();

    return devices.stream()
      .filter(d -> !d.getKey().equals(key))
      .map(Device::getName)
      .anyMatch(name::equals);
  }

  static @NotNull String getText(@NotNull Device device) {
    return getText(device, null);
  }

  static @NotNull String getText(@NotNull Device device, @Nullable Key key) {
    return getText(device, key, null);
  }

  static @NotNull String getText(@NotNull Device device, @Nullable Key key, @Nullable Target target) {
    String bootOption = target == null ? null : target.getText(device).orElse(null);
    return getText(device.getName(), key == null ? null : key.toString(), bootOption, device.getValidityReason());
  }

  private static @NotNull String getText(@NotNull String device,
                                         @Nullable String key,
                                         @Nullable String bootOption,
                                         @Nullable String reason) {
    StringBuilder builder = new StringBuilder(device);

    if (key != null) {
      builder
        .append(" [")
        .append(key)
        .append(']');
    }

    if (bootOption != null) {
      builder
        .append(" - ")
        .append(bootOption);
    }

    if (reason != null) {
      builder
        .append(" (")
        .append(reason)
        .append(')');
    }

    return builder.toString();
  }
}
