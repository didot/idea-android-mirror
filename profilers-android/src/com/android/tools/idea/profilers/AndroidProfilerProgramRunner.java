/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.profilers;

import com.android.tools.idea.profilers.analytics.StudioFeatureTracker;
import com.android.tools.idea.run.AndroidBaseProgramRunner;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidProfilerProgramRunner extends AndroidBaseProgramRunner {
  @Override
  @NotNull
  public String getRunnerId() {
    return "AndroidProfilerProgramRunner";
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    if (!ProfileRunExecutor.EXECUTOR_ID.equals(executorId)) {
      return false;
    }

    return profile instanceof AndroidRunConfigurationBase;
  }

  @Nullable
  @Override
  protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env)
    throws ExecutionException {
    RunContentDescriptor descriptor = super.doExecute(state, env);

    ApplicationManager.getApplication().assertIsDispatchThread();

    if (descriptor != null) {
      descriptor.setActivateToolWindowWhenAdded(false);
    }

    Project project = env.getProject();
    ToolWindow window = ToolWindowManagerEx.getInstanceEx(project).getToolWindow(AndroidProfilerToolWindowFactory.ID);
    if (!window.isVisible()) {
      // First unset the last run app info, showing the tool window can trigger the profiler to start profiling using the stale info.
      // The most current run app info will be set in AndroidProfilerToolWindowLaunchTask instead.
      project.putUserData(AndroidProfilerToolWindow.LAST_RUN_APP_INFO, null);
      window.show(null);
    }

    AndroidProfilerToolWindow profilerToolWindow = AndroidProfilerToolWindowFactory.getProfilerToolWindow(project);
    if (profilerToolWindow != null) {
      // Prevents from starting profiling a pid restored by emulator snapshot or a pid that was previously alive.
      profilerToolWindow.disableAutoProfiling();

      // Early-terminate a previous ongoing session to simplify startup profiling scenarios.
      // Configuration and start of startup profiling is done while the old process/profiling session (if there is one) is still running.
      // Previously, when the old process/session eventually ends and the new session starts, the daemon can accidentally undo/end the
      // startup recording. By first ending the session here, we ensure the following sequence:
      // 1. Stops profiling the old process
      // 2. Configures startup profiling for the process to be launched
      // 3. Starts profiling the new process
      profilerToolWindow.getProfilers().getSessionsManager().endCurrentSession();
    }
    StudioFeatureTracker featureTracker = new StudioFeatureTracker(env.getProject());
    featureTracker.trackRunWithProfiling();

    return descriptor;
  }
}
