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
package com.android.tools.idea.uibuilder.property2.inspector

import com.android.SdkConstants.*
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.testutils.InspectorTestUtil
import com.android.tools.idea.uibuilder.property2.testutils.LineType
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Rule
import org.junit.Test

class ViewInspectorBuilderTest {
  @JvmField @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testAllButtonProperties() {
    runInEdtAndWait {
      val util = InspectorTestUtil(projectRule, BUTTON)
      val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
      addButtonProperties(util)
      builder.attachToInspector(util.inspector, util.properties)
      assertThat(util.inspector.lines).hasSize(6)
      assertThat(util.inspector.lines[0].type).isEqualTo(LineType.TITLE)
      assertThat(util.inspector.lines[1].editorModel?.property?.name).isEqualTo(ATTR_STATE_LIST_ANIMATOR)
      assertThat(util.inspector.lines[2].editorModel?.property?.name).isEqualTo(ATTR_ON_CLICK)
      assertThat(util.inspector.lines[3].editorModel?.property?.name).isEqualTo(ATTR_ELEVATION)
      assertThat(util.inspector.lines[4].editorModel?.property?.name).isEqualTo(ATTR_BACKGROUND)
      assertThat(util.inspector.lines[5].editorModel?.property?.name).isEqualTo(ATTR_BACKGROUND_TINT)
    }
  }

  @Test
  fun testButtonWithSomeMissingProperties() {
    runInEdtAndWait {
      val util = InspectorTestUtil(projectRule, BUTTON)
      val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
      addButtonProperties(util)
      util.removeProperty(ANDROID_URI, ATTR_BACKGROUND_TINT)
      util.removeProperty(ANDROID_URI, ATTR_VISIBILITY)
      util.removeProperty(ANDROID_URI, ATTR_ON_CLICK)
      builder.attachToInspector(util.inspector, util.properties)
      assertThat(util.inspector.lines).hasSize(4)
      assertThat(util.inspector.lines[0].type).isEqualTo(LineType.TITLE)
      assertThat(util.inspector.lines[1].editorModel?.property?.name).isEqualTo(ATTR_STATE_LIST_ANIMATOR)
      assertThat(util.inspector.lines[2].editorModel?.property?.name).isEqualTo(ATTR_ELEVATION)
      assertThat(util.inspector.lines[3].editorModel?.property?.name).isEqualTo(ATTR_BACKGROUND)
    }
  }

  @Test
  fun testImageViewWithAppCompatProperties() {
    runInEdtAndWait {
      val util = InspectorTestUtil(projectRule, IMAGE_VIEW)
      val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
      addImageViewProperties(util, withAppCompat = true)
      builder.attachToInspector(util.inspector, util.properties)
      assertThat(util.inspector.lines).hasSize(8)
      assertThat(util.inspector.lines[0].type).isEqualTo(LineType.TITLE)
      assertThat(util.inspector.lines[1].editorModel?.property?.name).isEqualTo(ATTR_SRC_COMPAT)
      assertThat(util.inspector.lines[1].editorModel?.property?.namespace).isEqualTo(AUTO_URI)
      assertThat(util.inspector.lines[2].editorModel?.property?.name).isEqualTo(ATTR_SRC_COMPAT)
      assertThat(util.inspector.lines[2].editorModel?.property?.namespace).isEqualTo(TOOLS_URI)
      assertThat(util.inspector.lines[3].editorModel?.property?.name).isEqualTo(ATTR_CONTENT_DESCRIPTION)
      assertThat(util.inspector.lines[4].editorModel?.property?.name).isEqualTo(ATTR_BACKGROUND)
      assertThat(util.inspector.lines[5].editorModel?.property?.name).isEqualTo(ATTR_SCALE_TYPE)
      assertThat(util.inspector.lines[6].editorModel?.property?.name).isEqualTo(ATTR_ADJUST_VIEW_BOUNDS)
      assertThat(util.inspector.lines[7].editorModel?.property?.name).isEqualTo(ATTR_CROP_TO_PADDING)
    }
  }

  @Test
  fun testImageViewWithoutAppCompatProperties() {
    runInEdtAndWait {
      val util = InspectorTestUtil(projectRule, IMAGE_VIEW)
      val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
      addImageViewProperties(util, withAppCompat = false)
      builder.attachToInspector(util.inspector, util.properties)
      assertThat(util.inspector.lines).hasSize(8)
      assertThat(util.inspector.lines[0].type).isEqualTo(LineType.TITLE)
      assertThat(util.inspector.lines[1].editorModel?.property?.name).isEqualTo(ATTR_SRC)
      assertThat(util.inspector.lines[1].editorModel?.property?.namespace).isEqualTo(ANDROID_URI)
      assertThat(util.inspector.lines[2].editorModel?.property?.name).isEqualTo(ATTR_SRC)
      assertThat(util.inspector.lines[2].editorModel?.property?.namespace).isEqualTo(TOOLS_URI)
      assertThat(util.inspector.lines[3].editorModel?.property?.name).isEqualTo(ATTR_CONTENT_DESCRIPTION)
      assertThat(util.inspector.lines[4].editorModel?.property?.name).isEqualTo(ATTR_BACKGROUND)
      assertThat(util.inspector.lines[5].editorModel?.property?.name).isEqualTo(ATTR_SCALE_TYPE)
      assertThat(util.inspector.lines[6].editorModel?.property?.name).isEqualTo(ATTR_ADJUST_VIEW_BOUNDS)
      assertThat(util.inspector.lines[7].editorModel?.property?.name).isEqualTo(ATTR_CROP_TO_PADDING)
    }
  }

  private fun addButtonProperties(util: InspectorTestUtil) {
    util.addProperty("", ATTR_STYLE, NelePropertyType.STYLE)
    util.addProperty(ANDROID_URI, ATTR_BACKGROUND, NelePropertyType.COLOR_OR_DRAWABLE)
    util.addProperty(ANDROID_URI, ATTR_BACKGROUND_TINT, NelePropertyType.COLOR)
    util.addProperty(ANDROID_URI, ATTR_STATE_LIST_ANIMATOR, NelePropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_ELEVATION, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_ON_CLICK, NelePropertyType.STRING)
  }

  private fun addImageViewProperties(util: InspectorTestUtil, withAppCompat: Boolean) {
    if (withAppCompat) {
      util.addProperty(AUTO_URI, ATTR_SRC_COMPAT, NelePropertyType.COLOR_OR_DRAWABLE)
    }
    else {
      util.addProperty(ANDROID_URI, ATTR_SRC, NelePropertyType.COLOR_OR_DRAWABLE)
    }
    util.addProperty(ANDROID_URI, ATTR_CONTENT_DESCRIPTION, NelePropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_BACKGROUND, NelePropertyType.COLOR_OR_DRAWABLE)
    util.addProperty(ANDROID_URI, ATTR_SCALE_TYPE, NelePropertyType.INTEGER)
    util.addProperty(ANDROID_URI, ATTR_ADJUST_VIEW_BOUNDS, NelePropertyType.BOOLEAN)
    util.addProperty(ANDROID_URI, ATTR_CROP_TO_PADDING, NelePropertyType.BOOLEAN)
  }
}
