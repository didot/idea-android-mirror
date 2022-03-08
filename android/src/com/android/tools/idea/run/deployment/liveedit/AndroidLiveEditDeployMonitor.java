/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit;

import static com.android.tools.idea.run.deployment.liveedit.ErrorReporterKt.errorMessage;
import static com.android.tools.idea.run.deployment.liveedit.ErrorReporterKt.reportDeployerError;

import com.android.annotations.Nullable;
import com.android.annotations.Trace;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.deployer.AdbClient;
import com.android.tools.deployer.AdbInstaller;
import com.android.tools.deployer.Installer;
import com.android.tools.deployer.MetricsRecorder;
import com.android.tools.deployer.tasks.LiveUpdateDeployer;
import com.android.tools.idea.editors.literals.EditState;
import com.android.tools.idea.editors.literals.EditStatus;
import com.android.tools.idea.editors.literals.LiveEditService;
import com.android.tools.idea.editors.literals.LiveLiteralsMonitorHandler;
import com.android.tools.idea.editors.literals.LiveLiteralsService;
import com.android.tools.idea.editors.literals.EditEvent;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.editors.liveedit.LiveEditConfig;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.android.tools.idea.run.deployment.AndroidExecutionTarget;
import com.android.tools.idea.util.StudioPathManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension;

/**
 * Helper to set up Live Literal deployment monitoring.
 *
 * Since the UI / UX of this is still not fully agreed upon. This class is design to have MVP like
 * functionality just enough for compose user group to dogfood for now.
 *
  * The LiveEdit change detection & handling flow is as follows:
 * There are three thread contexts:
 * - The UI thread, which reports PSI events
 * - The LiveEditService executor, which queues changes and schedules
 *   LiveEdit pushes. Single-threaded.
 * - The AndroidLiveEditDeployMonitor executor, which handles
 *   compile/push of LiveEdit changes. Single-threaded.
 *
 * ┌──────────┐         ┌───────────┐
 * │ UI Thread├─────────┤PSIListener├─handleChangeEvent()─────────────────────────►
 * └──────────┘         └──────────┬┘
 *                                 │
 * ┌───────────────────────┐   ┌───▼────────┐
 * │LiveEditServiceExecutor├───┤EditListener├─────────────────────────────────────►
 * └───────────────────────┘   └──────┬─────┘
 *                                    │
 *                                    │                       ┌─────┐
 *                                    ├──────────────────────►│QUEUE│
 *                                    │                       └──┬──┘
 *                                    │ schedule()               │
 * ┌──────────────────────────┐       │                          │
 * │AndroidEditServiceExecutor├───────▼──────────────────────────▼──processChanges()
 * └──────────────────────────┘
 *
 * It is important that both executors owned by LiveEdit are single-threaded,
 * in order to ensure that each processes events serially without any races.
 *
 * LiveEditService registers a single PSI listener with the PsiManager.
 * This listener receives callbacks on the UI thread when PSI
 * events are generated. There is one LiveEditService instance per Project.
 *
 * AndroidLiveEditDeployMonitor registers one LiveEditService.EditListener
 * per Project with the corresponding Project's LiveEditService. When the
 * LiveEditService receives PSI events, the listener receives a callback
 * on a single-threaded application thread pool owned by the
 * LiveEditService.
 *
 * The EditListener callback enqueues the event in a collection of
 * "unhandled" events, schedules a LiveEdit compile+push, and returns
 * quickly to allow the thread pool to continue enqueuing events.
 *
 * The scheduled LiveEdit compile+push is executed on a single-threaded
 * executor owned by the EditListener. It handles changes as follows:
 * 1. Lock the queue  of unhandled changes
 * 2. Make a copy of the queue, clear the queue, then unlock the queue
 * 3. If the copy is empty, return
 * 4. Attempt to compile and push the copied changes
 * 5. If the compilation is successful, return.
 * 6. If the compilation is cancelled, lock queue, read-add the removed
 * events, then schedule another compile+push
 *
 * Compilation may be cancelled by PSI write actions, such as the user
 * continuing to type after making a change. It may also be prevented by
 * an ongoing write action, or a PSI write action from another source,
 * which is why it is safer to schedule a retry rather than assuming
 * whatever PSI modification cancelled the change will cause a LiveEdit
 * push.
 *
 * Note that this retry logic does NOT apply if the compilation explicitly
 * fails; only if it is cancelled by PSI write actions.
 *
 * Compilation is responsible for handling duplicate changes
 * originating from the same file, and performs de-duplication logic to
 * ensure that the same file is not re-compiled multiple times.
 */
public class AndroidLiveEditDeployMonitor {
  // TODO: The logging is overly excessive for now given we have no UI to provide feedback to the user
  // when things go wrong. This will be changed in the final product.
  private static final LogWrapper LOGGER = new LogWrapper(Logger.getInstance(AndroidLiveEditDeployMonitor.class));

  private static final EditStatus UPDATE_IN_PROGRESS = new EditStatus(EditState.IN_PROGRESS, "Live edit update in progress");

  private static final EditStatus DISCONNECTED = new EditStatus(EditState.PAUSED, "No apps are ready to receive live edits");

  private final @NotNull Project project;

  private @Nullable String applicationId;

  // A map of live edited files and their corresponding state information.
  private final Map<PsiFile, FunctionState> functionStateMap = new HashMap<>();

  private final ScheduledExecutorService methodChangesExecutor = Executors.newSingleThreadScheduledExecutor();

  private final @NotNull AtomicReference<EditStatus> editStatus = new AtomicReference<>(LiveEditService.DISABLED_STATUS);

  private class EditStatusGetter implements LiveEditService.EditStatusProvider {
    @NotNull
    @Override
    public EditStatus invoke() {
      if (StringUtil.isEmpty(applicationId)) {
        return LiveEditService.DISABLED_STATUS;
      }

      boolean noRunningLiveEditApp = deviceIterator(project, applicationId).noneMatch(d -> {
        for (Client client : d.getClients()) {
          if (applicationId.equals(client.getClientData().getPackageName())) {
            return true;
          }
        }
        return false;
      });
      EditStatus status = editStatus.get();
      if (noRunningLiveEditApp) {
        // Set state to paused if no devices in past deploys are present but we have edits queued.
        return status.getEditState() == EditState.DISABLED ? status : DISCONNECTED;
      }
      return status;
    }
  }

  private class EditsListener implements Disposable {
    // Care should be given when modifying this field to preserve atomicity.
    private final ConcurrentLinkedQueue<EditEvent> changedMethodQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void dispose() {
      editStatus.set(LiveEditService.DISABLED_STATUS);
      methodChangesExecutor.shutdownNow();
    }

    // This method is invoked on the listener executor thread in LiveEditService and does not block the UI thread.
    public void onLiteralsChanged(EditEvent event) {
      if (StringUtil.isEmpty(applicationId)) {
        return;
      }

      changedMethodQueue.add(event);
      methodChangesExecutor.schedule(this::processChanges, LiveEditConfig.getInstance().getRefreshRateMs(), TimeUnit.MILLISECONDS);
    }

    private void processChanges() {
      if (changedMethodQueue.isEmpty()) {
        return;
      }

      List<EditEvent> copy = new ArrayList<>();
      changedMethodQueue.removeIf(e -> {
        copy.add(e);
        return true;
      });

      editStatusChanged(editStatus.getAndUpdate(editStatus -> {
        switch (editStatus.getEditState()) {
          case PAUSED:
          case UP_TO_DATE:
          case IN_PROGRESS:
            return UPDATE_IN_PROGRESS;
          default:
            return editStatus;
        }
      }));

      if (!handleChangedMethods(project, applicationId, copy)) {
        changedMethodQueue.addAll(copy);
        methodChangesExecutor.schedule(this::processChanges, LiveEditConfig.getInstance().getRefreshRateMs(), TimeUnit.MILLISECONDS);
      }
    }
  }

  public AndroidLiveEditDeployMonitor(@NotNull LiveEditService liveEditService, @NotNull Project project) {
    this.project = project;
    EditsListener editsListener = new EditsListener();
    liveEditService.addOnEditListener(editsListener::onLiteralsChanged);
    liveEditService.addEditStatusProvider(new EditStatusGetter());
    Disposer.register(liveEditService, editsListener);
  }

  public Callable<?> getCallback(String applicationId, IDevice device) {
    String deviceId = device.getSerialNumber();

    // TODO: Don't use Live Literal's reporting
    LiveLiteralsService.getInstance(project).liveLiteralsMonitorStopped(deviceId + "#" + applicationId);

    // Live Edit will eventually replace Live Literals. They conflict with each other the only way the enable
    // one is to to disable the other.
    if (StudioFlags.COMPOSE_DEPLOY_LIVE_LITERALS.get()) {
      LOGGER.info("Live Edit disabled because %s is enabled.", StudioFlags.COMPOSE_DEPLOY_LIVE_LITERALS.getId());
      return null;
    }
    if (!StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT.get()) {
      LOGGER.info("Live Edit disabled because %s is disabled.", StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT.getId());
      return null;
    }

    if (!supportLiveEdits(device)) {
      LOGGER.info("Live edit not support for device %s targeting app %s", project.getName(), applicationId);
      return null;
    }

    LOGGER.info("Creating monitor for project %s targeting app %s", project.getName(), applicationId);

    return () -> methodChangesExecutor
      .schedule(
        () -> {
          this.applicationId = null;
          functionStateMap.clear();
          this.applicationId = applicationId;
          editStatusChanged(editStatus.getAndSet(LiveEditService.UP_TO_DATE_STATUS));

          LiveLiteralsMonitorHandler.DeviceType deviceType;
          if (device.isEmulator()) {
            deviceType = LiveLiteralsMonitorHandler.DeviceType.EMULATOR;
          }
          else {
            deviceType = LiveLiteralsMonitorHandler.DeviceType.PHYSICAL;
          }

          LiveLiteralsService.getInstance(project).liveLiteralsMonitorStarted(deviceId + "#" + applicationId, deviceType);
        },
        0L,
        TimeUnit.NANOSECONDS)
      .get();
  }

  private static void checkJetpackCompose(@NotNull Project project) {
    final List<IrGenerationExtension> pluginExtensions = IrGenerationExtension.Companion.getInstances(project);
    boolean found = false;
    for (IrGenerationExtension extension : pluginExtensions) {
      if (extension.getClass().getName().equals("com.android.tools.compose.ComposePluginIrGenerationExtension")) {
        found = true;
        break;
      }
    }

    if (!found) {
      throw LiveEditUpdateException.compilationError("Cannot find Jetpack Compose plugin in Android Studio. Is it enabled?", null);
    }
  }

  @Trace
  private boolean handleChangedMethods(Project project,
                                       String packageName,
                                       List<EditEvent> changes) {
    LOGGER.info("Change detected for project %s targeting app %s", project.getName(), packageName);

    long start = System.nanoTime();
    long compileFinish, pushFinish;

    // Perform bookkeeping of function locations.
    for (EditEvent event : changes) {
      functionStateMap.computeIfAbsent(event.getFile(), file -> new FunctionState());
      functionStateMap.get(event.getFile()).updateFunction(event);
    }

    ArrayList<AndroidLiveEditCodeGenerator.CodeGeneratorOutput> compiled = new ArrayList<>();
    LiveEditUpdateException exception = null;
    try {
      // Check that Jetpack Compose plugin is enabled otherwise inline linking will fail with
      // unclear BackendException
      checkJetpackCompose(project);
      List<AndroidLiveEditCodeGenerator.CodeGeneratorInput> inputs = changes.stream().map(
        change ->
          new AndroidLiveEditCodeGenerator.CodeGeneratorInput(change.getFile(), change.getFunction(), functionStateMap.get(change.getFile())))
        .collect(Collectors.toList());
      if (!new AndroidLiveEditCodeGenerator(project).compile(inputs, compiled)) {
        return false;
      }
    } catch (LiveEditUpdateException e) {
      // We need to do this because currently error reporting requires an AdbClient object, which we don't create until we push.
      // Once compilation error reporting does *not* require device knowledge, this should be removed.
      exception = e;
      LOGGER.error(e, "Error while compiling");
    } finally {
      compileFinish = System.nanoTime();
    }

    String deployEventKey = Integer.toString(changes.hashCode());
    try {
      pushUpdates(project, packageName, deployEventKey, compiled, exception);
    } finally {
      pushFinish = System.nanoTime();
    }

    long compileDurationMs = TimeUnit.NANOSECONDS.toMillis(compileFinish - start);
    long pushDurationMs = TimeUnit.NANOSECONDS.toMillis(pushFinish - compileFinish);
    LOGGER.info("LiveEdit completed in %dms (compile: %dms, push: %dms)", compileDurationMs + pushDurationMs, compileDurationMs,
                pushDurationMs);

    LiveEditUpdateException e = exception;
    editStatusChanged(editStatus.getAndUpdate(editStatus -> {
      if (editStatus.getEditState() == LiveEditService.DISABLED_STATUS.getEditState()) {
        return LiveEditService.DISABLED_STATUS;
      }
      if (e != null) {
        return new EditStatus(EditState.PAUSED, errorMessage(e));
      }
      return LiveEditService.UP_TO_DATE_STATUS;
    }));

    return true;
  }

  private static Stream<IDevice> deviceIterator(Project project, String packageName) {
    List<AndroidSessionInfo> sessions = AndroidSessionInfo.findActiveSession(project);
    if (sessions == null) {
      LOGGER.info("No running session found for %s", packageName);
      return Stream.empty();
    }

    return sessions
      .stream()
      .map(AndroidSessionInfo::getExecutionTarget)
      .filter(t -> t instanceof AndroidExecutionTarget)
      .flatMap(t -> ((AndroidExecutionTarget)t).getRunningDevices().stream())
      .filter(AndroidLiveEditDeployMonitor::supportLiveEdits);
  }

  private void pushUpdates(Project project,
                           String packageName,
                           String deployEventKey,
                           List<AndroidLiveEditCodeGenerator.CodeGeneratorOutput> updates,
                           LiveEditUpdateException exception) {
    deviceIterator(project, packageName)
      .map(d -> new AdbClient(d, LOGGER))
      .forEach(adb -> {
        if (exception != null) {
          onCompileFailCallBack(project, adb, packageName, deployEventKey, errorMessage(exception));
          return;
        }

        MetricsRecorder metrics = new MetricsRecorder();

        Installer installer = new AdbInstaller(getLocalInstaller(), adb, metrics.getDeployMetrics(), LOGGER, AdbInstaller.Mode.DAEMON);
        LiveUpdateDeployer deployer = new LiveUpdateDeployer();

        updates.forEach(update -> onCompileSuccessCallBack(project, adb, packageName, deployEventKey, deployer, installer, update));
    });
  }

  private static void onCompileSuccessCallBack(
    Project project, AdbClient adb, String packageName, String deployEventKey, LiveUpdateDeployer deployer, Installer installer,
    AndroidLiveEditCodeGenerator.CodeGeneratorOutput update) {
    boolean useDebugMode = LiveEditConfig.getInstance().getUseDebugMode();
    boolean usePartialRecompose = LiveEditConfig.getInstance().getUsePartialRecompose() &&
                                  update.getFunctionType() == AndroidLiveEditCodeGenerator.FunctionType.COMPOSABLE;
    LiveUpdateDeployer.UpdateLiveEditsParam param =
      new LiveUpdateDeployer.UpdateLiveEditsParam(
        update.getClassName(), update.getMethodName(), update.getMethodDesc(),
        usePartialRecompose,
        update.getOffSet().getStart(),
        update.getOffSet().getEnd(),
        update.getClassData(),
        update.getSupportClasses(), useDebugMode);

    String deviceId = adb.getSerial() + "#" + packageName;
    LiveLiteralsService.getInstance(project).liveLiteralPushStarted(deviceId, deployEventKey);

    List<LiveUpdateDeployer.UpdateLiveEditError> results = deployer.updateLiveEdit(installer, adb, packageName, param);
    for (LiveUpdateDeployer.UpdateLiveEditError result : results ) {
      reportDeployerError(result);
    }

    LiveLiteralsService.getInstance(project).liveLiteralPushed(
      deviceId, deployEventKey,results.stream().map(
        r -> new LiveLiteralsMonitorHandler.Problem(LiveLiteralsMonitorHandler.Problem.Severity.ERROR, r.msg))
        .collect(Collectors.toList()));
  }

  private static void onCompileFailCallBack(
    Project project, AdbClient adb, String packageName, String deployEventKey, String errorMessage) {
    String deviceId = adb.getSerial() + "#" + packageName;
    LiveLiteralsService.getInstance(project).liveLiteralPushStarted(deviceId, deployEventKey);
    List< LiveLiteralsMonitorHandler.Problem> e = new ArrayList<>();
    e.add(new LiveLiteralsMonitorHandler.Problem(LiveLiteralsMonitorHandler.Problem.Severity.ERROR, errorMessage));
    LiveLiteralsService.getInstance(project).liveLiteralPushed(deviceId, deployEventKey, e);
  }

  private static boolean supportLiveEdits(IDevice device) {
    return device.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.R);
  }

  // TODO: Unify this part.
  private static String getLocalInstaller() {
    Path path;
    if (StudioPathManager.isRunningFromSources()) {
      // Development mode
      path = StudioPathManager.resolvePathFromSourcesRoot("bazel-bin/tools/base/deploy/installer/android-installer");
    } else {
      path = Paths.get(PathManager.getHomePath(), "plugins/android/resources/installer");
    }
    return path.toString();
  }

  private void editStatusChanged(@NotNull EditStatus oldStatus) {
    if (editStatus.get() != oldStatus) {
      ApplicationManager.getApplication().invokeLater(ActionToolbarImpl::updateAllToolbarsImmediately);
    }
  }
}
