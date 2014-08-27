/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.memory.actions;

import com.android.tools.idea.memory.MemorySampler;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;

public class RecordingAction extends ToggleAction {
  private MemorySampler myMemorySampler;

  public RecordingAction(MemorySampler memorySampler) {
    super(null, null, AllIcons.Debugger.Db_set_breakpoint);
    myMemorySampler = memorySampler;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myMemorySampler != null && myMemorySampler.isRunning();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    if (isSelected(e)) {
      final Presentation presentation = e.getPresentation();
      presentation.setText("Stop");
      presentation.setDescription("Stops memory information recording.");
    } else {
      final Presentation presentation = e.getPresentation();
      presentation.setText("Start");
      presentation.setDescription("Starts memory information recording.");
    }
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    if (myMemorySampler != null) {
      if (state) {
        myMemorySampler.start();
      }
      else {
        myMemorySampler.stop();
      }
    }
  }
}
