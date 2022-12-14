/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.intellij.execution.ui.ConsoleView;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AndroidExecutionState {

  @Nullable
  Collection<IDevice> getDevices();

  @Nullable
  ConsoleView getConsoleView();

  /**
   * The ID of the run configuration that produced this state. This is used to correlate existing execution states to run
   * configurations.
   */
  int getRunConfigurationId();

  /**
   * A human friendly name of the type of run configuration that produced this state.
   */
  @NotNull
  String getRunConfigurationTypeId();
}
