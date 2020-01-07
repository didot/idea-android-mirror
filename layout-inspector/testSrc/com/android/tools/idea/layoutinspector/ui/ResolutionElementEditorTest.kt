/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.testutils.TestUtils
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.idea.layoutinspector.model.ResolutionStackModel
import com.android.tools.idea.layoutinspector.properties.InspectorGroupPropertyItem
import com.android.tools.idea.layoutinspector.properties.InspectorPropertiesModel
import com.android.tools.idea.layoutinspector.util.ComponentUtil.flatten
import com.android.tools.idea.layoutinspector.util.InspectorBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.impl.model.TextFieldPropertyEditorModel
import com.android.tools.property.panel.impl.ui.PropertyTextField
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Component
import java.awt.Container
import java.awt.event.ActionEvent
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import javax.swing.JComponent

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData/ui"
private const val DIFF_THRESHOLD = 0.5

@RunsInEdt
class ResolutionElementEditorTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.withSdk()

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @Before
  fun setUp() {
    InspectorBuilder.setUpDemo(projectRule)
  }

  @After
  fun tearDown() {
    InspectorBuilder.tearDownDemo()
  }

  @Test
  fun testPaint() {
    val editors = createEditor()
    checkImage(editors, "Closed")

    editors[0].editorModel.isExpandedTableItem = true
    checkImage(editors, "Open")

    setExpandLabel(editors[0], 0, true)
    checkImage(editors, "OpenWithDetails")

    setExpandLabel(editors[1], 0, true)
    checkImage(editors, "OpenWithTwoDetails")
  }

  @Test
  fun testDynamicHeight() {
    var updateCount = 0
    val editors = createEditor()
    val editor = editors[0]
    editor.updateRowHeight = { updateCount++ }
    assertThat(editor.isCustomHeight).isFalse()

    editor.editorModel.isExpandedTableItem = true
    assertThat(editor.isCustomHeight).isTrue()
    assertThat(updateCount).isEqualTo(0)

    setExpandLabel(editor, 0, true)
    assertThat(editor.isCustomHeight).isTrue()
    assertThat(updateCount).isEqualTo(1)

    setExpandLabel(editor, 0, false)
    assertThat(editor.isCustomHeight).isTrue()
    assertThat(updateCount).isEqualTo(2)

    editor.editorModel.isExpandedTableItem = false
    assertThat(editor.isCustomHeight).isFalse()
  }

  private fun checkImage(editors: List<ResolutionElementEditor>, expected: String) {
    editors.forEach { updateSize(it) }
    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(200, 300, BufferedImage.TYPE_INT_ARGB)
    val graphics = generatedImage.createGraphics()
    graphics.fillRect(0, 0, 200, 300)
    editors[0].paint(graphics)
    var y = editors[0].height
    if (editors[0].editorModel.isExpandedTableItem) {
      for (index in 1 until editors.size) {
        graphics.transform = AffineTransform.getTranslateInstance(0.0, y.toDouble())
        editors[index].paint(graphics)
        y += editors[index].height
      }
    }
    val platform = SystemInfo.OS_NAME.replace(' ', '_')
    val filename = "$TEST_DATA_PATH/testResolutionEditorPaint$expected$platform.png"
    ImageDiffUtil.assertImageSimilar(File(TestUtils.getWorkspaceRoot(), filename),
                                     generatedImage, DIFF_THRESHOLD)
  }

  private fun updateSize(component: Component) {
    component.invalidate()
    if (component is Container) {
      component.components.forEach { updateSize(it) }
      component.size = component.preferredSize
      component.doLayout()
    }
    else {
      component.size = component.preferredSize
    }
  }

  private fun setExpandLabel(editor: ResolutionElementEditor, expandIndex: Int, open: Boolean) {
    val keyStroke = if (open) KeyStrokes.RIGHT else KeyStrokes.LEFT
    val link = findLinkComponent(editor, expandIndex)!!
    val action = link.getActionForKeyStroke(keyStroke)!!
    val event = ActionEvent(link, ActionEvent.ACTION_PERFORMED, "open")
    action.actionPerformed(event)
  }

  private fun createEditor(): List<ResolutionElementEditor> {
    val propertiesModel = InspectorBuilder.createModel(projectRule)
    val item = InspectorBuilder.createProperty("title", ATTR_TEXT_COLOR, Type.COLOR, null, propertiesModel)
    val textStyleMaterial = ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, "TextAppearance.Material")
    val map = listOf(textStyleMaterial).associateWith { item.resourceLookup!!.findAttributeValue(item, it) }
    val value = item.resourceLookup!!.findAttributeValue(item, item.source!!)
    val property = InspectorGroupPropertyItem(
      ANDROID_URI, item.attrName, item.type, value, null, item.group, item.source, item.view, item.resourceLookup, map)
    val editors = mutableListOf<ResolutionElementEditor>()
    editors.add(createEditor(property, propertiesModel))
    property.children.forEach { editors.add(createEditor(it, propertiesModel)) }
    return editors
  }

  private fun createEditor(property: PropertyItem, propertiesModel: InspectorPropertiesModel): ResolutionElementEditor {
    val model = ResolutionStackModel(propertiesModel)
    val editorModel = TextFieldPropertyEditorModel(property, true)
    val editorComponent = PropertyTextField(editorModel)
    editorModel.readOnly = true
    return ResolutionElementEditor(model, editorModel, editorComponent)
  }

  private fun findLinkComponent(editor: ResolutionElementEditor, index: Int): JComponent? =
    flatten(editor).filter { (it as? JComponent)?.actionMap?.get("open") != null }[index] as JComponent?
}
