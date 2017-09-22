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

import com.android.annotations.Nullable;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.ng.GradleModuleModels;
import com.android.tools.idea.gradle.project.sync.setup.module.android.*;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public class AndroidModuleSetup {
  @NotNull private final AndroidModuleSetupStep[] mySetupSteps;

  public AndroidModuleSetup() {
    this(new AndroidFacetModuleSetupStep(), new SdkModuleSetupStep(), new JdkModuleSetupStep(), new ContentRootsModuleSetupStep(),
         new DependenciesAndroidModuleSetupStep(), new CompilerOutputModuleSetupStep());
  }

  public AndroidModuleSetup(@NotNull AndroidModuleSetupStep... setupSteps) {
    mySetupSteps = setupSteps;
  }

  public void setUpModule(@NotNull Module module,
                          @NotNull IdeModifiableModelsProvider ideModelsProvider,
                          @Nullable AndroidModuleModel androidModel,
                          @Nullable GradleModuleModels models,
                          @Nullable ProgressIndicator indicator,
                          boolean syncSkipped) {
    for (AndroidModuleSetupStep step : mySetupSteps) {
      if (syncSkipped && !step.invokeOnSkippedSync()) {
        continue;
      }
      step.setUpModule(module, ideModelsProvider, androidModel, models, indicator);
    }
  }

  @TestOnly
  @NotNull
  public AndroidModuleSetupStep[] getSetupSteps() {
    return mySetupSteps;
  }
}
