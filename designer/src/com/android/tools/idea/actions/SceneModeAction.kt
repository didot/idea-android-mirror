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
package com.android.tools.idea.actions

import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.SceneMode
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

/**
 * Action class to switch the [SceneMode] in a [NlDesignSurface].
 */
class SceneModeAction(private val sceneMode: SceneMode, private val designSurface: NlDesignSurface) : ToggleAction(
  sceneMode.displayName, "Show ${sceneMode.displayName} Surface", null) {
  override fun isSelected(e: AnActionEvent) = designSurface.sceneMode == sceneMode

  override fun setSelected(e: AnActionEvent, state: Boolean) = designSurface.setScreenMode(sceneMode, true)
}