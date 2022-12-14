/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public interface SplitApkDeployTaskContext {
  /**
   * The application ID of the application to deploy.
   */
  @NotNull
  String getApplicationId();

  /**
   * {@code true} if this is a patch (i.e. partial) install
   */
  boolean isPatchBuild();

  /**
   * The list of APK files to deploy to the target device. If {@link #isPatchBuild()} is {@code true},
   * then the list should contain only the subset of APKs used for partial deployment.
   */
  @NotNull
  List<File> getArtifacts();

  /**
   * Notification that the deployment has completed.
   * @param project the project
   * @param device the target device
   * @param status {@code true} if the deployment was successful
   */
  void notifyInstall(@NotNull Project project, @NotNull IDevice device, boolean status);
}
