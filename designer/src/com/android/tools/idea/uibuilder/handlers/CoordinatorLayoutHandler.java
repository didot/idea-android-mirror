/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers;

import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.Insets;
import com.android.tools.idea.uibuilder.model.NlComponent;

import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Handler for the {@code <android.support.design.widget.CoordinatorLayout>} layout
 */
public class CoordinatorLayoutHandler extends ScrollViewHandler {
  @NotNull
  @Override
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_CONTEXT,
      ATTR_FITS_SYSTEM_WINDOWS);
  }

  @NotNull
  @Override
  public List<String> getLayoutInspectorProperties() {
    return Collections.singletonList(ATTR_LAYOUT_BEHAVIOR);
  }

  @Nullable
  @Override
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent layout,
                                       @NotNull List<NlComponent> components,
                                       @NotNull DragType type) {
    // The {@link CoordinatorDragHandler} handles the logic for anchoring a
    // single component to an existing component in the CoordinatorLayout.
    // If we are moving several components we probably don't want them to be
    // anchored to the same place, so instead we use the FrameLayoutHandler in
    // this case.
    if (components.size() == 1 && components.get(0) != null) {
      return new CoordinatorDragHandler(editor, layout, components, type);
    } else {
      return super.createDragHandler(editor, layout, components, type);
    }
  }

  private class CoordinatorDragHandler extends FrameLayoutHandler.FrameDragHandler {
    private NlComponent myAnchor;
    private NlComponent myDragged;
    private String myAnchorGravity;
    private String myGravity;
    @AndroidDpCoordinate
    private int myPreviewX;
    @AndroidDpCoordinate
    private int myPreviewY;

    public CoordinatorDragHandler(@NotNull ViewEditor editor,
                                  @NotNull SceneComponent layout,
                                  @NotNull List<NlComponent> components,
                                  @NotNull DragType type) {
      super(editor, CoordinatorLayoutHandler.this, layout, components, type);
      assert components.size() == 1;
      myDragged = components.get(0);
      assert myDragged != null;
    }

    @Override
    public void start(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers) {
      super.start(x, y, modifiers);
      checkPosition();
    }

    @Nullable
    @Override
    public String update(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers) {
      String result = super.update(x, y, modifiers);
      checkPosition();
      return result;
    }

    @Override
    public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers, @NotNull InsertType insertType) {
      checkPosition();
      if (myAnchor == null) {
        myDragged.setAttribute(AUTO_URI, ATTR_LAYOUT_ANCHOR, null);
        myDragged.setAttribute(AUTO_URI, ATTR_LAYOUT_ANCHOR_GRAVITY, null);
      } else {
        NlComponent root = myDragged.getRoot();
        root.ensureNamespace(APP_PREFIX, AUTO_URI);
        root.ensureNamespace(ANDROID_NS_NAME, ANDROID_URI);
        myAnchor.ensureId();
        String id = myAnchor.getAttribute(ANDROID_URI, ATTR_ID);
        myDragged.setAttribute(AUTO_URI, ATTR_LAYOUT_ANCHOR, id);
        myDragged.setAttribute(AUTO_URI, ATTR_LAYOUT_ANCHOR_GRAVITY, myAnchorGravity);
        myDragged.setAttribute(ANDROID_URI, ATTR_LAYOUT_GRAVITY, myGravity);
      }
      insertComponents(-1, insertType);
    }

    @Override
    public void paint(@NotNull NlGraphics gc) {
      if (myAnchor == null) {
        super.paint(gc);
      } else {
        Insets padding = myAnchor.getPadding();
        @AndroidCoordinate int anchorX = myDragged.x;
        @AndroidCoordinate int anchorY = myDragged.y;
        @AndroidCoordinate int anchorW = myDragged.w;
        @AndroidCoordinate int anchorH = myDragged.h;

        // Highlight the anchor
        gc.useStyle(NlDrawingStyle.DROP_RECIPIENT);
        gc.drawRect(anchorX + padding.left, anchorY + padding.top, anchorW + padding.width(), anchorH + padding.height());

        gc.useStyle(NlDrawingStyle.DROP_ZONE);
        @AndroidCoordinate int draggedW = myDragged.w;
        @AndroidCoordinate int draggedH = myDragged.h;

        gc.drawRect(anchorX - draggedW, anchorY - draggedH, draggedW * 2, draggedH * 2);
        gc.drawRect(anchorX + anchorW - draggedW, anchorY - draggedH, draggedW * 2, draggedH * 2);
        gc.drawRect(anchorX - draggedW, anchorY + anchorH - draggedH, draggedW * 2, draggedH * 2);
        gc.drawRect(anchorX + anchorW - draggedW, anchorY + anchorH - draggedH, draggedW * 2, draggedH * 2);
        if (anchorW > 4 * draggedW) {
          gc.drawRect(anchorX + anchorW / 2 - draggedW, anchorY - draggedH, draggedW * 2, draggedH * 2);
          gc.drawRect(anchorX + anchorW / 2 - draggedW, anchorY + anchorH - draggedH, draggedW * 2, draggedH * 2);
        }
        if (anchorH > 4 * draggedH) {
          gc.drawRect(anchorX - draggedW, anchorY + anchorH / 2 - draggedH, draggedW * 2, draggedH * 2);
          gc.drawRect(anchorX + anchorW - draggedW, anchorY + anchorH / 2 - draggedH, draggedW * 2, draggedH * 2);
        }
        if (anchorW > 4 * draggedW && anchorH > 4 * draggedH) {
          gc.drawRect(anchorX + anchorW / 2 - draggedW, anchorY + anchorH / 2 - draggedH, draggedW * 2, draggedH * 2);
        }
        if (myAnchorGravity != null) {
          gc.useStyle(NlDrawingStyle.DROP_PREVIEW);
          gc.drawRect(myPreviewX, myPreviewY, draggedW, draggedH);
        }
      }
    }

    private void checkPosition() {
      myAnchor = findAnchor();
      myAnchorGravity = null;
      myGravity = null;
      myPreviewX = -1;
      myPreviewY = -1;

      if (myAnchor != null) {
        String anchorHgrav = null;
        String anchorVgrav = null;
        String selfHgrav = null;
        String selfVgrav = null;
        @AndroidDpCoordinate int left = -1;
        @AndroidDpCoordinate int top = -1;
        @AndroidDpCoordinate int x = -1;
        @AndroidDpCoordinate int y = -1;

        if (lastX < myAnchor.x + myDragged.w) {
          anchorHgrav = GRAVITY_VALUE_LEFT;
          left = myAnchor.x - myDragged.w;
          x = lastX - myAnchor.x;
        } else if (lastX >= myAnchor.x + myAnchor.w - myDragged.w) {
          anchorHgrav = GRAVITY_VALUE_RIGHT;
          left = myAnchor.x + myAnchor.w - myDragged.w;
          x = lastX - (myAnchor.x + myAnchor.w - myDragged.w);
        } else if (myAnchor.w > 4 * myDragged.w &&
                   myAnchor.x + myAnchor.w / 2 - myDragged.w <= lastX &&
                   lastX < myAnchor.x + myAnchor.w / 2 + myDragged.w) {
          anchorHgrav = GRAVITY_VALUE_CENTER_HORIZONTAL;
          left = myAnchor.x + myAnchor.w / 2 - myDragged.w;
          x = (lastX - (myAnchor.x + myAnchor.w / 2 - myDragged.w)) / 2;
        }
        if (anchorHgrav != null) {
          if (x < myDragged.w / 3) {
            selfHgrav = GRAVITY_VALUE_LEFT;
          } else if (x < 2 * myDragged.w / 3) {
            selfHgrav = GRAVITY_VALUE_CENTER_HORIZONTAL;
            left += myDragged.w / 2;
          } else {
            selfHgrav = GRAVITY_VALUE_RIGHT;
            left += myDragged.w;
          }
        }

        if (lastY < myAnchor.y + myDragged.h) {
          anchorVgrav = GRAVITY_VALUE_TOP;
          top = myAnchor.y - myDragged.h;
          y = lastY - myAnchor.y;
        } else if (lastY >= myAnchor.y + myAnchor.h - myDragged.h) {
          anchorVgrav = GRAVITY_VALUE_BOTTOM;
          top = myAnchor.y + myAnchor.h - myDragged.h;
          y = lastY - (myAnchor.y + myAnchor.h - myDragged.h);
        } else if (myAnchor.h > 4 * myDragged.h &&
                   myAnchor.y + myAnchor.h / 2 - myDragged.h <= lastY &&
                   lastY < myAnchor.y + myAnchor.h / 2 + myDragged.h) {
          anchorVgrav = GRAVITY_VALUE_CENTER_VERTICAL;
          top = myAnchor.y + myAnchor.h / 2 - myDragged.h;
          y = (lastY - (myAnchor.y + myAnchor.h / 2 - myDragged.h)) / 2;
        }
        if (anchorVgrav != null) {
          if (y < myDragged.h / 3) {
            selfVgrav = GRAVITY_VALUE_TOP;
          } else if (y < 2 * myDragged.h / 3) {
            selfVgrav = GRAVITY_VALUE_CENTER_VERTICAL;
            top += myDragged.h / 2;
          } else {
            selfVgrav = GRAVITY_VALUE_BOTTOM;
            top += myDragged.h;
          }
        }

        if (anchorHgrav != null && anchorVgrav != null) {
          myAnchorGravity = anchorVgrav + "|" + anchorHgrav;
          myGravity = selfVgrav + "|" + selfHgrav;
          myPreviewX = left;
          myPreviewY = top;
        }
      }
    }

    @Nullable
    NlComponent findAnchor() {
      for (int i = layout.getChildCount() - 1; i >= 0; i--) {
        NlComponent component = layout.getChild(i).getNlComponent();
        assert component != null;
        if (component.x < lastX && lastX < component.x + component.w &&
            component.y < lastY && lastY < component.y + component.h &&
            component.w > myDragged.w * 3 && component.h > myDragged.h * 3) {
          return component;
        }
      }
      return null;
    }
  }
}
