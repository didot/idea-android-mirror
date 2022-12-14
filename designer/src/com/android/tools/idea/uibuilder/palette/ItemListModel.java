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
package com.android.tools.idea.uibuilder.palette;

import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractListModel;
import javax.swing.ListModel;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link ListModel} for displaying palette items in a list.
 * Updates are being generated by a {@link DataModel}.
 */
public class ItemListModel extends AbstractListModel<Palette.Item> {
  private final List<Palette.Item> myItems;

  public ItemListModel() {
    myItems = new ArrayList<>();
  }

  public void update(@NotNull List<Palette.Item> items) {
    myItems.clear();

    myItems.addAll(items);
    fireContentsChanged(this, 0, myItems.size() - 1);
  }

  @Override
  public int getSize() {
    return myItems.size();
  }

  @Override
  public Palette.Item getElementAt(int index) {
    return myItems.get(index);
  }
}
