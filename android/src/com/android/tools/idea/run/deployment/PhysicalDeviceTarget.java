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

import com.intellij.openapi.project.Project;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDeviceTarget extends Target {
  PhysicalDeviceTarget(@NotNull SerialNumber deviceKey) {
    super(deviceKey);
  }

  /**
   * @return Optional.empty. Physical devices never get submenus and as there's only one type of target for them, there's no need for
   * distinguishing text.
   */
  @Override
  @NotNull Optional<@NotNull String> getText(@NotNull Device device) {
    return Optional.empty();
  }

  @Override
  void boot(@NotNull VirtualDevice device, @NotNull Project project) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int hashCode() {
    return getDeviceKey().hashCode();
  }

  @Override
  public boolean equals(@Nullable Object object) {
    return object instanceof PhysicalDeviceTarget && getDeviceKey().equals(((Target)object).getDeviceKey());
  }
}
