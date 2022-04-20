/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.borderLight
import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData
import com.android.tools.idea.appinspection.inspectors.network.view.rules.createDecoratedTable
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

private const val MINIMUM_DETAILS_VIEW_WIDTH = 400
const val TEXT_LABEL_WIDTH = 220

/**
 * View to display a single network interception rule and its detailed information.
 */
class RuleDetailsView : JPanel() {
  var selectedRule = RuleData(-1, "", false)
    set(value) {
      if (field == value) {
        return
      }
      field = value

      val detailsPanel = ScrollablePanel(VerticalLayout(18))
      // Reserve 14px extra space for scroll bar on the right.
      detailsPanel.border = JBUI.Borders.empty(6, 16, 20, 30)
      updateRuleInfo(detailsPanel, field)
      scrollPane.setViewportView(detailsPanel)
    }

  private val scrollPane = JBScrollPane()

  init {
    layout = TabularLayout("*", "28px,*")
    border = BorderFactory.createEmptyBorder()
    minimumSize = Dimension(MINIMUM_DETAILS_VIEW_WIDTH, minimumSize.height)
    val headingPanel = JPanel(BorderLayout())
    val instanceViewLabel = JLabel("Rule Details")
    instanceViewLabel.border = BorderFactory.createEmptyBorder(0, 6, 0, 0)
    headingPanel.add(instanceViewLabel, BorderLayout.WEST)
    add(headingPanel, TabularLayout.Constraint(0, 0))
    scrollPane.border = AdtUiUtils.DEFAULT_TOP_BORDER
    add(scrollPane, TabularLayout.Constraint(1, 0))
  }

  private fun updateRuleInfo(detailsPanel: ScrollablePanel, rule: RuleData) {
    detailsPanel.add(
      createKeyValuePair(
        "Name",
        createTextField(rule.name, TEXT_LABEL_WIDTH) { text ->
          rule.name = text
        }
      )
    )
    detailsPanel.add(createCategoryPanel("Origin", listOf(
      createKeyValuePair(
        "Host url",
        createTextField(rule.criteria.host, TEXT_LABEL_WIDTH) { text ->
          rule.criteria.apply {
            host = text
          }
        }
      )
    )))

    detailsPanel.add(createCategoryPanel("Header rules", listOf(
      createRulesTable(rule.headerRuleTableModel)
    )))

    detailsPanel.add(createCategoryPanel("Body rules", listOf(
      createRulesTable(rule.bodyRuleTableModel)
    )))

    TreeWalker(detailsPanel).descendantStream().forEach { (it as? JComponent)?.isOpaque = false }
    detailsPanel.background = primaryContentBackground
    detailsPanel.isOpaque
  }

  private fun createRulesTable(model: ListTableModel<RuleData.TransformationRuleData>): JComponent {
    val table = TableView(model)
    val decorator = ToolbarDecorator.createDecorator(table)

    val addRowAction: (RuleData.TransformationRuleData) -> Unit = { newItem ->
      model.addRow(newItem)
      val index = table.convertRowIndexToView(model.rowCount - 1)
      table.selectionModel.setSelectionInterval(index, index)
    }
    decorator.setAddAction {
      when (model) {
        is RuleData.HeaderRulesTableModel -> HeaderRuleDialog(null, addRowAction).show()
        is RuleData.BodyRulesTableModel -> BodyRuleDialog(null, addRowAction).show()
      }
    }

    val replaceRowAction: (RuleData.TransformationRuleData) -> Unit = { newItem ->
      val selectedItem = table.selectedObject
      val replaceIndex = model.items.indexOf(selectedItem)
      if (replaceIndex != -1) {
        model.items = model.items.map {
          if (it == selectedItem) newItem else it
        }
        model.fireTableRowsUpdated(replaceIndex, replaceIndex)
        val tableIndex = table.convertRowIndexToView(replaceIndex)
        table.selectionModel.setSelectionInterval(tableIndex, tableIndex)
      }
    }
    decorator.setEditAction {
      val selectedItem = table.selectedObject
      when (model) {
        is RuleData.HeaderRulesTableModel -> HeaderRuleDialog(selectedItem, replaceRowAction).show()
        is RuleData.BodyRulesTableModel -> BodyRuleDialog(selectedItem, replaceRowAction).show()
      }
    }

    val container = ScrollablePanel(TabularLayout("*", "200px"))
    container.add(createDecoratedTable(table, decorator).apply {
      border = BorderFactory.createLineBorder(borderLight)
    }, TabularLayout.Constraint(0, 0))
    return JBScrollPane().apply { setViewportView(container) }
  }
}
