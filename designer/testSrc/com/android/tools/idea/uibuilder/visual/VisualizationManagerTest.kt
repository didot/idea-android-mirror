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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import com.intellij.testFramework.EdtRule
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(JUnit4::class)
class VisualizationManagerTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @Before
  fun setupToolWindowManager() {
    // The HeadlessToolWindowManager doesn't record the status of ToolWindow. We create a simple one to record it.
    projectRule.replaceProjectService(ToolWindowManager::class.java, MyToolWindowManager(projectRule.project))
  }

  @Test
  fun testToolWindowIsRegisteredAfterInit() {
    val manager = projectRule.project.getService(VisualizationManager::class.java)
    assertNull(ToolWindowManager.getInstance(projectRule.project).getToolWindow(manager.toolWindowId))

    UIUtil.invokeAndWaitIfNeeded(Runnable { manager.initToolWindow() })
    assertNotNull(ToolWindowManager.getInstance(projectRule.project).getToolWindow(manager.toolWindowId))
  }

  @Test
  fun testAvailableWithLayoutFile() {
    // We make it visible, so when focusing layout file it would be opened automatically.
    VisualizationToolSettings.getInstance().globalState.isVisible = true

    val project = projectRule.project
    val manager = projectRule.project.getService(VisualizationManager::class.java)
    val layoutFile = projectRule.fixture.addFileToProject("res/layout/my_layout.xml", LAYOUT_FILE_TEXT)
    val ktFile = projectRule.fixture.addFileToProject("src/my_test_project/SomeFile.kt", KT_FILE_TEXT)

    // Handle post activity case.
    manager.toolWindowUpdateQueue?.waitForAllExecuted(10, TimeUnit.SECONDS)
    // Not visible when there is no editor.
    assertFalse(manager.isToolWindowAvailable)


    WriteCommandAction.runWriteCommandAction(projectRule.project) { projectRule.fixture.openFileInEditor(layoutFile.virtualFile) }
    manager.toolWindowUpdateQueue?.waitForAllExecuted(10, TimeUnit.SECONDS)
    assertTrue(manager.isToolWindowAvailable)


    WriteCommandAction.runWriteCommandAction(projectRule.project) { projectRule.fixture.openFileInEditor(ktFile.virtualFile) }
    manager.toolWindowUpdateQueue?.waitForAllExecuted(10, TimeUnit.SECONDS)
    assertFalse(manager.isToolWindowAvailable)


    WriteCommandAction.runWriteCommandAction(projectRule.project) { projectRule.fixture.openFileInEditor(layoutFile.virtualFile) }
    manager.toolWindowUpdateQueue?.waitForAllExecuted(10, TimeUnit.SECONDS)
    assertTrue(manager.isToolWindowAvailable)


    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      FileEditorManager.getInstance(project).closeFile(ktFile.virtualFile)
      FileEditorManager.getInstance(project).closeFile(layoutFile.virtualFile)
    }
    manager.toolWindowUpdateQueue?.waitForAllExecuted(10, TimeUnit.SECONDS)
    assertFalse(manager.isToolWindowAvailable)
  }
}

@Language("kotlin")
private const val KT_FILE_TEXT = """
package my_test_project
object SomeFile
"""

@Language("xml")
private const val LAYOUT_FILE_TEXT = """<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="match_parent"
  android:layout_height="match_parent" />"""

private class MyToolWindowManager(private val project: Project) : ToolWindowHeadlessManagerImpl(project) {
  private val toolWindows = mutableMapOf<String, ToolWindow>()

  override fun registerToolWindow(task: RegisterToolWindowTask): ToolWindow {
    val toolWindow = MyMockToolWindow(project)
    toolWindows[task.id] = toolWindow
    fireStateChange()
    return toolWindow
  }

  override fun getToolWindow(id: String?): ToolWindow? {
    return toolWindows[id]
  }

  private fun fireStateChange() {
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged(this)
  }
}

private class MyMockToolWindow(project: Project) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
  private var _isAvailable = false

  override fun setAvailable(available: Boolean, runnable: Runnable?) {
    _isAvailable = available
  }

  override fun setAvailable(value: Boolean) {
    _isAvailable = value
  }

  override fun isAvailable() = _isAvailable
}
