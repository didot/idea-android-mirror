/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions;

import com.android.tools.adtui.workbench.WorkBenchManager;
import com.intellij.ide.actions.StoreDefaultLayoutAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;

/**
 * Stores the layout of tool windows.
 */
public class StoreDefaultWindowLayoutAction extends AnAction implements DumbAware {
  private final StoreDefaultLayoutAction myDelegate;

  public StoreDefaultWindowLayoutAction() {
    super("Store Current Layout as Default");
    myDelegate = new StoreDefaultLayoutAction();
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    myDelegate.actionPerformed(event);

    WorkBenchManager workBenchManager = WorkBenchManager.getInstance();
    workBenchManager.storeDefaultLayout();
  }

  @Override
  public void update(AnActionEvent event){
    myDelegate.update(event);
  }
}
