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
package com.android.tools.idea.gradle.project.sync.setup.module;

import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.ng.GradleModuleModels;
import com.android.tools.idea.gradle.project.sync.setup.module.ndk.ContentRootModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.ndk.NdkFacetModuleSetupStep;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NdkModuleSetup {
  @NotNull private final NdkModuleSetupStep[] mySetupSteps;

  public NdkModuleSetup() {
    this(new NdkFacetModuleSetupStep(), new ContentRootModuleSetupStep());
  }

  @VisibleForTesting
  NdkModuleSetup(@NotNull NdkModuleSetupStep... setupSteps) {
    mySetupSteps = setupSteps;
  }

  public void setUpModule(@NotNull Module module,
                          @NotNull IdeModifiableModelsProvider ideModelsProvider,
                          @Nullable NdkModuleModel ndkModuleModel,
                          @Nullable GradleModuleModels models,
                          @Nullable ProgressIndicator indicator,
                          boolean syncSkipped) {
    for (NdkModuleSetupStep step : mySetupSteps) {
      if (syncSkipped && !step.invokeOnSkippedSync()) {
        continue;
      }
      step.setUpModule(module, ideModelsProvider, ndkModuleModel, models, indicator);
    }
  }
}
