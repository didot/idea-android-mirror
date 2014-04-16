/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.actions;

import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;

public class AndroidNewModuleInGroupAction extends AndroidNewModuleAction {
  public AndroidNewModuleInGroupAction() {
    super("Module", "Adds a new module to the project", null);
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    final ModuleGroup[] moduleGroups = ModuleGroup.ARRAY_DATA_KEY.getData(e.getDataContext());
    final Module[] modules = e.getData(LangDataKeys.MODULE_CONTEXT_ARRAY);
    e.getPresentation().setVisible((moduleGroups != null && moduleGroups.length > 0) ||
                                   (modules != null && modules.length > 0));
  }
}
