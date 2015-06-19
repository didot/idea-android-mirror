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
package org.jetbrains.android.run;

import com.android.ddmlib.*;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.intellij.execution.process.ProcessOutputTypes.STDERR;
import static com.intellij.execution.process.ProcessOutputTypes.STDOUT;

/**
 * A base class for application launchers that starts an activity (rather then e.g. a test).
 * Subclasses should fill in the logic for determining which activity.
 */
public class AndroidActivityLauncher extends AndroidApplicationLauncher {
  private static final Logger LOG = Logger.getInstance(AndroidActivityLauncher.class);

  @NotNull private final AndroidFacet myFacet;
  private final boolean myNeedsLaunch;
  @NotNull private final ActivityLocator myActivityLocator;

  public AndroidActivityLauncher(@NotNull AndroidFacet facet, boolean needsLaunch, @NotNull ActivityLocator locator) {
    myFacet = facet;
    myNeedsLaunch = needsLaunch;
    myActivityLocator = locator;
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    try {
      myActivityLocator.validate(myFacet);
    }
    catch (ActivityLocator.ActivityLocatorException e) {
      throw new RuntimeConfigurationException(e.getMessage());
    }
  }

  @NotNull
  private String getQualifiedActivityName(@NotNull final AndroidFacet facet) throws ActivityLocator.ActivityLocatorException {
    final String activityName = myActivityLocator.getActivityName();
    // Return the qualified activity name if possible.
    final String activityRuntimeQName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        final GlobalSearchScope scope = facet.getModule().getModuleWithDependenciesAndLibrariesScope(false);
        final PsiClass activityClass = JavaPsiFacade.getInstance(facet.getModule().getProject()).findClass(activityName, scope);

        if (activityClass != null) {
          return JavaExecutionUtil.getRuntimeQualifiedName(activityClass);
        }
        return null;
      }
    });
    if (activityRuntimeQName != null) {
      return activityRuntimeQName;
    }

    return activityName;
  }

  @Override
  public boolean isReadyForDebugging(@NotNull ClientData data, @Nullable ProcessHandler processHandler) {
    ClientData.DebuggerStatus status = data.getDebuggerConnectionStatus();
    switch (status) {
      case ERROR:
        if (processHandler != null) {
          processHandler.notifyTextAvailable("Debug port is busy\n", STDOUT);
        }
        LOG.info("Debug port is busy");
        return false;
      case ATTACHED:
        if (processHandler != null) {
          processHandler.notifyTextAvailable("Debugger already attached\n", STDOUT);
        }
        LOG.info("Debugger already attached");
        return false;
      case WAITING:
        return true;
      case DEFAULT:
      default:
        String msg = "Client not ready yet.";
        if (processHandler != null) {
          processHandler.notifyTextAvailable(msg + "\n", STDOUT);
        }
        LOG.info(msg);
        return false;
    }
  }

  @Override
  public LaunchResult launch(@NotNull AndroidRunningState state, @NotNull IDevice device)
    throws IOException, AdbCommandRejectedException, TimeoutException {
    if (!myNeedsLaunch) {
      return LaunchResult.NOTHING_TO_DO;
    }

    ProcessHandler processHandler = state.getProcessHandler();
    String activityName = null;
    try {
      activityName = getQualifiedActivityName(state.getFacet());
    }
    catch (ActivityLocator.ActivityLocatorException e) {
      processHandler.notifyTextAvailable("Could not identify launch activity: " + e.getMessage(), STDOUT);
      return LaunchResult.NOTHING_TO_DO;
    }
    activityName = activityName.replace("$", "\\$");
    final String activityPath = state.getPackageName() + '/' + activityName;
    if (state.isStopped()) return LaunchResult.STOP;
    processHandler.notifyTextAvailable("Launching application: " + activityPath + ".\n", STDOUT);
    AndroidRunningState.MyReceiver receiver = state.new MyReceiver();
    while (true) {
      if (state.isStopped()) return LaunchResult.STOP;
      String command = "am start " +
                       getDebugFlags(state) +
                       " -n \"" + activityPath + "\" " +
                       "-a android.intent.action.MAIN " +
                       "-c android.intent.category.LAUNCHER";
      boolean deviceNotResponding = false;
      try {
        state.executeDeviceCommandAndWriteToConsole(device, command, receiver);
      }
      catch (ShellCommandUnresponsiveException e) {
        LOG.info(e);
        deviceNotResponding = true;
      }
      if (!deviceNotResponding && receiver.getErrorType() != 2) {
        break;
      }
      processHandler.notifyTextAvailable("Device is not ready. Waiting for " + AndroidRunningState.WAITING_TIME + " sec.\n", STDOUT);
      synchronized (state.getRunningLock()) {
        try {
          state.getRunningLock().wait(AndroidRunningState.WAITING_TIME * 1000);
        }
        catch (InterruptedException e) {
        }
      }
      receiver = state.new MyReceiver();
    }
    boolean success = receiver.getErrorType() == AndroidRunningState.NO_ERROR;
    if (success) {
      processHandler.notifyTextAvailable(receiver.getOutput().toString(), STDOUT);
    }
    else {
      processHandler.notifyTextAvailable(receiver.getOutput().toString(), STDERR);
    }
    return success ? LaunchResult.SUCCESS : LaunchResult.STOP;
  }

  /** Returns the flags used to the "am start" command for launching in debug mode. */
  @NotNull
  protected String getDebugFlags(@NotNull AndroidRunningState state) {
    return state.isDebugMode() ? "-D" : "";
  }
}
