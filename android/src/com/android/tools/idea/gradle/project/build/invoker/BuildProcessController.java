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
package com.android.tools.idea.gradle.project.build.invoker;

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel.ProcessController;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

class BuildProcessController implements ProcessController {
  @NotNull private final ExternalSystemTaskId myTaskId;
  @NotNull private final BuildStopper myBuildStopper;
  @NotNull private final ProgressIndicator myProgressIndicator;

  BuildProcessController(@NotNull ExternalSystemTaskId taskId,
                         @NotNull BuildStopper buildStopper,
                         @NotNull ProgressIndicator progressIndicator) {
    myTaskId = taskId;
    myBuildStopper = buildStopper;
    myProgressIndicator = progressIndicator;
  }

  @Override
  public void stopProcess() {
    myBuildStopper.attemptToStopBuild(myTaskId, myProgressIndicator);
  }

  @Override
  public boolean isProcessStopped() {
    return !myProgressIndicator.isRunning();
  }
}
