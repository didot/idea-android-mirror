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
package com.android.tools.idea.sdk.wizard;

import com.android.repository.api.*;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.sdk.install.StudioSdkInstallerUtil;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.progress.ThrottledProgressWrapper;
import com.android.tools.idea.ui.properties.core.BoolProperty;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.android.tools.idea.ui.validation.validators.FalseValidator;
import com.android.tools.idea.ui.validation.validators.TrueValidator;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * {@link ModelWizardStep} responsible for installing all selected packages before allowing the user the proceed.
 * This class extends {@link WithoutModel} since this step only acts as a middleman between the user
 * accepting the packages and an InstallTask installing the packages in the background. No model is needed since no data
 * is recorded.
 */
public final class InstallSelectedPackagesStep extends ModelWizardStep.WithoutModel {
  private final BoolProperty myInstallFailed = new BoolValueProperty();
  private final BoolProperty myInstallationFinished = new BoolValueProperty();

  private final StudioWizardStepPanel myStudioPanel;
  private final ValidatorPanel myValidatorPanel;
  private final AndroidSdkHandler mySdkHandler;

  private JPanel myContentPanel;
  private JBLabel myLabelSdkPath;
  private JBLabel myProgressOverallLabel;
  private JTextArea mySdkManagerOutput;
  private JProgressBar myProgressBar;
  private JBLabel myProgressDetailLabel;

  private List<UpdatablePackage> myInstallRequests;
  private Collection<LocalPackage> myUninstallRequests;

  // Ok to keep a reference, since the wizard is short-lived and modal.
  private final RepoManager myRepoManager;
  private com.android.repository.api.ProgressIndicator myLogger;
  private static final Object LOGGER_LOCK = new Object();
  private final BackgroundAction myBackgroundAction = new BackgroundAction();
  private final boolean myBackgroundable;

  public InstallSelectedPackagesStep(@NotNull List<UpdatablePackage> installRequests,
                                     @NotNull Collection<LocalPackage> uninstallRequests,
                                     @NotNull AndroidSdkHandler sdkHandler,
                                     boolean backgroundable) {
    super("Component Installer");
    myInstallRequests = installRequests;
    myUninstallRequests = uninstallRequests;
    myRepoManager = sdkHandler.getSdkManager(new StudioLoggerProgressIndicator(getClass()));
    myValidatorPanel = new ValidatorPanel(this, myContentPanel);
    myStudioPanel = new StudioWizardStepPanel(myValidatorPanel, "Installing Requested Components");
    myBackgroundable = backgroundable;
    mySdkHandler = sdkHandler;
  }

  @Override
  public Action getExtraAction() {
    return myBackgroundable ? myBackgroundAction : null;
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    // This will show a warning to the user once installation starts and will disable the next/finish button until installation finishes
    String finishedText = "Please wait until the installation finishes";
    myValidatorPanel.registerValidator(myInstallationFinished, new TrueValidator(Validator.Severity.INFO, finishedText));

    String installError = "Installation did not complete successfully. See the IDE log for details";
    myValidatorPanel.registerValidator(myInstallFailed, new FalseValidator(installError));

    myBackgroundAction.setWizard(wizard);
  }

  @Override
  protected void onEntering() {
    mySdkManagerOutput.setText("");
    myLabelSdkPath.setText(myRepoManager.getLocalPath().getPath());

    startSdkInstall();
  }

  @Override
  protected boolean shouldShow() {
    return !myInstallRequests.isEmpty() || !myUninstallRequests.isEmpty();
  }

  @Override
  public boolean canGoBack() {
    return false;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myInstallationFinished;
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myStudioPanel;
  }

  @Override
  public void dispose() {
    synchronized (LOGGER_LOCK) {
      // If we're backgrounded, don't cancel when the window closes; allow the operation to continue.
      if (myLogger != null && !myBackgroundAction.isBackgrounded()) {
        myLogger.cancel();
      }
    }
  }

  private void startSdkInstall() {
    CustomLogger customLogger = new CustomLogger();
    synchronized (LOGGER_LOCK) {
      myLogger = new ThrottledProgressWrapper(customLogger);
    }

    Function<List<RepoPackage>, Void> completeCallback = failures -> {
      UIUtil.invokeLaterIfNeeded(() -> {
        myProgressBar.setValue(100);
        myProgressOverallLabel.setText("");

        if (!failures.isEmpty()) {
          myInstallFailed.set(true);
          myProgressBar.setEnabled(false);
        }
        else {
          myProgressDetailLabel.setText("Done");
          checkForUpgrades(myInstallRequests);
        }
        myInstallationFinished.set(true);
      });
      return null;
    };

    InstallerFactory factory = StudioSdkInstallerUtil.createInstallerFactory(mySdkHandler);

    InstallTask task = new InstallTask(factory, mySdkHandler, StudioSettingsController.getInstance(), myLogger);
    task.setInstallRequests(myInstallRequests);
    task.setUninstallRequests(myUninstallRequests);
    task.setCompleteCallback(completeCallback);
    task.setPrepareCompleteCallback(() -> myBackgroundAction.setEnabled(false));
    ProgressIndicator indicator;
    boolean hasOpenProjects = ProjectManager.getInstance().getOpenProjects().length > 0;
    if (hasOpenProjects) {
      indicator = new BackgroundableProcessIndicator(task);
    }
    else {
      // If we don't have any open projects runProcessWithProgressAsynchronously will show a modal popup no matter what.
      // Instead use an empty progress indicator to suppress that.
      indicator = new EmptyProgressIndicator();
    }
    customLogger.setIndicator(indicator);
    myLogger.logInfo("To install:");
    for (UpdatablePackage p : myInstallRequests) {
      myLogger.logInfo(String.format("- %1$s (%2$s)", p.getRemote().getDisplayName(), p.getRemote().getPath()));
    }
    myLogger.logInfo("");
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator);
  }

  /**
   * Look through the list of completed changes, and set a key if any new platforms
   * were installed.
   */
  private static void checkForUpgrades(@Nullable List<UpdatablePackage> completedChanges) {
    if (completedChanges == null) {
      return;
    }
    int highestNewApiLevel = 0;
    for (UpdatablePackage updated : completedChanges) {
      TypeDetails details = updated.getRepresentative().getTypeDetails();
      if (details instanceof DetailsTypes.PlatformDetailsType) {
        int api = ((DetailsTypes.PlatformDetailsType)details).getApiLevel();
        if (api > highestNewApiLevel) {
          highestNewApiLevel = api;
        }
      }
    }
    if (highestNewApiLevel > 0) {
      // TODO: Fix this code after we delete WizardConstants
      PropertiesComponent.getInstance().setValue(WizardConstants.NEWLY_INSTALLED_API_KEY.name, highestNewApiLevel, -1);
    }
  }


  private final class CustomLogger implements com.android.repository.api.ProgressIndicator {

    private ProgressIndicator myIndicator;
    private boolean myCancelled;
    private Logger myLogger = Logger.getInstance(getClass());

    @Override
    public void setText(@Nullable final String s) {
      UIUtil.invokeLaterIfNeeded(() -> myProgressOverallLabel.setText(s));
      if (myIndicator != null) {
        myIndicator.setText(s);
      }
    }

    @Override
    public boolean isCanceled() {
      return myCancelled;
    }

    @Override
    public void cancel() {
      myCancelled = true;
      if (myIndicator != null) {
        myIndicator.cancel();
      }
    }

    @Override
    public void setCancellable(boolean cancellable) {
      // Nothing
    }

    @Override
    public boolean isCancellable() {
      return true;
    }

    @Override
    public void setIndeterminate(final boolean indeterminate) {
      UIUtil.invokeLaterIfNeeded(() -> myProgressBar.setIndeterminate(indeterminate));
      if (myIndicator != null) {
        myIndicator.setIndeterminate(indeterminate);
      }
    }

    @Override
    public boolean isIndeterminate() {
      return myProgressBar.isIndeterminate();
    }

    @Override
    public void setFraction(final double v) {
      UIUtil.invokeLaterIfNeeded(() -> {
        myProgressBar.setIndeterminate(false);
        myProgressBar.setValue((int)(v * (double)(myProgressBar.getMaximum() - myProgressBar.getMinimum())));
      });
      if (myIndicator != null) {
        myIndicator.setFraction(v);
      }
    }

    @Override
    public double getFraction() {
      return myProgressBar.getPercentComplete();
    }

    @Override
    public void setSecondaryText(@Nullable final String s) {
      UIUtil.invokeLaterIfNeeded(() -> myProgressDetailLabel.setText(s));
      if (myIndicator != null) {
        myIndicator.setText2(s);
      }
    }

    @Override
    public void logWarning(@NotNull String s) {
      appendText(s);
      myLogger.warn(s);
    }

    @Override
    public void logWarning(@NotNull String s, @Nullable Throwable e) {
      appendText(s);
      myLogger.warn(s, e);
    }

    @Override
    public void logError(@NotNull String s) {
      appendText(s);
      myLogger.error(s);
    }

    @Override
    public void logError(@NotNull String s, @Nullable Throwable e) {
      appendText(s);
      myLogger.error(s, e);
    }

    @Override
    public void logInfo(@NotNull String s) {
      appendText(s);
      myLogger.info(s);
    }

    private void appendText(@NotNull final String s) {
      UIUtil.invokeLaterIfNeeded(() -> {
        String current = mySdkManagerOutput.getText();
        String separator = "\n";
        if (current == null) {
          current = "";
        }
        else if (current.endsWith("\n")) {
          // Want to chew the first "extra" newline since in different places
          // the messages either end with an explicit "\n" or not, but the intention is always
          // to have one trailing newline.
          //
          // The calling code can still supply more than one newline,
          // and it will result in empty lines, since 2+ explicitly provided newlines
          // probably mean that this was the intention
          separator = "";
        }
        mySdkManagerOutput.setText(current + separator + s);
      });
    }

    public void setIndicator(ProgressIndicator indicator) {
      myIndicator = indicator;
    }
  }

  /**
   * Action shown as an extra action in the wizard (see {@link ModelWizardStep#getExtraAction()}.
   * Cancels the wizard, but lets our install task continue running.
   */
  private static class BackgroundAction extends AbstractAction {
    private boolean myIsBackgrounded = false;
    private ModelWizard.Facade myWizard;

    public BackgroundAction() {
      super("Background");
    }

    public void setWizard(@NotNull ModelWizard.Facade wizard) {
      myWizard = wizard;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myIsBackgrounded = true;
      myWizard.cancel();
    }

    public boolean isBackgrounded() {
      return myIsBackgrounded;
    }
  }
}
