/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.ddms.actions;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DeviceContext;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractDeviceAction extends AnAction {
  @NotNull protected final DeviceContext myDeviceContext;

  public AbstractDeviceAction(@NotNull DeviceContext context, @Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);

    myDeviceContext = context;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled());
  }

  protected boolean isEnabled() {
    return myDeviceContext.getSelectedDevice() != null && myDeviceContext.getSelectedDevice().isOnline();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    IDevice device = myDeviceContext.getSelectedDevice();
    if (device != null) {
      performAction(e, device);
    }
  }

  protected abstract void performAction(@NotNull AnActionEvent e, @NotNull IDevice device);
}
