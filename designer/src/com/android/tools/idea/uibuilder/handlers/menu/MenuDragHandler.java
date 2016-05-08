/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.menu;

import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.handlers.Utils;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

final class MenuDragHandler extends DragHandler {
  private final NlComponent myMenu;
  private final List<NlComponent> myItems;

  private NlComponent myActiveItem;

  MenuDragHandler(@NotNull ViewEditor editor,
                  @NotNull ViewGroupHandler handler,
                  @NotNull NlComponent menu,
                  @NotNull List<NlComponent> items,
                  @NotNull DragType type) {
    super(editor, handler, menu, items, type);
    myMenu = menu;

    myItems = menu.getChildren().stream()
      .filter(item -> item.h != -1)
      .collect(Collectors.toList());
  }

  @Override
  public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
  }

  @Nullable
  @Override
  public String update(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
    String message = super.update(x, y, modifiers);

    if (message != null) {
      myActiveItem = null;
      return message;
    }

    int count = myItems.size();

    if (count == 0) {
      myActiveItem = null;
      return null;
    }

    NlComponent firstItem = myItems.get(0);

    if (y < firstItem.y) {
      myActiveItem = firstItem;
      return null;
    }

    for (NlComponent item : myItems) {
      if (Utils.containsY(item, y)) {
        myActiveItem = item;
        return null;
      }
    }

    myActiveItem = myItems.get(count - 1);
    return null;
  }

  @Override
  public void paint(@NotNull NlGraphics graphics) {
    if (!myItems.isEmpty()) {
      drawDropPreviewLine(graphics);
      drawDropRecipientLines(graphics);
      drawDropZoneLines(graphics);
    }
  }

  private void drawDropPreviewLine(@NotNull NlGraphics graphics) {
    graphics.useStyle(NlDrawingStyle.DROP_PREVIEW);

    if (myActiveItem == null) {
      graphics.drawBottom(myMenu);
    }
    else if (lastY < Utils.getMidpointY(myActiveItem)) {
      graphics.drawTop(myActiveItem);
    }
    else {
      graphics.drawBottom(myActiveItem);
    }
  }

  private void drawDropRecipientLines(@NotNull NlGraphics graphics) {
    graphics.useStyle(NlDrawingStyle.DROP_RECIPIENT);

    if (lastY >= Utils.getMidpointY(myItems.get(0))) {
      graphics.drawTop(myMenu);
    }

    graphics.drawLeft(myMenu);
    graphics.drawRight(myMenu);

    if (lastY < Utils.getMidpointY(myItems.get(myItems.size() - 1))) {
      graphics.drawBottom(myMenu);
    }
  }

  private void drawDropZoneLines(@NotNull NlGraphics graphics) {
    int midpoint = Utils.getMidpointY(myActiveItem);
    graphics.useStyle(NlDrawingStyle.DROP_ZONE);

    for (int i = 1, count = myItems.size(); i < count; i++) {
      NlComponent item = myItems.get(i);

      if (myActiveItem == myItems.get(i - 1)) {
        if (lastY < midpoint) {
          graphics.drawTop(item);
        }
      }
      else if (myActiveItem == item) {
        if (lastY >= midpoint) {
          graphics.drawTop(item);
        }
      }
      else {
        graphics.drawTop(item);
      }
    }
  }

  @Override
  public int getInsertIndex() {
    if (myActiveItem == null) {
      return -1;
    }

    int index = myMenu.getChildren().indexOf(myActiveItem);

    if (lastY < Utils.getMidpointY(myActiveItem)) {
      return index;
    }
    else if (index + 1 != myMenu.getChildCount()) {
      return index + 1;
    }
    else {
      return -1;
    }
  }
}
