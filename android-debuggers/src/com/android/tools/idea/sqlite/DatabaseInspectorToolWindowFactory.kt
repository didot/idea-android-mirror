/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.sqlite

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.jetbrains.ide.PooledThreadExecutor

class DatabaseInspectorToolWindowFactory : DumbAware, ToolWindowFactory {
  companion object {
    const val TOOL_WINDOW_ID = "Database Inspector"
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    DatabaseInspectorClient.startListeningForPipelineConnections(
      DatabaseInspectorProjectService.getInstance(project),
      PooledThreadExecutor.INSTANCE
    )


    val toolWindowContent = toolWindow.contentManager.factory.createContent(
      DatabaseInspectorProjectService.getInstance(project).sqliteInspectorComponent,
      "",
      true
    )
    toolWindow.contentManager.addContent(toolWindowContent)
  }
}