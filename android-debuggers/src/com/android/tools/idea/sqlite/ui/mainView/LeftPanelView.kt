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
package com.android.tools.idea.sqlite.ui.mainView

import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.idea.sqlite.localization.DatabaseInspectorBundle
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class LeftPanelView(private val mainView: DatabaseInspectorViewImpl) {
  private val rootPanel = JPanel(BorderLayout())
  private val tree = Tree()

  private val refreshSchemaButton = CommonButton(AllIcons.Actions.Refresh)
  private val runSqlButton = CommonButton(StudioIcons.DatabaseInspector.NEW_QUERY)
  private val keepConnectionsOpenButton = CommonButton(StudioIcons.DatabaseInspector.KEEP_DATABASES_OPEN)

  val component = rootPanel
  val databasesCount: Int get() = (tree.model.root as? DefaultMutableTreeNode)?.childCount ?: 0

  init {
    val northPanel = createNorthPanel()
    val centerPanel = createCenterPanel()

    rootPanel.add(northPanel, BorderLayout.NORTH)
    rootPanel.add(centerPanel, BorderLayout.CENTER)

    setUpSchemaTree(tree)
  }

  fun updateKeepConnectionOpenButton(enabled: Boolean) {
    if (enabled) {
      keepConnectionsOpenButton.icon = StudioIcons.DatabaseInspector.KEEP_DATABASES_OPEN
    }
    else {
      keepConnectionsOpenButton.icon = StudioIcons.DatabaseInspector.ALLOW_DATABASES_TO_CLOSE
    }

    keepConnectionsOpenButton.disabledIcon = IconLoader.getDisabledIcon(keepConnectionsOpenButton.icon)
  }

  fun addDatabaseSchema(viewDatabase: ViewDatabase, schema: SqliteSchema?, index: Int) {
    val treeModel = tree.model as DefaultTreeModel

    val root = if (treeModel.root == null) {
      val root = DefaultMutableTreeNode("Databases")
      treeModel.setRoot(root)
      root
    } else {
      treeModel.root as DefaultMutableTreeNode
    }

    refreshSchemaButton.isEnabled = true
    runSqlButton.isEnabled = true
    keepConnectionsOpenButton.isEnabled = true

    val schemaNode = DefaultMutableTreeNode(viewDatabase)
    schema?.tables?.sortedBy { it.name }?.forEach { table ->
      val tableNode = DefaultMutableTreeNode(table)
      table.columns.forEach { column -> tableNode.add(DefaultMutableTreeNode(column)) }
      schemaNode.add(tableNode)
    }

    treeModel.insertNodeInto(schemaNode, root, index)
    tree.expandPath(TreePath(schemaNode.path))
  }

  // TODO(b/149920358) handle error by recreating the view.
  fun updateDatabase(viewDatabase: ViewDatabase, diffOperations: List<SchemaDiffOperation>) {
    val treeModel = tree.model as DefaultTreeModel
    val databaseNode = findDatabaseNode(viewDatabase)
    databaseNode.userObject = viewDatabase

    for (diffOp in diffOperations) {
      when (diffOp) {
        is AddTable -> {
          addNewTableNode(treeModel, databaseNode, diffOp.indexedSqliteTable, diffOp.columns)
        }
        is AddColumns -> {
          val tableNode = findTableNode(databaseNode, diffOp.tableName)
                          ?: error(DatabaseInspectorBundle.message("tree.node.not.found", diffOp.tableName))
          tableNode.userObject = diffOp.newTable
          addColumnsToTableNode(treeModel, tableNode, diffOp.columns)
        }
        is RemoveTable -> {
          val tableNode = findTableNode(databaseNode, diffOp.tableName)
                          ?: error(DatabaseInspectorBundle.message("tree.node.not.found", diffOp.tableName))

          treeModel.removeNodeFromParent(tableNode)
        }
        is RemoveColumns -> {
          val tableNode = findTableNode(databaseNode, diffOp.tableName)
                          ?: error(DatabaseInspectorBundle.message("tree.node.not.found", diffOp.tableName))

          tableNode.userObject = diffOp.newTable
          diffOp.columnsToRemove.map { findColumnNode(tableNode, it.name) }.forEach { treeModel.removeNodeFromParent(it) }
        }
      }
    }
  }

  /**
   * Removes [viewDatabase] from the schema [tree].
   * @return The number of open databases after [viewDatabase] has been removed.
   */
  fun removeDatabaseSchema(viewDatabase: ViewDatabase): Int {
    val treeModel = tree.model as DefaultTreeModel
    val databaseNode = findDatabaseNode(viewDatabase)
    treeModel.removeNodeFromParent(databaseNode)

    if (databasesCount == 0) {
      tree.model = DefaultTreeModel(null)

      refreshSchemaButton.isEnabled = false
      runSqlButton.isEnabled = false
      keepConnectionsOpenButton.isEnabled = false
    }

    return databasesCount
  }

  private fun createNorthPanel(): JPanel {
    val northPanel = JPanel(FlowLayout(FlowLayout.LEFT))

    refreshSchemaButton.disabledIcon = IconLoader.getDisabledIcon(AllIcons.Actions.Refresh)
    refreshSchemaButton.name = "refresh-schema-button"
    refreshSchemaButton.isEnabled = false
    refreshSchemaButton.toolTipText = DatabaseInspectorBundle.message("action.refresh.schema.tooltip")
    northPanel.add(refreshSchemaButton)
    refreshSchemaButton.addActionListener {
      mainView.listeners.forEach { it.refreshAllOpenDatabasesSchemaActionInvoked() }
    }

    runSqlButton.disabledIcon = IconLoader.getDisabledIcon(StudioIcons.DatabaseInspector.NEW_QUERY)
    runSqlButton.name = "run-sql-button"
    runSqlButton.isEnabled = false
    runSqlButton.toolTipText = DatabaseInspectorBundle.message("action.run.query.tooltip")
    northPanel.add(runSqlButton)

    runSqlButton.addActionListener { mainView.listeners.forEach { it.openSqliteEvaluatorTabActionInvoked() } }

    keepConnectionsOpenButton.disabledIcon = IconLoader.getDisabledIcon(keepConnectionsOpenButton.icon)
    keepConnectionsOpenButton.name = "keep-connections-open-button"
    keepConnectionsOpenButton.isEnabled = false
    HelpTooltip()
      .setTitle(DatabaseInspectorBundle.message("action.keep.open.tooltip.title"))
      .setDescription(DatabaseInspectorBundle.message("action.keep.open.tooltip.desc"))
      .setLink(DatabaseInspectorBundle.message("learn.more")) {
        BrowserUtil.browse("https://d.android.com/r/studio-ui/db-inspector-help/lock-connections")
      }
      .installOn(keepConnectionsOpenButton)
    northPanel.add(keepConnectionsOpenButton)

    keepConnectionsOpenButton.addActionListener { mainView.listeners.forEach { it.toggleKeepConnectionOpenActionInvoked() } }

    return northPanel
  }

  private fun createCenterPanel(): JPanel {
    val centerPanel = JPanel(BorderLayout())
    centerPanel.border = IdeBorderFactory.createBorder(SideBorder.TOP)

    val scrollPane = JBScrollPane(tree)
    scrollPane.border = JBUI.Borders.empty()

    centerPanel.add(scrollPane, BorderLayout.CENTER)

    return centerPanel
  }

  private fun setUpSchemaTree(tree: Tree) {
    tree.cellRenderer = SchemaTreeCellRenderer()

    tree.model = DefaultTreeModel(null)
    tree.toggleClickCount = 0
    tree.emptyText.text = DatabaseInspectorBundle.message("nothing.to.show")
    tree.emptyText.isShowAboveCenter = false

    tree.name = "left-panel-tree"

    setUpSchemaTreeListeners(tree)
  }

  private fun setUpSchemaTreeListeners(tree: Tree) {
    // TODO(b/137731627) why do we have to do this manually? Check how is done in Device Explorer.
    val treeKeyAdapter = object : KeyAdapter() {
      override fun keyPressed(event: KeyEvent) {
        if (event.keyCode == KeyEvent.VK_ENTER) {
          fireAction(tree, event)
        }
      }
    }

    val treeDoubleClickListener = object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        fireAction(tree, event)
        return true
      }
    }

    tree.addKeyListener(treeKeyAdapter)
    treeDoubleClickListener.installOn(tree)
  }

  private fun fireAction(tree: Tree, e: InputEvent) {
    val lastPathComponent = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode ?: return

    val sqliteTable = lastPathComponent.userObject
    if (sqliteTable is SqliteTable) {
      val parentNode = lastPathComponent.parent as DefaultMutableTreeNode
      val viewDatabase = parentNode.userObject as ViewDatabase
      mainView.listeners.forEach { l -> l.tableNodeActionInvoked(viewDatabase.databaseId, sqliteTable) }
      e.consume()
    }
    else {
      val path = TreePath(lastPathComponent.path)
      if (tree.isExpanded(path)) {
        tree.collapsePath(path)
      }
      else {
        tree.expandPath(path)
      }
    }
  }

  /** Adds a child node to [databaseNode] for the table in [sqliteTableToAdd] at the specified index. */
  private fun addNewTableNode(treeModel: DefaultTreeModel, databaseNode: DefaultMutableTreeNode, sqliteTableToAdd: IndexedSqliteTable, sqliteColumnsToAdd: List<IndexedSqliteColumn>) {
    val (sqliteTable, index) = sqliteTableToAdd
    val newTableNode = DefaultMutableTreeNode(sqliteTable)
    sqliteColumnsToAdd.sortedBy { it.index }.map { it.sqliteColumn }.forEach { column -> newTableNode.add(DefaultMutableTreeNode(column)) }
    treeModel.insertNodeInto(newTableNode, databaseNode, index)
  }

  /** Add child nodes to [tableNode] for each column in [columnsToAdd] at the specified index */
  private fun addColumnsToTableNode(
    treeModel: DefaultTreeModel,
    tableNode: DefaultMutableTreeNode,
    columnsToAdd: List<IndexedSqliteColumn>
  ) {
    columnsToAdd.forEach {  indexedSqliteColumn ->
      val (sqliteColumn, index) = indexedSqliteColumn
      val newColumnNode = DefaultMutableTreeNode(sqliteColumn)
      treeModel.insertNodeInto(newColumnNode, tableNode, index)
    }
  }

  private fun findDatabaseNode(viewDatabase: ViewDatabase): DefaultMutableTreeNode {
    val root = tree.model.root as DefaultMutableTreeNode
    return root.children().asSequence()
      .map { it as DefaultMutableTreeNode }
      .first { it.userObject == viewDatabase }
  }

  private fun findTableNode(databaseNode: DefaultMutableTreeNode, tableName: String): DefaultMutableTreeNode? {
    return databaseNode.children().asSequence()
      .map { it as DefaultMutableTreeNode }
      .filter { it.userObject is SqliteTable }
      .firstOrNull { (it.userObject as SqliteTable).name == tableName }
  }

  private fun findColumnNode(databaseNode: DefaultMutableTreeNode, columnName: String): DefaultMutableTreeNode? {
    return databaseNode.children().asSequence()
      .map { it as DefaultMutableTreeNode }
      .filter { it.userObject is SqliteColumn }
      .firstOrNull { (it.userObject as SqliteColumn).name == columnName }
  }

  private class SchemaTreeCellRenderer : ColoredTreeCellRenderer() {
    private val colorTextAttributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.gray)

    override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean
    ) {
      toolTipText = null
      if (value is DefaultMutableTreeNode) {
        when (val userObject = value.userObject) {
          is ViewDatabase -> {
            append(userObject.databaseId.name)
            if (userObject.isOpen) {
              icon = StudioIcons.DatabaseInspector.DATABASE
            }
            else {
              append(" (closed)", colorTextAttributes)
              icon = StudioIcons.DatabaseInspector.DATABASE_UNAVAILABLE
            }
            toolTipText = userObject.databaseId.path
          }

          is SqliteTable -> {
            icon = if (userObject.isView) {
              StudioIcons.DatabaseInspector.VIEW
            }
            else {
              StudioIcons.DatabaseInspector.TABLE
            }
            append(userObject.name)
          }

          is SqliteColumn -> {
            if (userObject.inPrimaryKey) icon = StudioIcons.DatabaseInspector.PRIMARY_KEY
            else icon = StudioIcons.DatabaseInspector.COLUMN
            append(userObject.name)
            append("  :  ", colorTextAttributes)
            append(userObject.affinity.name.toUpperCase(Locale.US), colorTextAttributes)
            append(if (userObject.isNullable) "" else ", NOT NULL", colorTextAttributes)
          }

          // String (e.g. "Tables" node)
          is String -> {
            append(userObject)
          }
        }
      }

      if (hasFocus) {
        icon = ColoredIconGenerator.generateWhiteIcon(icon)
      }
    }
  }
}