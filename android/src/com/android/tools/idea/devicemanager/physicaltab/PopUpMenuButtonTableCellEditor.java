/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.PopUpMenuValue;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.JBPopupMenu;
import java.util.function.Supplier;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.jetbrains.annotations.NotNull;

final class PopUpMenuButtonTableCellEditor extends IconButtonTableCellEditor {
  PopUpMenuButtonTableCellEditor(@NotNull Supplier<@NotNull Iterable<@NotNull JMenuItem>> newItems) {
    super(AllIcons.Actions.More, PopUpMenuValue.INSTANCE);

    myButton.addActionListener(event -> {
      JPopupMenu menu = new JBPopupMenu();

      newItems.get().forEach(menu::add);
      menu.show(myButton, 0, myButton.getHeight());
    });
  }
}