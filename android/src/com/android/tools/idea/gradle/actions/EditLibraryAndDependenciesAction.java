/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.structure.editors.AndroidProjectSettingsService;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * Action that allows users to edit libraries and dependencies for the selected module, if the module is a Gradle module.
 */
public class EditLibraryAndDependenciesAction extends AbstractProjectStructureAction {
  public EditLibraryAndDependenciesAction() {
    super("Edit Libraries and Dependencies...");
  }

  @Override
  protected Module getTargetModule(@NotNull AnActionEvent e) {
    return getSelectedGradleModule(e);
  }

  @Override
  protected void doPerform(@NotNull Module module, @NotNull AndroidProjectSettingsService projectStructureService, @NotNull AnActionEvent e) {
    projectStructureService.openAndSelectDependenciesEditor(module);
  }
}
