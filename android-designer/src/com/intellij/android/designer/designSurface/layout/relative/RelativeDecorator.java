/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.designSurface.layout.relative;

import com.intellij.android.designer.designSurface.graphics.DesignerGraphics;
import com.intellij.android.designer.designSurface.graphics.DrawingStyle;
import com.intellij.android.designer.model.Margins;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.layout.relative.RelativeInfo;
import com.intellij.designer.designSurface.DecorationLayer;
import com.intellij.designer.designSurface.StaticDecorator;
import com.intellij.designer.model.RadComponent;

import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
public class RelativeDecorator extends StaticDecorator {
  private static final int TOP = 0;
  private static final int BOTTOM = 1;
  private static final int BASELINE = 2;
  private static final int LEFT = 3;
  private static final int RIGHT = 4;

  public RelativeDecorator(RadComponent container) {
    super(container);
  }

  @Override
  protected void paint(DecorationLayer layer, Graphics2D g, RadComponent container) {
    if (!layer.showSelection() || container.getClientProperty(SnapPointFeedbackHost.KEY) != null) {
      return;
    }

    DesignerGraphics.useStroke(DrawingStyle.GUIDELINE_DASHED_STROKE, g);

    Rectangle bounds = container.getBounds(layer);
    Map<RadComponent, RelativeInfo> relativeInfos = container.getClientProperty(RelativeInfo.KEY);
    List<RadComponent> selection = layer.getArea().getSelection();

    for (RadComponent component : selection) {
      RelativeInfo info = relativeInfos.get(component);
      if (info != null) {
        paintOutRelative(layer, g, component, info);
        paintContainerRelative(g, bounds, info);
      }
    }

    DesignerGraphics.useStroke(DrawingStyle.GUIDELINE, g);

    for (RadComponent component : selection) {
      RelativeInfo info = relativeInfos.get(component);
      if (info != null) {
        paintContainerMarginRelative(layer, g, bounds, (RadViewComponent)component, info);
      }
    }
  }

  private static void paintOutRelative(DecorationLayer layer, Graphics2D g, RadComponent component, RelativeInfo info) {
    Rectangle bounds = component.getBounds(layer);

    paintHorizontal(layer, g, bounds, info.alignTop, TOP);
    paintHorizontal(layer, g, bounds, info.alignBottom, BOTTOM);
    paintHorizontal(layer, g, bounds, info.below, TOP);
    paintHorizontal(layer, g, bounds, info.above, BOTTOM);

    paintHorizontal(layer, g, bounds, info.alignBaseline, BASELINE);

    paintVertical(layer, g, bounds, info.alignLeft, LEFT);
    paintVertical(layer, g, bounds, info.alignRight, RIGHT);
    paintVertical(layer, g, bounds, info.toRightOf, LEFT);
    paintVertical(layer, g, bounds, info.toLeftOf, RIGHT);
  }

  private static void paintContainerRelative(Graphics2D g, Rectangle bounds, RelativeInfo info) {
    if (info.parentTop) {
      g.drawLine(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y);
    }
    if (info.parentBottom) {
      g.drawLine(bounds.x, bounds.y + bounds.height, bounds.x + bounds.width, bounds.y + bounds.height);
    }
    if (info.parentLeft) {
      g.drawLine(bounds.x, bounds.y, bounds.x, bounds.y + bounds.height);
    }
    if (info.parentRight) {
      g.drawLine(bounds.x + bounds.width, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height);
    }
    if (info.parentCenterHorizontal) {
      int centerX = bounds.x + bounds.width / 2 - 1;
      g.drawLine(centerX, bounds.y, centerX, bounds.y + bounds.height);
    }
    if (info.parentCenterVertical) {
      int centerY = bounds.y + bounds.height / 2 - 1;
      g.drawLine(bounds.x, centerY, bounds.x + bounds.width, centerY);
    }
  }

  private static void paintContainerMarginRelative(DecorationLayer layer,
                                                   Graphics2D g,
                                                   Rectangle containerBounds,
                                                   RadViewComponent component,
                                                   RelativeInfo info) {
    Rectangle componentBounds = component.getBounds(layer);
    Margins margins = component.getMargins();

    if (info.parentTop && margins.top > 0) {
      int x = componentBounds.x + componentBounds.width / 2;
      DesignerGraphics.drawArrow(DrawingStyle.GUIDELINE, g, x, componentBounds.y, x, containerBounds.y);
    }
    if (info.parentBottom && margins.bottom > 0) {
      int x = componentBounds.x + componentBounds.width / 2;
      DesignerGraphics.drawArrow(DrawingStyle.GUIDELINE, g, x, containerBounds.y, x, componentBounds.y + componentBounds.height);
    }
    if (info.parentLeft && margins.left > 0) {
      int y = componentBounds.y + componentBounds.height / 2;
      DesignerGraphics.drawArrow(DrawingStyle.GUIDELINE, g, componentBounds.x, y, containerBounds.x, y);
    }
    if (info.parentRight && margins.right > 0) {
      int y = componentBounds.y + componentBounds.height / 2;
      DesignerGraphics.drawArrow(DrawingStyle.GUIDELINE, g, containerBounds.x, y, componentBounds.x + componentBounds.width, y);
    }
  }

  private static void paintHorizontal(DecorationLayer layer, Graphics2D g, Rectangle from, RadComponent toComponent, int yType) {
    if (toComponent == null) {
      return;
    }

    Rectangle to = toComponent.getBounds(layer);

    int x1, x2;
    if (from.x < to.x) {
      x1 = from.x - SnapPointFeedbackHost.EXPAND_SIZE;
      x2 = to.x + to.width + SnapPointFeedbackHost.EXPAND_SIZE;
    }
    else {
      x1 = to.x - SnapPointFeedbackHost.EXPAND_SIZE;
      x2 = from.x + from.width + SnapPointFeedbackHost.EXPAND_SIZE;
    }

    int y;
    if (yType == TOP) {
      y = from.y;
    }
    else if (yType == BOTTOM) {
      y = from.y + from.height;
    }
    else {
      y = to.y;

      int baseline = ((RadViewComponent)toComponent).getBaseline();
      if (baseline != -1) {
        baseline = toComponent.fromModel(layer, new Dimension(0, baseline)).height;

        y += baseline;
      }
    }

    g.drawLine(x1, y, x2, y);
  }

  private static void paintVertical(DecorationLayer layer, Graphics2D g, Rectangle from, RadComponent toComponent, int xType) {
    if (toComponent == null) {
      return;
    }

    Rectangle to = toComponent.getBounds(layer);

    int y1, y2;
    if (from.y < to.y) {
      y1 = from.y - SnapPointFeedbackHost.EXPAND_SIZE;
      y2 = to.y + to.height + SnapPointFeedbackHost.EXPAND_SIZE;
    }
    else {
      y1 = to.y - SnapPointFeedbackHost.EXPAND_SIZE;
      y2 = from.y + from.height + SnapPointFeedbackHost.EXPAND_SIZE;
    }

    int x = xType == LEFT ? from.x : from.x + from.width;

    g.drawLine(x, y1, x, y2);
  }
}