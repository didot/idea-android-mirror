/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat

import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.IDevice
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.adtui.toolwindow.splittingtabs.state.SplittingTabsStateProvider
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.ddms.DeviceContext
import com.android.tools.idea.logcat.actions.HeaderFormatOptionsAction
import com.android.tools.idea.logcat.messages.DocumentAppender
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.LogcatColors
import com.android.tools.idea.logcat.messages.MessageBacklog
import com.android.tools.idea.logcat.messages.MessageFormatter
import com.android.tools.idea.logcat.messages.MessageProcessor
import com.android.tools.idea.logcat.messages.TextAccumulator
import com.intellij.execution.impl.ConsoleBuffer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.tools.SimpleActionGroup
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.time.ZoneId

/**
 * The top level Logcat panel.
 */
internal class LogcatMainPanel(
  project: Project,
  private val popupActionGroup: ActionGroup,
  logcatColors: LogcatColors,
  state: LogcatPanelConfig?,
  zoneId: ZoneId = ZoneId.systemDefault()
) : BorderLayoutPanel(), SplittingTabsStateProvider, Disposable {

  @VisibleForTesting
  internal val editor: EditorEx = createEditor(project)
  private val documentAppender = DocumentAppender(project, editor.document)
  private val deviceContext = DeviceContext()
  private val formattingOptions = state?.formattingOptions ?: FormattingOptions()
  private val messageFormatter = MessageFormatter(formattingOptions, logcatColors, zoneId)
  private val messageBacklog = MessageBacklog(ConsoleBuffer.getCycleBufferSize())

  @VisibleForTesting
  internal val messageProcessor = MessageProcessor(this, messageFormatter::formatMessages, this::appendToDocument)
  private val headerPanel = LogcatHeaderPanel(project, deviceContext)
  private var logcatReader: LogcatReader? = null
  private val toolbar = ActionManager.getInstance().createActionToolbar("LogcatMainPanel", createToolbarActions(project), false)
  private var ignoreCaretAtBottom = false // Derived from similar code in ConsoleViewImpl. See initScrollToEndStateHandling()

  init {
    editor.installPopupHandler(object : ContextMenuPopupHandler() {
      override fun getActionGroup(event: EditorMouseEvent): ActionGroup = popupActionGroup
    })

    toolbar.setTargetComponent(this)

    // TODO(aalbert): Ideally, we would like to be able to select the connected device and client in the header from the `state` but this
    //  might be challenging both technically and from a UX perspective. Since, when restoring the state, the device/client might not be
    //  available.
    //  From a UX perspective, it's not clear what we should do in this case.
    //  From a technical standpoint, the current implementation that uses DevicePanel doesn't seem to be well suited for preselecting a
    //  device/client.
    addToTop(headerPanel)
    addToLeft(toolbar.component)
    addToCenter(editor.component)

    deviceContext.addListener(object : DeviceConnectionListener() {
      override fun onDeviceConnected(device: IDevice) {
        logcatReader?.let {
          Disposer.dispose(it)
        }
        logcatReader = LogcatReader(device, this@LogcatMainPanel, this@LogcatMainPanel::processMessages).also(LogcatReader::start)
      }

      override fun onDeviceDisconnected(device: IDevice) {
        logcatReader?.let {
          Disposer.dispose(it)
        }
        logcatReader = null
      }
    }, this)

    initScrollToEndStateHandling()
  }

  /**
   * Derived from similar code in ConsoleViewImpl.
   *
   * The purpose of this code is to 'not scroll to end' when the caret is at the end **but** the user has scrolled away from the bottom of
   * the file.
   *
   * aalbert: In theory, it seems like it should be possible to determine the state of the scroll bar directly and see if it's at the
   * bottom, but when I attempted that, it did not quite work. The code in `isScrollAtBottom()` doesn't always return the expected result.
   *
   * Specifically, on the second batch of text appended to the document, the expression "`scrollBar.maximum - scrollBar.visibleAmount`" is
   * equal to "`position + <some small number>`" rather than to "`position`" exactly.
   */
  private fun initScrollToEndStateHandling() {
    val mouseListener: MouseAdapter = object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        updateScrollToEndState(true)
      }

      override fun mouseDragged(e: MouseEvent) {
        updateScrollToEndState(false)
      }

      override fun mouseWheelMoved(e: MouseWheelEvent) {
        if (e.isShiftDown) return  // ignore horizontal scrolling
        updateScrollToEndState(false)
      }
    }
    val scrollPane = editor.scrollPane
    scrollPane.addMouseWheelListener(mouseListener)
    scrollPane.verticalScrollBar.addMouseListener(mouseListener)
    scrollPane.verticalScrollBar.addMouseMotionListener(mouseListener)
  }

  private suspend fun processMessages(messages: List<LogCatMessage>) {
    messageBacklog.addAll(messages)
    messageProcessor.appendMessages(messages)
  }

  override fun getState(): String = LogcatPanelConfig.toJson(
    LogcatPanelConfig(
      deviceContext.selectedDevice?.serialNumber,
      deviceContext.selectedClient?.clientData?.packageName,
      formattingOptions))

  private suspend fun appendToDocument(buffer: TextAccumulator) = withContext(uiThread(ModalityState.any())) {
    if (!isActive) {
      return@withContext
    }
    // Derived from similar code in ConsoleViewImpl. See initScrollToEndStateHandling()
    val shouldStickToEnd = !ignoreCaretAtBottom && editor.isCaretAtBottom()
    ignoreCaretAtBottom = false // The 'ignore' only needs to last for one update. Next time, isCaretAtBottom() will be false.
    documentAppender.appendToDocument(buffer)

    if (shouldStickToEnd) {
      scrollToEnd()
    }
  }

  override fun dispose() {
    EditorFactory.getInstance().releaseEditor(editor)
  }

  @UiThread
  fun refreshDocument() {
    editor.document.setText("")
    AndroidCoroutineScope(this, AndroidDispatchers.workerThread).launch {
      messageProcessor.appendMessages(messageBacklog.messages)
    }
  }

  private fun createToolbarActions(project: Project): ActionGroup {
    return SimpleActionGroup().apply {
      add(ScrollToTheEndToolbarAction(editor).apply {
        val text = LogcatBundle.message("logcat.scroll.to.end.text")
        templatePresentation.text = StringUtil.toTitleCase(text)
        templatePresentation.description = text
      })
      add(HeaderFormatOptionsAction(project, formattingOptions, this@LogcatMainPanel::refreshDocument))
    }
  }

  // Derived from similar code in ConsoleViewImpl. See initScrollToEndStateHandling()
  @UiThread
  private fun updateScrollToEndState(useImmediatePosition: Boolean) {
    val scrollAtBottom = editor.isScrollAtBottom(useImmediatePosition)
    val caretAtBottom = editor.isCaretAtBottom()
    if (!scrollAtBottom && caretAtBottom) {
      ignoreCaretAtBottom = true
    }
  }

  private fun scrollToEnd() {
    EditorUtil.scrollToTheEnd(editor, true)
    ignoreCaretAtBottom = false
  }

  companion object {
    /**
     * This code is based on [com.intellij.execution.impl.ConsoleViewImpl]
     */
    fun createEditor(project: Project): EditorEx {
      val editorFactory = EditorFactory.getInstance()
      val document = (editorFactory as EditorFactoryImpl).createDocument(true)
      UndoUtil.disableUndoFor(document)
      val editor = editorFactory.createViewer(document, project, EditorKind.CONSOLE) as EditorEx
      editor.document.setCyclicBufferSize(ConsoleBuffer.getCycleBufferSize())
      val editorSettings = editor.settings
      editorSettings.isAllowSingleLogicalLineFolding = true
      editorSettings.isLineMarkerAreaShown = false
      editorSettings.isIndentGuidesShown = false
      editorSettings.isLineNumbersShown = false
      editorSettings.isFoldingOutlineShown = true
      editorSettings.isAdditionalPageAtBottom = false
      editorSettings.additionalColumnsCount = 0
      editorSettings.additionalLinesCount = 0
      editorSettings.isRightMarginShown = false
      editorSettings.isCaretRowShown = false
      editorSettings.isShowingSpecialChars = false
      editor.gutterComponentEx.isPaintBackground = false

      return editor
    }
  }
}

@VisibleForTesting
@UiThread
internal fun EditorEx.isCaretAtBottom() = document.let {
  it.getLineNumber(caretModel.offset) >= it.lineCount - 1
}

@UiThread
private fun EditorEx.isScrollAtBottom(useImmediatePosition: Boolean): Boolean {
  val scrollBar = scrollPane.verticalScrollBar
  val position = if (useImmediatePosition) scrollBar.value else scrollingModel.visibleAreaOnScrollingFinished.y
  return scrollBar.maximum - scrollBar.visibleAmount == position
}
