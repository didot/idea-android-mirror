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
package com.android.tools.idea.uibuilder.api;

import static com.android.SdkConstants.ANDROIDX_APPCOMPAT_LIB_ARTIFACT;
import static com.android.SdkConstants.ANDROIDX_CARD_VIEW_ARTIFACT;
import static com.android.SdkConstants.ANDROIDX_CONSTRAINT_LAYOUT_LIB_ARTIFACT;
import static com.android.SdkConstants.ANDROIDX_LEANBACK_ARTIFACT;
import static com.android.SdkConstants.ANDROIDX_MATERIAL_ARTIFACT;
import static com.android.SdkConstants.ANDROIDX_RECYCLER_VIEW_ARTIFACT;
import static com.android.SdkConstants.ANDROIDX_SUPPORT_LIB_ARTIFACT;
import static com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT;
import static com.android.SdkConstants.DESIGN_LIB_ARTIFACT;
import static com.android.SdkConstants.LEANBACK_V17_ARTIFACT;
import static com.android.SdkConstants.SUPPORT_LIB_ARTIFACT;
import static com.google.common.truth.Truth.assertThat;

import com.android.AndroidXConstants;
import org.junit.Test;

public class PaletteComponentHandlerTest {

  @Test
  public void testGetGradleCoordinateId() {
    PaletteComponentHandler handler = new PaletteComponentHandler() {};

    assertThat(handler.getGradleCoordinateId(AndroidXConstants.NESTED_SCROLL_VIEW.oldName())).isEqualTo(SUPPORT_LIB_ARTIFACT);
    assertThat(handler.getGradleCoordinateId(AndroidXConstants.NESTED_SCROLL_VIEW.newName())).isEqualTo(ANDROIDX_SUPPORT_LIB_ARTIFACT);

    assertThat(handler.getGradleCoordinateId(
      AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_BARRIER.oldName())).isEqualTo(CONSTRAINT_LAYOUT_LIB_ARTIFACT);
    assertThat(handler.getGradleCoordinateId(
      AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_BARRIER.newName())).isEqualTo(ANDROIDX_CONSTRAINT_LAYOUT_LIB_ARTIFACT);

    assertThat(handler.getGradleCoordinateId(AndroidXConstants.CLASS_BROWSE_FRAGMENT.oldName())).isEqualTo(LEANBACK_V17_ARTIFACT);
    assertThat(handler.getGradleCoordinateId(AndroidXConstants.CLASS_BROWSE_FRAGMENT.newName())).isEqualTo(ANDROIDX_LEANBACK_ARTIFACT);

    assertThat(handler.getGradleCoordinateId(AndroidXConstants.CLASS_FLOATING_ACTION_BUTTON.oldName())).isEqualTo(DESIGN_LIB_ARTIFACT);
    assertThat(handler.getGradleCoordinateId(AndroidXConstants.CLASS_FLOATING_ACTION_BUTTON.newName())).isEqualTo(ANDROIDX_MATERIAL_ARTIFACT);

    // Note: RecyclerViewHandler is overriding the default and will return RECYCLER_VIEW_LIB_ARTIFACT for non AndroidX controls
    assertThat(handler.getGradleCoordinateId(AndroidXConstants.RECYCLER_VIEW.oldName())).isEqualTo(APPCOMPAT_LIB_ARTIFACT);
    assertThat(handler.getGradleCoordinateId(AndroidXConstants.RECYCLER_VIEW.newName())).isEqualTo(ANDROIDX_RECYCLER_VIEW_ARTIFACT);

    // Note: CardViewHandler is overriding the default and will return CARD_VIEW_LIB_ARTIFACT for non AndroidX controls
    assertThat(handler.getGradleCoordinateId(AndroidXConstants.CARD_VIEW.oldName())).isEqualTo(APPCOMPAT_LIB_ARTIFACT);
    assertThat(handler.getGradleCoordinateId(AndroidXConstants.CARD_VIEW.newName())).isEqualTo(ANDROIDX_CARD_VIEW_ARTIFACT);

    assertThat(handler.getGradleCoordinateId(AndroidXConstants.TOOLBAR_V7.oldName())).isEqualTo(APPCOMPAT_LIB_ARTIFACT);
    assertThat(handler.getGradleCoordinateId(AndroidXConstants.TOOLBAR_V7.newName())).isEqualTo(ANDROIDX_APPCOMPAT_LIB_ARTIFACT);
  }
}
