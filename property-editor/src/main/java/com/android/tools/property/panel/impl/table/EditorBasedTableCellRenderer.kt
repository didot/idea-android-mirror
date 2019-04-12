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
package com.android.tools.property.panel.impl.table

import com.android.tools.property.ptable2.PTable
import com.android.tools.property.ptable2.PTableCellRenderer
import com.android.tools.property.ptable2.PTableColumn
import com.android.tools.property.ptable2.PTableItem
import com.android.tools.property.panel.api.*
import com.android.tools.property.panel.impl.ui.CellPanel
import com.android.tools.property.panel.impl.ui.PropertyComboBox
import com.android.tools.property.panel.impl.ui.PropertyTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.border.Border

/**
 * A simple text cell renderer for displaying the value of a [PTableItem].
 *
 * The properties values in a table are rendered using the control actually
 * used to edit the value. Cache these controls and their editor model for
 * each [ControlType].
 */
class EditorBasedTableCellRenderer<in P : PropertyItem>(private val itemClass: Class<P>,
                                                        private val controlTypeProvider: ControlTypeProvider<P>,
                                                        private val editorProvider: EditorProvider<P>,
                                                        private val fontSize: UIUtil.FontSize,
                                                        private val defaultRenderer: PTableCellRenderer) : PTableCellRenderer {
  private val componentCache = mutableMapOf<ControlKey, Pair<PropertyEditorModel, JComponent>>()
  private val leftSpacing = JBUI.scale(LEFT_STANDARD_INDENT) + JBUI.scale(MIN_SPACING) + UIUtil.getTreeCollapsedIcon().iconWidth
  private val depthIndent = JBUI.scale(DEPTH_INDENT)

  override fun getEditorComponent(table: PTable, item: PTableItem, column: PTableColumn, depth: Int,
                                  isSelected: Boolean, hasFocus: Boolean): JComponent? {
    if (!itemClass.isInstance(item)) {
      return defaultRenderer.getEditorComponent(table, item, column, depth, isSelected, hasFocus)
    }
    val property = itemClass.cast(item)
    val controlType = controlTypeProvider(property)
    val hasBrowseButton = property.browseButton != null
    val key = ControlKey(controlType, hasBrowseButton)
    val (model, editor) = componentCache[key] ?: createEditor(key, property, column, depth, table.gridLineColor)
    model.property = property
    if (isSelected && hasFocus) {
      editor.foreground = UIUtil.getTreeSelectionForeground(true)
      editor.background = UIUtil.getTreeSelectionBackground(true)
    }
    else {
      editor.foreground = table.foregroundColor
      editor.background = table.backgroundColor
    }
    return editor
  }

  private fun createEditor(key: ControlKey,
                           property: P,
                           column: PTableColumn,
                           depth: Int,
                           gridLineColor: Color): Pair<PropertyEditorModel, JComponent> {
    val (model, editor) = editorProvider.createEditor(property, asTableCellEditor = true)
    val panel = CellPanel()
    panel.add(editor, BorderLayout.CENTER)
    panel.border = createBorder(column, depth, editor, gridLineColor)
    editor.font = UIUtil.getLabelFont(fontSize)

    val result = Pair(model, panel)
    componentCache[key] = result
    return result
  }

  private fun createBorder(column: PTableColumn, depth: Int, editor: JComponent, gridLineColor: Color): Border =
    when (column) {
      PTableColumn.NAME -> BorderFactory.createEmptyBorder(0, leftSpacing - editorLeftMargin(editor) + depth * depthIndent, 0, 0)
      PTableColumn.VALUE -> JBUI.Borders.customLine(gridLineColor, 0, 1, 0, 0)
    }

  // Somewhat dirty: Estimate the space to the left edge of the text in the component to align with other
  // names in the table.
  private fun editorLeftMargin(editor: JComponent): Int =
    when (editor) {
      is PropertyTextField -> editor.margin.left
      is PropertyComboBox -> editor.editor.margin.left
      else -> editor.insets.left
    }

  private data class ControlKey(val type: ControlType, val hasBrowseButton: Boolean)
}
