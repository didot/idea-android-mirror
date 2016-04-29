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
package com.android.tools.idea.run.util;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProcessHandlerLaunchStatus implements LaunchStatus {
  @NotNull private ProcessHandler myHandler;

  /**
   * Indicates whether the process has been terminated or is in the process of termination.
   * Ideally, we'd rely solely on the Process Handler's termination status, but it turns out that calls to terminate a non-started
   * process to terminate never have any effect until after the process is started.
   */
  private boolean myTerminated;

  public ProcessHandlerLaunchStatus(@NotNull ProcessHandler handler) {
    myHandler = handler;
  }

  @NotNull
  public ProcessHandler getProcessHandler() {
    return myHandler;
  }

  public void setProcessHandler(@NotNull ProcessHandler handler) {
    myHandler = handler;
  }

  @Override
  public boolean isLaunchTerminated() {
    return myTerminated || myHandler.isProcessTerminated() || myHandler.isProcessTerminating();
  }

  @Override
  public void terminateLaunch(@Nullable String reason) {
    myTerminated = true;
    myHandler.notifyTextAvailable(reason + "\n", ProcessOutputTypes.STDERR);
    myHandler.destroyProcess();
  }
}
