/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.sketchImporter.structure;

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.interfaces.SketchLayer;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.interfaces.SketchLayerable;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;

import static com.android.tools.idea.resourceExplorer.sketchImporter.logic.PathDataUtils.buildShapeString;

public class SketchShapeGroup extends SketchLayer implements SketchLayerable {
  private final SketchStyle style;
  private final SketchLayer[] layers;
  private final short clippingMaskMode;
  private final boolean hasClippingMask;
  private final short windingRule;

  public SketchShapeGroup(@NotNull String classType,
                          @NotNull String objectId,
                          int booleanOperation,
                          @NotNull Rectangle.Double frame,
                          boolean isFlippedHorizontal,
                          boolean isFlippedVertical,
                          boolean isVisible,
                          @NotNull String name,
                          int rotation,
                          boolean shouldBreakMaskChain,
                          @NotNull SketchStyle style,
                          @NotNull SketchLayer[] layers,
                          short clippingMaskMode,
                          boolean hasClippingMask,
                          short windingRule) {
    super(classType, objectId, booleanOperation, frame, isFlippedHorizontal, isFlippedVertical, isVisible, name, rotation,
          shouldBreakMaskChain);
    this.style = style;
    this.layers = layers;
    this.clippingMaskMode = clippingMaskMode;
    this.hasClippingMask = hasClippingMask;
    this.windingRule = windingRule;
  }

  @Override
  @NotNull
  public SketchStyle getStyle() {
    return style;
  }

  @Override
  @NotNull
  public SketchLayer[] getLayers() {
    return layers;
  }

  public short getClippingMaskMode() {
    return clippingMaskMode;
  }

  public boolean hasClippingMask() {
    return hasClippingMask;
  }

  public short getWindingRule() {
    return windingRule;
  }

  @Override
  public void setAbsoluteLocation(ArrayList<String> paths, Point2D.Double parentCoords) {
    paths.add(buildShapeString(this, parentCoords));
  }
}

