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
package com.android.tools.adtui.toolwindow.splittingtabs

import com.android.tools.adtui.toolwindow.splittingtabs.SplitOrientation.HORIZONTAL
import com.android.tools.adtui.toolwindow.splittingtabs.SplitOrientation.VERTICAL
import com.android.tools.adtui.toolwindow.splittingtabs.state.SplittingTabsStateProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.content.Content
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.VisibleForTesting
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu

/**
 * A [JPanel] that can split itself in the specified [SplitOrientation].
 *
 * The split is performed by inserting a [OnePixelSplitter] between the component and its parent. The original component is set as the
 * [OnePixelSplitter.setFirstComponent] and a new SplittingPanel is assigned to [OnePixelSplitter.setSecondComponent].
 *
 * Any SplittingPanel in the hierarchy can also be closed. Closing a panel is accomplished by removing the parent OnePixelSplitter and
 * attaching the child SplittingPanel that is not being closed to the hierarchy where the parent was attached to.
 *
 * This code was inspired by `org.jetbrains.plugins.terminal.TerminalContainer`
 */
internal class SplittingPanel(private val content: Content, private val createChildComponent: () -> JComponent)
  : JPanel(), SplittingTabsStateProvider, Disposable {

  val component = createChildComponent()

  private val menu = JPopupMenu().apply {
    add(SplitMenuItem(VERTICAL))
    add(SplitMenuItem(HORIZONTAL))
    add(JMenuItem(SplittingTabsBundle.message("SplittingTabsToolWindow.close"),
                  AllIcons.Actions.Close).apply { addActionListener { close() } })
  }

  init {
    add(component)
    Disposer.register(content, this)

    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        maybeShowPopupMenu(e)
      }

      override fun mouseReleased(e: MouseEvent) {
        maybeShowPopupMenu(e)
      }

      private fun maybeShowPopupMenu(e: MouseEvent) {
        if (e.isPopupTrigger) {
          menu.show(this@SplittingPanel, e.x, e.y)
        }
      }
    })
  }

  override fun dispose() {
    if (component is Disposable) {
      component.dispose()
    }
  }

  fun split(orientation: SplitOrientation) {
    val parent = parent
    val splitter = createSplitter(orientation, this, SplittingPanel(content, createChildComponent))

    if (parent is OnePixelSplitter) {
      if (parent.firstComponent == this) {
        parent.firstComponent = splitter
      }
      else {
        parent.secondComponent = splitter
      }
    }
    else {
      parent.remove(this)
      parent.add(splitter)
      content.component = splitter
    }
    content.manager?.setSelectedContent(content)

    parent.revalidate()
  }

  // TODO(aalbert): Make this private if we are able to test close through the popup menu.
  @VisibleForTesting
  fun close() {
    val parent = parent
    if (parent is OnePixelSplitter) {
      val grandparent = parent.parent
      val other = if (parent.firstComponent == this) parent.secondComponent else parent.firstComponent
      if (grandparent is OnePixelSplitter) {
        if (grandparent.firstComponent == parent) {
          grandparent.firstComponent = other
        }
        else {
          grandparent.secondComponent = other
        }
      }
      else {
        grandparent.remove(parent)
        grandparent.add(other)
        content.component = other
      }
      dispose()
      grandparent.revalidate()
    }
    else {
      parent.remove(this)
      content.manager?.removeContent(content, true)
    }
  }

  private fun createSplitter(orientation: SplitOrientation, first: SplittingPanel, second: SplittingPanel): OnePixelSplitter {
    return OnePixelSplitter(orientation.toSplitter(), 0.5f, 0.1f, 0.9f).apply {
      firstComponent = first
      secondComponent = second
      dividerWidth = JBUI.scale(1)
      val scheme = EditorColorsManager.getInstance().globalScheme
      val color = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR)
      if (color != null) {
        divider.background = color
      }
    }
  }

  private inner class SplitMenuItem(val orientation: SplitOrientation) : JMenuItem(orientation.text, orientation.icon) {
    init {
      addActionListener {
        split(orientation)
      }
    }
  }

  override fun getState(): String? = (component as? SplittingTabsStateProvider)?.getState()

}

/**
 * Recursively traverse hierarchy until a [SplittingPanel] is found.
 */
internal fun findFirstSplitter(component: JComponent): SplittingPanel? = when (component) {
  is SplittingPanel -> component
  is OnePixelSplitter -> findFirstSplitter(component.firstComponent)
  else -> null
}