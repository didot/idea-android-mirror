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
package com.android.tools.idea.uibuilder.handlers;

import static com.android.SdkConstants.ATTR_ADDITIONAL_PADDING_END_FOR_ICON;
import static com.android.SdkConstants.ATTR_ADDITIONAL_PADDING_START_FOR_ICON;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_BACKGROUND_TINT;
import static com.android.SdkConstants.ATTR_BACKGROUND_TINT_MODE;
import static com.android.SdkConstants.ATTR_CORNER_RADIUS;
import static com.android.SdkConstants.ATTR_ELEVATION;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_ICON_PADDING;
import static com.android.SdkConstants.ATTR_ICON_TINT;
import static com.android.SdkConstants.ATTR_ICON_TINT_MODE;
import static com.android.SdkConstants.ATTR_INSET_BOTTOM;
import static com.android.SdkConstants.ATTR_INSET_LEFT;
import static com.android.SdkConstants.ATTR_INSET_RIGHT;
import static com.android.SdkConstants.ATTR_INSET_TOP;
import static com.android.SdkConstants.ATTR_ON_CLICK;
import static com.android.SdkConstants.ATTR_RIPPLE_COLOR;
import static com.android.SdkConstants.ATTR_STATE_LIST_ANIMATOR;
import static com.android.SdkConstants.ATTR_STROKE_COLOR;
import static com.android.SdkConstants.ATTR_STROKE_WIDTH;
import static com.android.SdkConstants.ATTR_STYLE;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class ButtonHandler extends TextViewHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_STYLE,
      ATTR_STATE_LIST_ANIMATOR,
      ATTR_ON_CLICK,
      ATTR_ELEVATION,
      ATTR_INSET_LEFT,
      ATTR_INSET_RIGHT,
      ATTR_INSET_TOP,
      ATTR_INSET_BOTTOM,
      ATTR_BACKGROUND,
      ATTR_BACKGROUND_TINT,
      ATTR_BACKGROUND_TINT_MODE,
      ATTR_ICON,
      ATTR_ICON_PADDING,
      ATTR_ICON_TINT,
      ATTR_ICON_TINT_MODE,
      ATTR_ADDITIONAL_PADDING_START_FOR_ICON,
      ATTR_ADDITIONAL_PADDING_END_FOR_ICON,
      ATTR_STROKE_COLOR,
      ATTR_STROKE_WIDTH,
      ATTR_CORNER_RADIUS,
      ATTR_RIPPLE_COLOR);
  }
}
