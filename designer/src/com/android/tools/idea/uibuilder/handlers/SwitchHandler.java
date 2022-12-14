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

import static com.android.SdkConstants.ATTR_CHECKED;
import static com.android.SdkConstants.ATTR_SHOW_TEXT;
import static com.android.SdkConstants.ATTR_SPLIT_TRACK;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.ATTR_SWITCH_MIN_WIDTH;
import static com.android.SdkConstants.ATTR_SWITCH_PADDING;
import static com.android.SdkConstants.ATTR_SWITCH_TEXT_APPEARANCE;
import static com.android.SdkConstants.ATTR_TEXT_OFF;
import static com.android.SdkConstants.ATTR_TEXT_ON;
import static com.android.SdkConstants.ATTR_THUMB;
import static com.android.SdkConstants.ATTR_THUMB_TINT;
import static com.android.SdkConstants.ATTR_TRACK;
import static com.android.SdkConstants.ATTR_TRACK_TINT;
import static com.android.SdkConstants.PREFIX_ANDROID;
import static com.android.SdkConstants.TOOLS_NS_NAME_PREFIX;

import com.android.tools.idea.common.model.NlComponent;
import com.google.common.collect.ImmutableList;
import icons.StudioIcons;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public class SwitchHandler extends ButtonHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_STYLE,
      ATTR_SWITCH_TEXT_APPEARANCE,
      ATTR_SWITCH_MIN_WIDTH,
      ATTR_SWITCH_PADDING,
      ATTR_THUMB,
      ATTR_THUMB_TINT,
      ATTR_TRACK,
      ATTR_TRACK_TINT,
      ATTR_TEXT_ON,
      ATTR_TEXT_OFF,
      ATTR_CHECKED,
      TOOLS_NS_NAME_PREFIX + ATTR_CHECKED,
      ATTR_SHOW_TEXT,
      ATTR_SPLIT_TRACK);
  }

  @Override
  @NotNull
  public List<String> getBaseStyles(@NotNull String tagName) {
    return ImmutableList.of(PREFIX_ANDROID + "Widget.CompoundButton." + tagName);
  }

  @NotNull
  @Override
  public Icon getIcon(@NotNull NlComponent component) {
    return StudioIcons.LayoutEditor.Menu.SWITCH;
  }
}
