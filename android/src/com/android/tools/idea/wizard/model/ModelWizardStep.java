/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.wizard.model;

import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.expressions.bool.BooleanExpressions;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * A step is a single page in a wizard. It is responsible for creating a single pane of UI to
 * present to the user, determining if the information on the page is valid in order to progress,
 * and storing the user's data in the target model.
 */
public abstract class ModelWizardStep<M extends WizardModel> {

  @NotNull private M myModel;

  protected ModelWizardStep(@NotNull M model) {
    myModel = model;
  }

  @NotNull
  protected M getModel() {
    return myModel;
  }

  /**
   * When this step is added to a wizard, it is given a chance to add subsequent steps that it is
   * willing to take responsibility for. This is useful for steps which contain toggles that
   * enable/disable following steps.
   */
  @NotNull
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    return ImmutableList.of();
  }

  /**
   * Returns {@code true} to indicate the that this step should be shown, or {@code false} if it
   * should be skipped.
   */
  protected boolean shouldShow() {
    return true;
  }

  /**
   * Returns an observable boolean, which when set to {@code true} means the current step is
   * complete and the user can move onto the next step.
   * <p/>
   * The return type is observable so as soon as you switch the value from {@code false} to
   * {@code true}, UI can automatically be notified through a binding.
   */
  @NotNull
  protected ObservableBool canProceed() {
    return BooleanExpressions.alwaysTrue();
  }

  /**
   * Called when the step is first entered, before it is shown. This method is not called when
   * returning back to a step.
   */
  protected void onEnter() {
  }
}
