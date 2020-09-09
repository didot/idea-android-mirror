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
package com.android.tools.idea.emulator.actions.dialogs

import com.android.annotations.concurrency.Slow
import com.android.emulator.control.SnapshotPackage
import com.android.emulator.snapshot.SnapshotOuterClass.Snapshot
import com.android.tools.adtui.ui.ImagePanel
import com.android.tools.adtui.util.getHumanizedSize
import com.android.tools.idea.emulator.EmptyStreamObserver
import com.android.tools.idea.emulator.EmulatorController
import com.android.tools.idea.emulator.EmulatorView
import com.android.tools.idea.emulator.actions.BootMode
import com.android.tools.idea.emulator.actions.BootType
import com.android.tools.idea.emulator.actions.SnapshotInfo
import com.android.tools.idea.emulator.actions.SnapshotManager
import com.android.tools.idea.emulator.actions.createBootMode
import com.android.tools.idea.emulator.logger
import com.google.common.html.HtmlEscapers
import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.AnActionButton
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.TableUtil
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.DialogManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.Label
import com.intellij.ui.components.dialog
import com.intellij.ui.components.htmlComponent
import com.intellij.ui.layout.applyToComponent
import com.intellij.ui.layout.panel
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.table.TableView
import com.intellij.util.concurrency.AppExecutorUtil.createBoundedApplicationPoolExecutor
import com.intellij.util.text.JBDateFormat
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.kotlin.utils.SmartSet
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Font
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Comparator
import java.util.Date
import java.util.Locale
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.RowSorter
import javax.swing.SortOrder
import javax.swing.SwingConstants
import javax.swing.event.TableModelEvent
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.JTableHeader
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

/**
 * Dialog for managing emulator snapshots.
 */
internal class ManageSnapshotsDialog(
  private val emulator: EmulatorController,
  private val emulatorView: EmulatorView?
) {

  private val snapshotTableModel = SnapshotTableModel()
  private val snapshotTable = SnapshotTable(snapshotTableModel)
  private val takeSnapshotButton = JButton("Take Snapshot").apply {
    addActionListener { createSnapshot() }
  }
  private val runningOperationLabel = JBLabel().apply {
    isVisible = false
  }
  private val snapshotImagePanel = ImagePanel()
  private val snapshotInfoPanel = htmlComponent(lineWrap = true)
  private val selectionStateLabel = Label("No snapshots selected")
  private val coldBootCheckBox = JBCheckBox("Start without using a snapshot (cold boot)").apply {
    addItemListener {
      if (isSelected != snapshotTableModel.isColdBoot) {
        val bootSnapshotRow = if (isSelected) snapshotTableModel.bootSnapshotIndex() else 0
        snapshotTableModel.setBootSnapshot(bootSnapshotRow, !isSelected)
      }
    }
  }
  private val snapshotManager = SnapshotManager(emulator.emulatorId.avdFolder, emulator.emulatorId.avdId)
  private val backgroundExecutor = createBoundedApplicationPoolExecutor("ManageSnapshotsDialog", 1)
  private var dialogManager: DialogManager? = null
  /** An invisible text field used to trigger clearing of the error messages. See [clearError]. */
  private var validationText = JTextField()

  init {
    // Install a double-click listener to edit snapshot on double click.
    snapshotTable.apply {
      object : DoubleClickListener() {
        override fun onDoubleClick(event: MouseEvent): Boolean {
          val point = event.point
          val row: Int = rowAtPoint(point)
          if (row <= QUICK_BOOT_SNAPSHOT_MODEL_ROW || columnAtPoint(point) < 0) {
            return false
          }
          selectionModel.setSelectionInterval(row, row)
          editSnapshot()
          return true
        }
      }.installOn(this)
    }
  }

  /**
   * Creates contents of the dialog.
   */
  private fun createPanel(): DialogPanel {
    return panel {
      row("Snapshots:") {}
      row {
        cell(isVerticalFlow = true) {
          component(createTablePanel()).constraints(growX, growY, pushX, pushY)
        }

        cell(isVerticalFlow = true) {
          component(selectionStateLabel).constraints(growX, growY).applyToComponent {
            preferredSize = JBUI.size(260, 100)
            verticalTextPosition = SwingConstants.CENTER
            horizontalAlignment = SwingConstants.CENTER
          }
          component(snapshotImagePanel).constraints(growX, growY, pushX).applyToComponent {
            preferredSize = JBUI.size(260, 100)
            isVisible = false
          }
          component(snapshotInfoPanel).constraints(growX).applyToComponent {
            val fontMetrics = getFontMetrics(font)
            preferredSize = Dimension(0, fontMetrics.height * 6)
            isVisible = false
          }
        }
      }
      row {
        cell {
          component(takeSnapshotButton)
          component(runningOperationLabel)
        }
      }
      row {
        component(coldBootCheckBox)
      }
    }.also {
      val selectionModel = snapshotTable.selectionModel
      selectionModel.addListSelectionListener {
        clearError()
        var count = 0
        for (i in selectionModel.minSelectionIndex..selectionModel.maxSelectionIndex) {
          if (selectionModel.isSelectedIndex(i)) {
            count++
          }
        }
        if (count == 1) {
          selectionStateLabel.isVisible = false
          snapshotImagePanel.isVisible = true
          snapshotInfoPanel.isVisible = true
          snapshotTable.selectedObject?.let { updateSnapshotDetails(it) }
        }
        else {
          selectionStateLabel.isVisible = true
          snapshotImagePanel.isVisible = false
          snapshotInfoPanel.isVisible = false
          selectionStateLabel.text = if (count == 0) "No snapshots selected" else "$count snapshots selected"
        }
      }
    }
  }

  private fun updateSnapshotDetails(snapshot: SnapshotInfo) {
    snapshotImagePanel.image = Toolkit.getDefaultToolkit().getImage(snapshot.screenshotFile.toString())
    val htmlEscaper = HtmlEscapers.htmlEscaper()
    val name = htmlEscaper.escape(snapshot.displayName)
    val size = getHumanizedSize(snapshot.sizeOnDisk)
    val creationTime = JBDateFormat.getFormatter().formatDateTime(snapshot.creationTime).replace(",", "")
    val folderName = htmlEscaper.escape(snapshot.snapshotFolder.fileName.toString())
    val attributeSection = if (snapshot.creationTime == 0L) "not created yet" else "${size}, created ${creationTime}<br>"
    val fileSection = if (snapshot.isQuickBoot) "" else "File: ${folderName}<br>"
    val description = htmlEscaper.escape(snapshot.description)
    snapshotInfoPanel.text = "<html><b>${name}</b><br>${attributeSection}${fileSection}<br>${description}</html>"
  }

  private fun createTablePanel(): JPanel {
    return BorderLayoutPanel().apply {
      add(
        ToolbarDecorator.createDecorator(snapshotTable)
          .setEditAction {
            editSnapshot()
          }
          .setRemoveAction {
            removeSnapshots()
          }
          .setAddAction(null)
          .setMoveDownAction(null)
          .setMoveUpAction(null)
          .addExtraAction(LoadSnapshotAction())
          .setRemoveActionUpdater { !snapshotTable.selectionModel.isSelectedIndex(QUICK_BOOT_SNAPSHOT_MODEL_ROW) }
          .setEditActionUpdater { !snapshotTable.selectionModel.isSelectedIndex(QUICK_BOOT_SNAPSHOT_MODEL_ROW) }
          .setPreferredSize(JBUI.size(500, 450))
          .setToolbarPosition(ActionToolbarPosition.BOTTOM)
          .setButtonComparator("Load Snapshot", "Edit", "Remove")
          .createPanel()
      )
    }
  }

  private fun createSnapshot() {
    clearError()
    val snapshotName = composeSnapshotName()
    val completionTracker = object : EmptyStreamObserver<SnapshotPackage>() {

      init {
        takeSnapshotButton.transferFocusBackward() // Transfer focus to the table.
        takeSnapshotButton.isEnabled = false // Disable the button temporarily.
        startLongOperation("Saving snapshot...")
        emulatorView?.showLongRunningOperationIndicator("Saving state...")
      }

      override fun onCompleted() {
        finished()
        backgroundExecutor.submit {
          val snapshot = snapshotManager.readSnapshotInfo(snapshotName)
          EventQueue.invokeLater {
            if (snapshot == null) {
              showError()
            }
            else {
              snapshotTableModel.addRow(snapshot)
              snapshotTable.selection = listOf(snapshot)
              TableUtil.scrollSelectionToVisible(snapshotTable)
            }
          }
        }
      }

      override fun onError(t: Throwable) {
        showError()
        finished()
      }

      private fun finished() {
        EventQueue.invokeLater {
          emulatorView?.hideLongRunningOperationIndicator()
          takeSnapshotButton.isEnabled = true // Re-enable the button.
          endLongOperation()
        }
      }

      private fun showError() {
        showError("Unable to create a snapshot")
      }
    }

    emulator.saveSnapshot(snapshotName, completionTracker)
  }

  private fun loadSnapshot() {
    clearError()
    val snapshot = snapshotTable.selectedObject ?: return
    val errorHandler = object : EmptyStreamObserver<SnapshotPackage>() {

      init {
        startLongOperation("Loading snapshot...")
        emulatorView?.showLongRunningOperationIndicator("Loading snapshot...")
      }

      override fun onNext(response: SnapshotPackage) {
        if (!response.success) {
          val error = response.err.toString(UTF_8)
          val detail = if (error.isEmpty()) "" else " - $error"
          EventQueue.invokeLater {
            showError("""Error loading snapshot "${snapshot.displayName}"${detail}""")
          }
        }
      }

      override fun onCompleted() {
        finished()
      }

      override fun onError(t: Throwable) {
        finished()
        EventQueue.invokeLater {
          showError("""Error loading snapshot. See the error log""")
        }
      }

      private fun finished() {
        EventQueue.invokeLater {
          endLongOperation()
          emulatorView?.hideLongRunningOperationIndicator()
        }
      }
    }

    emulator.loadSnapshot(snapshot.snapshotId, errorHandler)
  }

  private fun editSnapshot() {
    TableUtil.stopEditing(snapshotTable)
    clearError()
    val selectedIndex = snapshotTable.convertRowIndexToModel(snapshotTable.selectedRow)
    if (selectedIndex < 0) {
      return
    }
    val snapshot = snapshotTable.selectedObject ?: return
    val dialog = EditSnapshotDialog(snapshot.displayName, snapshot.description, snapshot == snapshotTableModel.bootSnapshot)
    if (dialog.createWrapper(parent = snapshotTable).showAndGet()) {
      if (dialog.snapshotName != snapshot.displayName || dialog.snapshotDescription != snapshot.description) {
        val proto = snapshot.snapshot.toBuilder().apply {
          logicalName = if (dialog.snapshotName == snapshot.snapshotId) "" else dialog.snapshotName
          description = dialog.snapshotDescription
        }.build()
        val updatedSnapshot = SnapshotInfo(snapshot.snapshotFolder, proto, snapshot.sizeOnDisk)
        val selectionState = SelectionState(snapshotTable)
        snapshotTableModel.removeRow(selectedIndex)
        snapshotTableModel.insertRow(selectedIndex, updatedSnapshot)
        snapshotTableModel.setBootSnapshot(selectedIndex, dialog.useToBoot)
        selectionState.restoreSelection()
        backgroundExecutor.submit {
          snapshotManager.saveSnapshotProto(updatedSnapshot.snapshotFolder, updatedSnapshot.snapshot)
        }
      }
    }
  }

  private fun removeSnapshots() {
    TableUtil.stopEditing(snapshotTable)
    clearError()
    val selectionState = SelectionState(snapshotTable)
    val selectionModel = snapshotTable.selectionModel
    val foldersToDelete = mutableListOf<Path>()
    for (row in selectionModel.maxSelectionIndex downTo selectionModel.minSelectionIndex) {
      if (selectionModel.isSelectedIndex(row)) {
        val index = snapshotTable.convertRowIndexToModel(row)
        val snapshot = snapshotTableModel.getItem(index)
        if (snapshot == snapshotTableModel.bootSnapshot) {
          // The boot snapshot is being deleted. Change the boot snapshot to QuickBoot.
          snapshotTableModel.setBootSnapshot(QUICK_BOOT_SNAPSHOT_MODEL_ROW, true)
        }
        foldersToDelete.add(snapshot.snapshotFolder)
        snapshotTableModel.removeRow(index)
      }
    }
    selectionModel.clearSelection()

    backgroundExecutor.submit {
      var errors = false
      for (folder in foldersToDelete) {
        try {
          FileUtil.delete(folder)
        }
        catch (e: IOException) {
          logger.error(e)
          errors = true
        }

        if (errors) {
          val snapshots = snapshotManager.fetchSnapshotList()
          EventQueue.invokeLater {
            snapshotTableModel.update(snapshots)
            selectionState.restoreSelection()
            showError("Some snapshots could not be deleted")
          }
        }
        else {
          val n = foldersToDelete.size
          val message = if (n == 1) "$n snapshot deleted" else "$n snapshots deleted"
          EventQueue.invokeLater {
            selectionStateLabel.text = message
          }
        }
      }
    }
  }

  private fun startLongOperation(message: String) {
    runningOperationLabel.text = message
    runningOperationLabel.isVisible = true
  }

  private fun endLongOperation() {
    runningOperationLabel.text = ""
    runningOperationLabel.isVisible = false
  }

  /**
   * Shows a red error message at the bottom of the dialog.
   */
  private fun showError(message: String) {
    // Unfortunately, DialogWrapper.setErrorInfoAll and the related methods are protected
    // nd cannot be called directly since we are not subclassing DialogWrapper. To get
    // around this limitation we take advantage of the existing setErrorInfoAll call in
    // the MyDialogWrapper.performAction method in components.kt.
    dialogManager?.performAction {
      validationText.text = message
      listOf(ValidationInfo(message), ValidationInfo(message, validationText))
    }
  }

  /**
   * Clears the error message at the bottom of the dialog.
   */
  private fun clearError() {
    // Changing validationText.text triggers a call to setErrorInfoAll(emptyList()) in
    // the MyDialogWrapper.clearErrorInfoOnFirstChange method in components.kt.
    validationText.text = ""
  }

  /**
   * Creates the dialog wrapper.
   */
  fun createWrapper(project: Project): DialogWrapper {
    return dialog(
      title = "Manage Snapshots",
      resizable = true,
      panel = createPanel(),
      project = project,
      createActions = { listOf(CloseDialogAction()) })
      .also {
        backgroundExecutor.submit {
          readBootModeAndSnapshotList()
        }
        dialogManager = it as DialogManager
      }
  }

  /*
   * Reads the boot mode and the snapshot list.
   */
  @Slow
  private fun readBootModeAndSnapshotList() {
    val bootMode = snapshotManager.readBootMode() ?: BootMode(BootType.COLD, null)
    val snapshots = snapshotManager.fetchSnapshotList().toMutableList()
    // Put the QuickBoot snapshot at the top of the list.
    snapshots.sortWith(compareByDescending(SnapshotInfo::isQuickBoot))
    if (snapshots.firstOrNull()?.isQuickBoot != true) {
      // Add a fake QuickBoot snapshot if is not present.
      snapshots.add(QUICK_BOOT_SNAPSHOT_MODEL_ROW, SnapshotInfo(snapshotManager.snapshotsFolder, Snapshot.getDefaultInstance(), 0L))
    }
    val bootSnapshot = when (bootMode.bootType) {
      BootType.COLD -> null
      BootType.QUICK -> snapshots[QUICK_BOOT_SNAPSHOT_MODEL_ROW]
      else -> snapshots.find { it.snapshotId == bootMode.bootSnapshotId }
    }

    EventQueue.invokeLater {
      snapshotTableModel.update(snapshots, bootSnapshot)
    }
  }


  private inner class SnapshotTableModel : ListTableModel<SnapshotInfo>() {

    private val nameColumn = object : SnapshotColumnInfo("Name") {

      override fun getRenderer(snapshot: SnapshotInfo): TableCellRenderer {
        return SnapshotNameRenderer()
      }

      override fun getComparator(): Comparator<SnapshotInfo> {
        return compareBy(Collator.getInstance(), SnapshotInfo::displayName)
      }
    }

    private val creationTimeColumn = object : SnapshotColumnInfo("Created") {

      override fun getRenderer(snapshot: SnapshotInfo): TableCellRenderer {
        return SnapshotCreationTimeRenderer()
      }

      override fun getComparator(): Comparator<SnapshotInfo> {
        return compareBy(SnapshotInfo::creationTime)
      }
    }

    private val sizeColumn = object : SnapshotColumnInfo("Size") {

      override fun getRenderer(snapshot: SnapshotInfo): TableCellRenderer {
        return SnapshotSizeRenderer()
      }

      override fun getComparator(): Comparator<SnapshotInfo> {
        return compareBy(SnapshotInfo::sizeOnDisk).thenBy(Collator.getInstance(), SnapshotInfo::displayName)
      }
    }

    private val bootColumn = object : ColumnInfo<SnapshotInfo, Boolean>("Use to Boot") {

      override fun valueOf(snapshot: SnapshotInfo): Boolean {
        return snapshot == bootSnapshot
      }

      override fun getColumnClass(): Class<*>? {
        return java.lang.Boolean::class.java
      }

      override fun isCellEditable(snapshot: SnapshotInfo): Boolean {
        return true
      }

      override fun getEditor(snapshot: SnapshotInfo): TableCellEditor {
        return BooleanTableCellEditor()
      }

      override fun getRenderer(snapshot: SnapshotInfo): TableCellRenderer {
        return BooleanTableCellRenderer()
      }
    }

    var bootSnapshot: SnapshotInfo? = null
      private set(value) {
        field = value
        coldBootCheckBox.isSelected = value == null
      }

    val isColdBoot: Boolean
      get() = bootSnapshot == null

    val nameColumnIndex: Int
    val bootColumnIndex: Int

    init {
      columnInfos = arrayOf(nameColumn, creationTimeColumn, sizeColumn, bootColumn)
      isSortable = true
      nameColumnIndex = 0
      bootColumnIndex = columnCount - 1
    }

    override fun setValueAt(value: Any?, row: Int, column: Int) {
      if (column == bootColumnIndex) {
        setBootSnapshot(row, value as Boolean)
      }
      else {
        invalidColumn(column)
      }
    }

    fun setBootSnapshot(row: Int, value: Boolean) {
      if (value) {
        val oldBootSnapshot = bootSnapshot
        bootSnapshot = getItem(row)
        if (oldBootSnapshot != null) {
          fireTableCellUpdated(snapshotIndex(oldBootSnapshot), bootColumnIndex)
        }
      }
      else {
        bootSnapshot = null
      }
      fireTableCellUpdated(row, bootColumnIndex)
      saveBootMode(bootSnapshot)
    }

    fun snapshotIndex(snapshot: SnapshotInfo) = indexOf(snapshot)

    fun bootSnapshotIndex() = bootSnapshot?.let { snapshotIndex(it) } ?: -1

    private fun saveBootMode(bootSnapshot: SnapshotInfo?) {
      val bootMode = createBootMode((bootSnapshot))
      backgroundExecutor.submit {
        snapshotManager.saveBootMode(bootMode)
      }
    }

    private fun invalidColumn(columnIndex: Int): Nothing {
      throw IllegalArgumentException("Invalid column ${columnIndex}")
    }

    fun update(snapshots: List<SnapshotInfo>, newBootSnapshot: SnapshotInfo?) {
      bootSnapshot = newBootSnapshot
      update(snapshots)
    }

    fun update(snapshots: List<SnapshotInfo>) {
      val savedSelection = SelectionState(snapshotTable)
      items = snapshots
      savedSelection.restoreSelection()
    }

    override fun getDefaultSortKey(): RowSorter.SortKey? {
      return RowSorter.SortKey(nameColumnIndex, SortOrder.ASCENDING)
    }
  }

  private inner class LoadSnapshotAction : AnActionButton("Load Snapshot", AllIcons.Actions.Upload) {

    override fun actionPerformed(event: AnActionEvent) {
      loadSnapshot()
    }
  }

  private class SnapshotTable(tableModel: SnapshotTableModel) : TableView<SnapshotInfo>(tableModel) {

    private var preferredColumnWidths: IntArray? = null

    override fun getModel(): SnapshotTableModel {
      return dataModel as SnapshotTableModel
    }

    init {
      createDefaultColumnsFromModel()
      setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS)

      addPropertyChangeListener { event ->
        if (event.propertyName == "model") {
          preferredColumnWidths = null
        }
      }
    }

    override fun createRowSorter(model: TableModel): TableRowSorter<TableModel> {
      return SnapshotRowSorter(model as SnapshotTableModel)
    }

    private fun getPreferredColumnWidth(column: Int): Int {
      var widths = preferredColumnWidths
      if (widths == null) {
        widths = calculatePreferredColumnWidths()
        preferredColumnWidths = widths
      }
      return widths[column]
    }

    private fun calculatePreferredColumnWidths(): IntArray {
      val widths = IntArray(model.columnCount)
      val tableWidth = width - insets.left - insets.right
      if (tableWidth <= 0) {
        return widths
      }
      var totalWidth = 0
      for (column in model.columnInfos.indices.reversed()) {
        val preferredColumnWidth = if (column == 0) {
          // The first column gets the remaining width.
          (tableWidth - totalWidth).coerceAtLeast(0)
        }
        else {
          calculatePreferredColumnWidth(column)
        }
        widths[column] = preferredColumnWidth
        totalWidth += preferredColumnWidth
      }
      return widths
    }

    override fun prepareRenderer(renderer: TableCellRenderer, row: Int, column: Int): Component {
      val component = super.prepareRenderer(renderer, row, column) as JComponent
      val tableColumn = columnModel.getColumn(column)
      val preferredWidth = getPreferredColumnWidth(column)
      tableColumn.preferredWidth = preferredWidth
      return component
    }

    override fun tableChanged(event: TableModelEvent) {
      preferredColumnWidths = null
      super.tableChanged(event)
    }

    private fun calculatePreferredColumnWidth(column: Int): Int {
      return getColumnHeaderWidth(column).coerceAtLeast(getColumnDataWidth(column))
    }

    private fun getColumnHeaderWidth(column: Int): Int {
      val tableHeader: JTableHeader = getTableHeader()
      val headerFontMetrics = tableHeader.getFontMetrics(tableHeader.font)
      return headerFontMetrics.stringWidth(getColumnName(column) + JBUIScale.scale(HEADER_GAP))
    }

    private fun getColumnDataWidth(column: Int): Int {
      var dataWidth = 0
      for (row in 0 until rowCount) {
        val cellRenderer: TableCellRenderer = getCellRenderer(row, column)
        val c: Component = super.prepareRenderer(cellRenderer, row, column)
        dataWidth = dataWidth.coerceAtLeast(c.preferredSize.width + intercellSpacing.width)
      }
      return dataWidth
    }

    private class SnapshotRowSorter(model: SnapshotTableModel) : DefaultColumnInfoBasedRowSorter(model) {

      override fun getComparator(column: Int): Comparator<*> {
        // Make sure that the QuickBoot snapshot is always at the top of the list.
        val quickBootComparator = if (sortKeys.firstOrNull()?.sortOrder == SortOrder.ASCENDING) {
          compareByDescending(SnapshotInfo::isQuickBoot)
        }
        else {
          compareBy(SnapshotInfo::isQuickBoot)
        }
        @Suppress("UNCHECKED_CAST")
        return quickBootComparator.then(super.getComparator(column) as Comparator<SnapshotInfo>)
      }
    }
  }

  private abstract class SnapshotTextColumnRenderer : DefaultTableCellRenderer() {

    override fun getTableCellRendererComponent(table: JTable,
                                               snapshot: Any,
                                               isSelected: Boolean,
                                               hasFocus: Boolean,
                                               row: Int,
                                               column: Int): Component {
      return super.getTableCellRendererComponent(table, snapshot, isSelected, false, row, column).apply {
        if ((snapshot as SnapshotInfo).isQuickBoot) {
          font = font.deriveFont(Font.ITALIC)
        }
      }
    }
  }

  private class SnapshotNameRenderer : SnapshotTextColumnRenderer() {

    override fun setValue(snapshot: Any) {
      snapshot as SnapshotInfo
      icon = null //TODO: Add tiny snapshot image.
      text = snapshot.displayName
    }
  }

  private class SnapshotCreationTimeRenderer : SnapshotTextColumnRenderer() {

    override fun setValue(snapshot: Any) {
      text = formatPrettySnapshotDateTime((snapshot as SnapshotInfo).creationTime)
    }
  }

  private class SnapshotSizeRenderer : SnapshotTextColumnRenderer() {

    override fun setValue(snapshot: Any) {
      text = formatSnapshotSize((snapshot as SnapshotInfo).sizeOnDisk)
    }
  }

  private abstract class SnapshotColumnInfo(name: String): ColumnInfo<SnapshotInfo, SnapshotInfo>(name) {

    override fun valueOf(snapshot: SnapshotInfo): SnapshotInfo {
      return snapshot
    }
  }

  private class SelectionState(val table: SnapshotTable) {
    private val selected = getSelectedSnapshotFolders()
    private val anchor: Path? = getSnapshotFolderAt(table.selectionModel.anchorSelectionIndex)
    private val lead: Path? = getSnapshotFolderAt(table.selectionModel.anchorSelectionIndex)

    fun restoreSelection() {
      val selectionModel: ListSelectionModel = table.selectionModel
      val model = table.model
      selectionModel.clearSelection()
      var anchorRow = -1
      var leadRow = -1
      for (i in 0 until model.rowCount) {
        val snapshotFolder = model.getItem(i).snapshotFolder
        val row = table.convertRowIndexToView(i)
        if (snapshotFolder in selected) {
          selectionModel.addSelectionInterval(row, row)
        }
        if (snapshotFolder == anchor) {
          anchorRow = row
        }
        if (snapshotFolder == lead) {
          leadRow = row
        }
      }
      selectionModel.anchorSelectionIndex = anchorRow
      selectionModel.leadSelectionIndex = leadRow
      TableUtil.scrollSelectionToVisible(table)
    }

    private fun getSelectedSnapshotFolders(): Set<Path> {
      val selectionModel: ListSelectionModel = table.selectionModel
      val minSelectionIndex = selectionModel.minSelectionIndex
      val maxSelectionIndex = selectionModel.maxSelectionIndex
      if (minSelectionIndex < 0 || maxSelectionIndex < 0) {
        return emptySet()
      }

      val result = SmartSet.create<Path>()
      for (i in minSelectionIndex..maxSelectionIndex) {
        if (selectionModel.isSelectedIndex(i)) {
          getSnapshotFolderAt(i)?.let { result.add(it) }
        }
      }
      return result
    }

    private fun getSnapshotFolderAt(row: Int): Path? {
      if (row < 0) {
        return null
      }
      val model = table.model
      val modelIndex: Int = table.convertRowIndexToModel(row)
      return if (modelIndex >= 0 && modelIndex < model.rowCount) model.getRowValue(modelIndex).snapshotFolder else null
    }
  }

  private class CloseDialogAction : AbstractAction(CommonBundle.getCloseButtonText()) {

    init {
      putValue(DialogWrapper.DEFAULT_ACTION, true)
    }

    override fun actionPerformed(event: ActionEvent) {
      val wrapper = DialogWrapper.findInstance(event.source as? Component)
      wrapper?.close(DialogWrapper.CLOSE_EXIT_CODE)
    }
  }
}

private fun formatPrettySnapshotDateTime(time: Long): String {
  return if (time > 0) JBDateFormat.getFormatter().formatPrettyDateTime(time).replace(",", "") else "-"
}

private fun formatSnapshotSize(size: Long): String {
  return if (size > 0) getHumanizedSize(size) else "-"
}

private fun composeSnapshotName(): String {
  val suffix = TIMESTAMP_FORMAT.format(Date())
  return "snap_${suffix}"
}

private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

/** Text offset in the table header. */
private const val HEADER_GAP = 20

private const val QUICK_BOOT_SNAPSHOT_MODEL_ROW = 0 // The QuickBoot snapshot is always first in the list.