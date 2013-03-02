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
package com.intellij.android.designer.designSurface.layout.flow;

import com.intellij.android.designer.designSurface.AbstractEditOperation;
import com.intellij.android.designer.designSurface.graphics.DrawingStyle;
import com.intellij.android.designer.designSurface.graphics.LineInsertFeedback;
import com.intellij.android.designer.designSurface.graphics.RectangleFeedback;
import com.intellij.android.designer.designSurface.layout.AbstractFlowBaseOperation;
import com.intellij.android.designer.model.Margins;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class FlowBaseOperation extends AbstractFlowBaseOperation {
  public FlowBaseOperation(RadComponent container, OperationContext context, boolean horizontal) {
    super(container, context, horizontal);
  }

  @Override
  protected Rectangle getBounds(RadComponent component, FeedbackLayer layer) {
    Rectangle bounds = component.getBounds(layer);

    Margins margins = ((RadViewComponent)component).getMargins(layer);

    if (margins.isEmpty()) {
      return bounds;
    }

    bounds.x -= margins.left;
    bounds.width += margins.left;

    bounds.y -= margins.top;
    bounds.height += margins.top;

    bounds.width += margins.right;
    bounds.height += margins.bottom;

    return bounds;
  }

  @Override
  protected void createInsertFeedback() {
    myInsertFeedback = new LineInsertFeedback(DrawingStyle.DROP_ZONE_ACTIVE, !myHorizontal);
    myInsertFeedback.size(myBounds.width, myBounds.height);
  }

  @Override
  protected void createFirstInsertFeedback() {
    myFirstInsertFeedback = new RectangleFeedback(DrawingStyle.DROP_ZONE_ACTIVE);
    myFirstInsertFeedback.setBounds(myBounds);
  }

  @Override
  protected void execute(@Nullable RadComponent insertBefore) throws Exception {
    AbstractEditOperation.execute(myContext, (RadViewComponent)myContainer, myComponents, (RadViewComponent)insertBefore);
  }
}