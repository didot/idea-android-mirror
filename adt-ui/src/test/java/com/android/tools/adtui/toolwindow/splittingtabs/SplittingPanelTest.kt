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

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.toolwindow.splittingtabs.SplitOrientation.HORIZONTAL
import com.android.tools.adtui.toolwindow.splittingtabs.SplitOrientation.VERTICAL
import com.android.tools.adtui.toolwindow.splittingtabs.SplittingPanelTest.TreeNode.Leaf
import com.android.tools.adtui.toolwindow.splittingtabs.SplittingPanelTest.TreeNode.Parent
import com.android.tools.adtui.toolwindow.splittingtabs.actions.SplitAction
import com.android.tools.adtui.toolwindow.splittingtabs.state.SplittingTabsStateProvider
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.content.Content
import org.junit.Rule
import org.junit.Test
import java.awt.Component
import java.awt.Dimension
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Tests for [SplittingPanel] and a few for [SplitAction] which require elaborate setup.
 */
@RunsInEdt
class SplittingPanelTest {
  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  val edtRule = EdtRule()

  private val contentManager by lazy { ToolWindowHeadlessManagerImpl.MockToolWindow(projectRule.project).contentManager }

  // The mock content manager doesn't assign a parent to the content component so we need to provide one
  private val contentRootPanel = JPanel().also { it.size = Dimension(100, 100) }

  private val fakeUi = FakeUi(contentRootPanel, createFakeWindow = true)

  @Test
  fun init_addsComponent() {
    val component = JLabel("Component")

    val splittingPanel = SplittingPanel(contentManager.factory.createContent(null, "Tab", false)) { component }

    assertThat(splittingPanel.component).isSameAs(component)
  }

  @Test
  fun split_selectsContent() {
    val content1 = contentManager.factory.createContent(null, "Tab1", false)
    val content2 = createSplittingPanelContent(contentRootPanel, ::JPanel)
    contentManager.setSelectedContent(content1)

    fakeUi.getComponent<SplittingPanel>().split(VERTICAL)

    assertThat(contentManager.selectedContent).isSameAs(content2)
  }

  @Test
  fun split() {
    val count = AtomicInteger(0)
    createSplittingPanelContent(contentRootPanel) { JLabel("${count.incrementAndGet()}") }

    split(SplitCommand("1", VERTICAL), SplitCommand("1", HORIZONTAL), SplitCommand("2", HORIZONTAL))

    assertThat(buildTree(contentRootPanel)).isEqualTo(
      Parent(VERTICAL,
             Parent(HORIZONTAL, Leaf("1"), Leaf("3")),
             Parent(HORIZONTAL, Leaf("2"), Leaf("4"))))
  }

  @Test
  fun close_1() {
    val count = AtomicInteger(0)
    createSplittingPanelContent(contentRootPanel) { DisposableLabel("${count.incrementAndGet()}") }
    split(SplitCommand("1", VERTICAL), SplitCommand("1", HORIZONTAL), SplitCommand("2", HORIZONTAL))
    val splittingPanel = fakeUi.getComponent<SplittingPanel> { it.isNamed("1") }

    splittingPanel.close()

    assertThat(buildTree(contentRootPanel)).isEqualTo(
      Parent(VERTICAL,
             Leaf("3"),
             Parent(HORIZONTAL, Leaf("2"), Leaf("4"))))
    assertThat((splittingPanel.component as DisposableLabel).isDisposed).isTrue()
  }

  @Test
  fun close_2() {
    val count = AtomicInteger(0)
    createSplittingPanelContent(contentRootPanel) { DisposableLabel("${count.incrementAndGet()}") }
    split(SplitCommand("1", VERTICAL), SplitCommand("1", HORIZONTAL), SplitCommand("2", HORIZONTAL))
    val splittingPanel = fakeUi.getComponent<SplittingPanel> { it.isNamed("2") }

    splittingPanel.close()

    assertThat(buildTree(contentRootPanel)).isEqualTo(
      Parent(VERTICAL,
             Parent(HORIZONTAL, Leaf("1"), Leaf("3")),
             Leaf("4")))
    assertThat((splittingPanel.component as DisposableLabel).isDisposed).isTrue()
  }

  @Test
  fun close_3() {
    val count = AtomicInteger(0)
    createSplittingPanelContent(contentRootPanel) { DisposableLabel("${count.incrementAndGet()}") }
    split(SplitCommand("1", VERTICAL), SplitCommand("1", HORIZONTAL), SplitCommand("2", HORIZONTAL))
    val splittingPanel = fakeUi.getComponent<SplittingPanel> { it.isNamed("3") }

    splittingPanel.close()

    assertThat(buildTree(contentRootPanel)).isEqualTo(
      Parent(VERTICAL,
             Leaf("1"),
             Parent(HORIZONTAL, Leaf("2"), Leaf("4"))))
    assertThat((splittingPanel.component as DisposableLabel).isDisposed).isTrue()
  }

  @Test
  fun close_4() {
    val count = AtomicInteger(0)
    createSplittingPanelContent(contentRootPanel) { DisposableLabel("${count.incrementAndGet()}") }
    split(SplitCommand("1", VERTICAL), SplitCommand("1", HORIZONTAL), SplitCommand("2", HORIZONTAL))
    val splittingPanel = fakeUi.getComponent<SplittingPanel> { it.isNamed("4") }

    splittingPanel.close()

    assertThat(buildTree(contentRootPanel)).isEqualTo(
      Parent(VERTICAL,
             Parent(HORIZONTAL, Leaf("1"), Leaf("3")),
             Leaf("2")))
    assertThat((splittingPanel.component as DisposableLabel).isDisposed).isTrue()
  }

  @Test
  fun disposeContent_disposesSplits() {
    val count = AtomicInteger(0)
    val content = createSplittingPanelContent(contentRootPanel) { DisposableLabel("${count.incrementAndGet()}") }
    split(SplitCommand("1", VERTICAL), SplitCommand("1", HORIZONTAL), SplitCommand("2", HORIZONTAL))
    val disposableLabels = fakeUi.findAllComponents<DisposableLabel>()

    Disposer.dispose(content)

    assertThat(disposableLabels.count { !it.isDisposed }).isEqualTo(0)
  }

  @Test
  fun closeAll_removesContent() {
    val count = AtomicInteger(0)
    createSplittingPanelContent(contentRootPanel) { DisposableLabel("${count.incrementAndGet()}") }
    split(SplitCommand("1", VERTICAL), SplitCommand("1", HORIZONTAL), SplitCommand("2", HORIZONTAL))

    while (true) {
      (fakeUi.findComponent<SplittingPanel>() ?: break).close()
    }

    assertThat(contentManager.contents).isEmpty()
    assertThat(contentRootPanel.componentCount).isEqualTo(0)
  }

  @Test
  fun findFirstSplitter_noSplits() {
    val count = AtomicInteger(0)
    val content = createSplittingPanelContent(contentRootPanel) { DisposableLabel("${count.incrementAndGet()}") }

    assertThat(content.findFirstSplitter()).isSameAs(fakeUi.getComponent<SplittingPanel> { it.isNamed("1") })
  }

  @Test
  fun findFirstSplitter_withSplits() {
    val count = AtomicInteger(0)
    val content = createSplittingPanelContent(contentRootPanel) { DisposableLabel("${count.incrementAndGet()}") }
    split(SplitCommand("1", VERTICAL), SplitCommand("1", HORIZONTAL), SplitCommand("2", HORIZONTAL))

    assertThat(content.findFirstSplitter()).isSameAs(fakeUi.getComponent<SplittingPanel> { it.isNamed("1") })
  }

  @Test
  fun findFirstSplitter_noSplitters() {
    val content = contentManager.factory.createContent(JPanel(), "Tab", /* isLockable= */ false)

    assertThat(content.findFirstSplitter()).isNull()
  }

  @Test
  fun getState_stateProvider() {
    createSplittingPanelContent(contentRootPanel) { StateProvidingComponent("State") }

    assertThat(fakeUi.getComponent<SplittingPanel>().getState()).isEqualTo("State")
  }

  @Test
  fun getState_notStateProvider() {
    createSplittingPanelContent(contentRootPanel, ::JPanel)

    assertThat(fakeUi.getComponent<SplittingPanel>().getState()).isNull()
  }

  @Test
  fun splitAction_vertical() {
    val count = AtomicInteger(0)
    val content = createSplittingPanelContent(contentRootPanel) { JLabel("${count.incrementAndGet()}") }

    SplitAction.Vertical().actionPerformed(content)

    assertThat(buildTree(contentRootPanel)).isEqualTo(Parent(VERTICAL, Leaf("1"), Leaf("2")))
  }

  @Test
  fun splitAction_horizontal() {
    val count = AtomicInteger(0)
    val content = createSplittingPanelContent(contentRootPanel) { JLabel("${count.incrementAndGet()}") }

    SplitAction.Horizontal().actionPerformed(content)

    assertThat(buildTree(contentRootPanel)).isEqualTo(Parent(HORIZONTAL, Leaf("1"), Leaf("2")))
  }

  @Test
  fun splitAction_noSplitter_doesNotCrash() {
    val content = contentManager.factory.createContent(JPanel(), "Tab", /* isLockable= */ false)

    SplitAction.Horizontal().actionPerformed(content)
  }

  private fun SplittingPanel.isNamed(name: String): Boolean = (component as JLabel).text == name

  private fun createSplittingPanelContent(contentRootPanel: JPanel, createChildComponent: () -> JComponent): Content {
    val content = contentManager.factory.createContent(/* component= */ null, "Tab", /* isLockable= */ false)
    val splittingPanel = SplittingPanel(content, createChildComponent)
    content.component = splittingPanel
    contentManager.addContent(content)
    contentRootPanel.add(splittingPanel) // The mock ContentManager doesn't assign a parent.
    splittingPanel.size = splittingPanel.parent.size

    return content
  }

  private fun split(vararg splitCommands: SplitCommand) {
    for (command in splitCommands) {
      fakeUi.getComponent<SplittingPanel> { it.isNamed(command.name) }.split(command.orientation)
    }
  }

  private fun buildTree(component: Component): TreeNode {
    return when (component) {
      contentRootPanel -> buildTree(contentRootPanel.getComponent(0))
      is SplittingPanel -> Leaf((component.component as JLabel).text)
      is Splitter -> Parent(
        SplitOrientation.fromSplitter(component),
        buildTree(component.firstComponent),
        buildTree(component.secondComponent))
      else -> throw IllegalStateException("Unexpected component found: ${component::class.qualifiedName}")
    }
  }

  private abstract class TreeNode {
    abstract fun toString(indent: String): String

    data class Leaf(val name: String) : TreeNode() {
      override fun toString(): String = toString("")
      override fun toString(indent: String): String = "$indent$name"
    }

    data class Parent(val orientation: SplitOrientation, val first: TreeNode, val second: TreeNode) : TreeNode() {
      override fun toString(): String = toString("")
      override fun toString(indent: String): String =
        "$indent${orientation.name}\n${first.toString("$indent ")}\n${second.toString("$indent ")}"
    }
  }

  private data class SplitCommand(val name: String, val orientation: SplitOrientation)

  private class DisposableLabel(text: String) : JLabel(text), Disposable {
    var isDisposed: Boolean = false

    override fun dispose() {
      isDisposed = true
    }
  }

  private class StateProvidingComponent(val componentState: String) : JPanel(), SplittingTabsStateProvider {
    override fun getState(): String = componentState
  }
}