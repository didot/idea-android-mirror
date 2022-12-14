/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.ddms;

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.utils.Pair;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import java.util.Comparator;
import java.util.Locale;
import javax.swing.JList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ClientCellRenderer extends ColoredListCellRenderer<Client> {
  @NotNull
  private final String myEmptyText;

  public ClientCellRenderer() {
    this("");
  }

  public ClientCellRenderer(@NotNull String emptyText) {
    myEmptyText = emptyText;
  }

  @VisibleForTesting
  static Pair<String, String> splitApplicationName(String name) {
    int index = name.lastIndexOf('.');
    return Pair.of(name.substring(0, index + 1), name.substring(index + 1));
  }

  private static void renderClient(@NotNull Client c, @NotNull ColoredTextContainer container) {
    ClientData cd = c.getClientData();
    String name = getClientName(cd);
    if (name == null) {
      return;
    }
    Pair<String, String> app = splitApplicationName(name);
    container.append(app.getFirst(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    container.append(app.getSecond(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

    if (cd.isValidUserId() && cd.getUserId() != 0) {
      container.append(String.format(Locale.US, " (user %1$d)", cd.getUserId()), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }

    container.append(String.format(Locale.US, " (%1$d)", cd.getPid()), SimpleTextAttributes.GRAY_ATTRIBUTES);

    if (!c.isValid()) {
      container.append(" [DEAD]", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    }
  }

  @Nullable
  static String getClientName(@NotNull ClientData cd) {
    return cd.getClientDescription();
  }

  @Override
  protected void customizeCellRenderer(@NotNull JList<? extends Client> list, Client value, int index, boolean selected, boolean focused) {
    if (value == null) {
      append(myEmptyText, SimpleTextAttributes.ERROR_ATTRIBUTES);
      return;
    }

    renderClient(value, this);
  }

  public static class ClientComparator implements Comparator<Client> {
    @Override
    public int compare(Client a, Client b) {
      ClientData ad = a.getClientData();
      ClientData bd = b.getClientData();

      String as = ad.getClientDescription() != null ? ad.getClientDescription() : String.valueOf(ad.getPid());
      String bs = bd.getClientDescription() != null ? bd.getClientDescription() : String.valueOf(bd.getPid());

      return as.compareTo(bs);
    }
  }
}
