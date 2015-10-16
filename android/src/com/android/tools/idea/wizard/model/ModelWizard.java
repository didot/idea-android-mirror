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
package com.android.tools.idea.wizard.model;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.core.BoolProperty;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * A wizard that owns a series of {@link ModelWizardStep}s. When finished, it iterates through its
 * steps, queries the {@link WizardModel} they're associated with, and calls their
 * {@link WizardModel#handleFinished()} method.
 * <p/>
 * In this way, users of this framework can design steps which handle the UI logic while putting
 * all non-UI business logic in a data model class.
 */
public final class ModelWizard {

  private final List<ModelWizardStep> mySteps;

  private final BindingsManager myBindings = new BindingsManager();
  private final BoolProperty myCanGoBack = new BoolValueProperty();
  private final BoolProperty myCanGoForward = new BoolValueProperty();
  private final BoolProperty myOnLastStep = new BoolValueProperty();

  private final Stack<ModelWizardStep> myPrevSteps = new Stack<ModelWizardStep>();
  @Nullable SettableFuture<Boolean> myResult;
  private int myCurrIndex = -1;

  /**
   * Construct a wizard and, for convenience, accept some initial steps. You can also call
   * {@link #addStep(ModelWizardStep)} later with additional steps. Once added, call
   * {@link #start()} to progress to the first step.
   * <p/>
   * When the wizard is finished, it will run through its steps, in order, and run
   * {@link WizardModel#handleFinished()} on each of their associated models.
   */
  public ModelWizard(@NotNull ModelWizardStep... steps) {
    mySteps = Lists.newArrayListWithExpectedSize(steps.length);
    for (ModelWizardStep step : steps) {
      addStep(step);
    }
  }

  /**
   * Boolean property which is set to {@code true} when there's a previous step we can go back to.
   * <p/>
   * The return type is an observable boolean so a UI can bind a back button to its value.
   */
  @NotNull
  public ObservableBool canGoBack() {
    return myCanGoBack;
  }

  /**
   * Boolean property which is set to {@code true} when there's a step we can move forward to.
   * Note that this can be {@code true} even on the last page - it just indicates that the current
   * step is satisfied with the information it has.
   * <p/>
   * The return type is an observable boolean so a UI can bind a back button to its value.
   */
  @NotNull
  public ObservableBool canGoForward() {
    return myCanGoForward;
  }

  /**
   * Boolean property which is set to {@code true} when the wizard is on the last step.
   * <p/>
   * The return type is an observable boolean so a UI can bind a finish button to its value.
   */
  @NotNull
  public ObservableBool onLastStep() {
    return myOnLastStep;
  }

  /**
   * Populates the wizard with an additional step. All steps must be added before {@link #start()}
   * is called.
   */
  public void addStep(@NotNull ModelWizardStep step) {
    if (hasStarted()) {
      throw new IllegalStateException("Attempting to add a step to a dialog that's already been started");
    }

    mySteps.add(step);
  }

  /**
   * Returns the currently active step.
   * <p/>
   * It is an error to call this method before the wizard has started or after it has finished.
   */
  @VisibleForTesting
  @NotNull
  ModelWizardStep getCurrentStep() {
    ensureWizardIsRunning();

    return mySteps.get(myCurrIndex);
  }

  /**
   * Starts this wizard, indicating all steps have been added and navigation can begin via
   * {@link #goForward()} and {@link #goBack()}. Once started, the wizard will be pointed at the
   * first step.
   * <p/>
   * You can only start a wizard once.
   *
   * @return a listenable future which will be set to {@code true} as soon as the wizard is
   * completed or {@code false} if it is cancelled.
   */
  public ListenableFuture<Boolean> start() {
    if (myResult != null) {
      throw new IllegalStateException("Can't call start on a wizard that was already started");
    }

    if (mySteps.isEmpty()) {
      throw new IllegalStateException("Can't call start on a wizard with no steps");
    }

    myResult = SettableFuture.create();

    goForward(); // Proceed to first step

    return myResult;
  }

  /**
   * Moves the wizard to the next page. If we're currently on the last page, then this action
   * finishes the wizard.
   * <p/>
   * It is an error to call this without first calling {@link #start()} or on a wizard that has
   * already finished.
   */
  public void goForward() {
    ensureWizardIsRunning();

    if (myCurrIndex >= 0) {
      ModelWizardStep currStep = mySteps.get(myCurrIndex);
      if (!currStep.canProceed().get()) {
        throw new IllegalStateException("Can't call goForward on wizard when the step prevents it");
      }

      myPrevSteps.add(currStep);
    }

    while (true) {
      myCurrIndex++;
      if (myCurrIndex >= mySteps.size()) {
        handleFinished(true);
        break;
      }

      ModelWizardStep step = mySteps.get(myCurrIndex);
      if (step.shouldShow()) {
        updateNavigationProperties();
        step.onEnter();
        break;
      }
    }
  }

  /**
   * Returns the wizard back to the previous page.
   * <p/>
   * It is an error to call this if there are no previous pages to return to or on a wizard that's
   * already finished.
   */
  public void goBack() {
    ensureWizardIsRunning();

    if (myPrevSteps.empty()) {
      throw new IllegalStateException("Calling back on wizard without any previous pages");
    }

    myCurrIndex = mySteps.indexOf(myPrevSteps.pop());
    updateNavigationProperties();
  }

  /**
   * Cancels the wizard, discarding all work done so far.
   * <p/>
   * It is an error to call this without first calling {@link #start()} or on a wizard that has
   * already finished.
   */
  public void cancel() {
    ensureWizardIsRunning();

    handleFinished(false);
  }

  public boolean hasStarted() {
    return myResult != null;
  }

  public boolean isFinished() {
    return myCurrIndex > mySteps.size();
  }

  private void ensureWizardIsRunning() {
    if (!hasStarted()) {
      throw new IllegalStateException("Invalid operation attempted before wizard was started");
    }

    if (isFinished()) {
      throw new IllegalStateException("Invalid operation attempted after wizard already finished");
    }
  }

  private void handleFinished(boolean success) {
    // We should only be called by code that only ran if a wizard had already started
    assert myResult != null;

    myResult.set(success);
    Set<WizardModel> seenModels = Sets.newHashSet();
    for (ModelWizardStep step : mySteps) {
      WizardModel model = step.getModel();
      if (seenModels.contains(model)) {
        continue;
      }
      seenModels.add(model);

      if (success) {
        model.handleFinished();
      }
      else {
        model.handleCancelled();
      }
    }

    myCurrIndex = mySteps.size() + 1; // Magic value indicates done. See: isFinished
    myBindings.releaseAll();
    myPrevSteps.clear();
    myCanGoBack.set(false);
    myCanGoForward.set(false);
    myOnLastStep.set(false);
  }

  /**
   * Update the navigational properties (next, prev, etc.) given the state of the current step.
   * This should only be called if you're already on a step.
   */
  private void updateNavigationProperties() {
    myCanGoBack.set(!myPrevSteps.empty());
    myOnLastStep.set(isOnLastVisibleStep());
    ModelWizardStep step = mySteps.get(myCurrIndex);
    myBindings.bind(myCanGoForward, step.canProceed());
  }

  private boolean isOnLastVisibleStep() {
    float size = mySteps.size();
    boolean currPageIsLast = true;
    for (int i = myCurrIndex + 1; i < size; i++) {
      ModelWizardStep step = mySteps.get(i);
      if (step.shouldShow()) {
        currPageIsLast = false;
        break;
      }
    }

    return currPageIsLast;
  }
}
