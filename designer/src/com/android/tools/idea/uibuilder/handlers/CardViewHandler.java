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

import static com.android.SdkConstants.ANDROIDX_PKG_PREFIX;
import static com.android.SdkConstants.ATTR_CARD_BACKGROUND_COLOR;
import static com.android.SdkConstants.ATTR_CARD_CORNER_RADIUS;
import static com.android.SdkConstants.ATTR_CARD_ELEVATION;
import static com.android.SdkConstants.ATTR_CARD_PREVENT_CORNER_OVERLAP;
import static com.android.SdkConstants.ATTR_CARD_USE_COMPAT_PADDING;
import static com.android.SdkConstants.ATTR_CONTENT_PADDING;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.CARD_VIEW_LIB_ARTIFACT;
import static com.android.SdkConstants.VALUE_MATCH_PARENT;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;

import com.android.support.AndroidxNameUtils;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.handlers.frame.FrameLayoutHandler;
import com.android.xml.XmlBuilder;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

/**
 * Handler for the {@code <CardView>} widget.
 */
public class CardViewHandler extends FrameLayoutHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_CARD_BACKGROUND_COLOR,
      ATTR_CARD_CORNER_RADIUS,
      ATTR_CONTENT_PADDING,
      ATTR_CARD_ELEVATION,
      ATTR_CARD_PREVENT_CORNER_OVERLAP,
      ATTR_CARD_USE_COMPAT_PADDING);
  }

  @Override
  @NotNull
  @Language("XML")
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    switch (xmlType) {
      case COMPONENT_CREATION:
        return new XmlBuilder()
          .startTag(tagName)
          .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
          .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
          .endTag(tagName)
          .toString();
      default:
        return super.getXml(tagName, xmlType);
    }
  }

  @Override
  @NotNull
  public String getGradleCoordinateId(@NotNull String viewTag) {
    return viewTag.startsWith(ANDROIDX_PKG_PREFIX) ?
           AndroidxNameUtils.getCoordinateMapping(CARD_VIEW_LIB_ARTIFACT) :
           CARD_VIEW_LIB_ARTIFACT;
  }
}
