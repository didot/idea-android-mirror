/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;
import java.awt.Component;
import java.awt.EventQueue;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

final class DeleteItem extends JBMenuItem {
  DeleteItem(@NotNull VirtualDevicePopUpMenuButtonTableCellEditor editor) {
    this(editor,
         DeleteItem::showCannotDeleteRunningAvdDialog,
         DeleteItem::showConfirmDeleteDialog,
         AvdManagerConnection::getDefaultAvdManagerConnection);
  }

  @VisibleForTesting
  DeleteItem(@NotNull VirtualDevicePopUpMenuButtonTableCellEditor editor,
             @NotNull Consumer<@NotNull Component> showCannotDeleteRunningAvdDialog,
             @NotNull BiPredicate<@NotNull Object, @NotNull Component> showConfirmDeleteDialog,
             @NotNull Supplier<@NotNull AvdManagerConnection> getDefaultAvdManagerConnection) {
    super("Delete");
    setToolTipText("Delete this AVD");

    addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(EventKind.VIRTUAL_DELETE_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);

      VirtualDevice device = editor.getDevice();
      VirtualDevicePanel devicePanel = editor.getPanel();
      VirtualDeviceTable table = devicePanel.getTable();

      if (device.isOnline()) {
        showCannotDeleteRunningAvdDialog.accept(table);
        return;
      }

      if (!showConfirmDeleteDialog.test(device, table)) {
        return;
      }

      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        getDefaultAvdManagerConnection.get().deleteAvd(device.getAvdInfo());
        UIUtil.invokeLaterIfNeeded(() -> {
          if (!devicePanel.isDisposed()) {
            table.refreshAvds();
          }
        });
      });
    });
  }

  private static void showCannotDeleteRunningAvdDialog(@NotNull Component component) {
    Messages.showErrorDialog(component,
                             "The selected AVD is currently running in the emulator. Please exit the emulator instance and try " +
                             "deleting again.",
                             "Cannot Delete a Running AVD");
  }

  private static boolean showConfirmDeleteDialog(@NotNull Object device, @NotNull Component component) {
    return MessageDialogBuilder.yesNo("Confirm Deletion", "Do you really want to delete " + device + "?").ask(component);
  }
}
