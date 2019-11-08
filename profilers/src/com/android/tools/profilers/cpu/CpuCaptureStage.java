/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.DefaultTimeline;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.EventModel;
import com.android.tools.adtui.model.event.LifecycleEventModel;
import com.android.tools.adtui.model.trackgroup.TrackGroupModel;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.ProfilerTrackRendererType;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisModel;
import com.android.tools.profilers.cpu.analysis.CpuFullTraceAnalysisModel;
import com.android.tools.profilers.cpu.atrace.AtraceCpuCapture;
import com.android.tools.profilers.cpu.atrace.AtraceFrame;
import com.android.tools.profilers.event.LifecycleEventDataSeries;
import com.android.tools.profilers.event.UserEventDataSeries;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class holds the models and capture data for the {@link com.android.tools.profilers.cpu.CpuCaptureStageView}.
 * This stage is set when a capture is selected from the {@link CpuProfilerStage}, or when a capture is imported.
 */
public class CpuCaptureStage extends Stage<Timeline> {
  public enum Aspect {
    /**
     * Triggered when the stage changes state from parsing to analyzing. This can also be viewed as capture parsing completed.
     * If the capture parsing fails the stage will transfer back to the {@link CpuProfilerStage}
     */
    STATE,
    /**
     * Triggered when a new analysis model is added / removed.
     */
    ANALYSIS_MODEL_UPDATED,
  }

  public enum State {
    /**
     * Initial state set when creating this stage. Parsing happens when {@link #enter} is called
     */
    PARSING,
    /**
     * When parsing has completed the state transitions to analyzing, we remain in this state while viewing data of the capture.
     */
    ANALYZING,
  }

  /**
   * Helper function to save trace data to disk. The file is put in to the users temp directory with the format cpu_trace_[traceid].trace.
   * If the file exists FileUtil will append numbers to the end making it unique.
   */
  @NotNull
  public static File saveCapture(long traceId, ByteString data) {
    try {
      File trace = FileUtil.createTempFile(String.format(Locale.US, "cpu_trace_%d", traceId), ".trace", true);
      try (FileOutputStream out = new FileOutputStream(trace)) {
        out.write(data.toByteArray());
      }
      return trace;
    }
    catch (IOException io) {
      throw new IllegalStateException("Unable to save trace to disk");
    }
  }

  @Nullable
  private static File getAndSaveCapture(@NotNull StudioProfilers profilers, long traceId) {
    Transport.BytesRequest traceRequest = Transport.BytesRequest.newBuilder()
      .setStreamId(profilers.getSession().getStreamId())
      .setId(String.valueOf(traceId))
      .build();
    Transport.BytesResponse traceResponse = profilers.getClient().getTransportClient().getBytes(traceRequest);
    if (!traceResponse.getContents().isEmpty()) {
      return saveCapture(traceId, traceResponse.getContents());
    }
    return null;
  }

  /**
   * Responsible for parsing trace files into {@link CpuCapture}.
   * Parsed captures should be obtained from this object.
   */
  private final CpuCaptureHandler myCpuCaptureHandler;
  private final AspectModel<Aspect> myAspect = new AspectModel<>();
  private final List<CpuAnalysisModel> myAnalysisModels = new ArrayList<>();
  private final List<TrackGroupModel> myTrackGroupModels = new ArrayList<>();

  private CpuCaptureMinimapModel myMinimapModel;
  private State myState = State.PARSING;

  // Accessible only when in state analyzing
  private CpuCapture myCapture;

  /**
   * The track groups share a timeline based on the minimap selection.
   * <p>
   * Data range: capture range;
   * View range: minimap selection;
   * Tooltip range: all track groups share the same mouse-over range, different from the minimap or profilers;
   * Selection range: union of all selected trace events;
   *
   */
  private final Timeline myTrackGroupTimeline = new DefaultTimeline();

  /**
   * Create a capture stage that loads a given trace id. If a trace id is not found null will be returned.
   */
  @Nullable
  public static CpuCaptureStage create(@NotNull StudioProfilers profilers, @NotNull String configurationName, long traceId) {
    File captureFile = getAndSaveCapture(profilers, traceId);
    if (captureFile == null) {
      return null;
    }
    String captureProcessNameHint = CpuProfiler.getTraceInfoFromId(profilers, traceId).getConfiguration().getAppName();
    return new CpuCaptureStage(profilers, configurationName, captureFile, captureProcessNameHint, profilers.getSession().getPid());
  }

  /**
   * Create a capture stage based on a file, this is used for both importing traces as well as cached traces loaded from trace ids.
   */
  @NotNull
  public static CpuCaptureStage create(@NotNull StudioProfilers profilers, @NotNull String configurationName, @NotNull File captureFile) {
    return new CpuCaptureStage(profilers, configurationName, captureFile, null, 0);
  }

  /**
   * Create a capture stage that loads a given file.
   */
  @VisibleForTesting
  CpuCaptureStage(@NotNull StudioProfilers profilers,
                  @NotNull String configurationName,
                  @NotNull File captureFile,
                  @Nullable String captureProcessNameHint,
                  int captureProcessIdHint) {
    super(profilers);
    myCpuCaptureHandler =
      new CpuCaptureHandler(profilers.getIdeServices(), captureFile, configurationName, captureProcessNameHint, captureProcessIdHint);
  }

  public State getState() {
    return myState;
  }

  @NotNull
  public AspectModel<Aspect> getAspect() {
    return myAspect;
  }

  @NotNull
  public CpuCaptureHandler getCaptureHandler() {
    return myCpuCaptureHandler;
  }

  @NotNull
  public List<TrackGroupModel> getTrackGroupModels() {
    return myTrackGroupModels;
  }

  @NotNull
  public CpuCaptureMinimapModel getMinimapModel() {
    assert myState == State.ANALYZING;
    return myMinimapModel;
  }

  @NotNull
  public List<CpuAnalysisModel> getAnalysisModels() {
    return myAnalysisModels;
  }

  /**
   * @return the capture timeline, different from the profiler's session timeline.
   */
  @NotNull
  public Timeline getCaptureTimeline() {
    return getCapture().getTimeline();
  }

  /**
   * @return the track group timeline specific to the capture stage.
   */
  @NotNull
  @Override
  public Timeline getTimeline() {
    return myTrackGroupTimeline;
  }

  private void setState(State state) {
    myState = state;
    myAspect.changed(Aspect.STATE);
  }

  @NotNull
  public CpuCapture getCapture() {
    assert myState == State.ANALYZING;
    return myCapture;
  }

  @Override
  public void enter() {
    getStudioProfilers().getUpdater().register(myCpuCaptureHandler);
    myCpuCaptureHandler.parse(capture -> {
      try {
        if (capture == null) {
          getStudioProfilers().getIdeServices().getMainExecutor()
            .execute(() -> getStudioProfilers().setStage(new CpuProfilerStage(getStudioProfilers())));
        }
        else {
          myCapture = capture;
          onCaptureParsed(capture);
          setState(State.ANALYZING);
        }
      }
      catch (Exception ex) {
        // Logging if an exception happens since setState may trigger various callbacks.
        Logger.getInstance(CpuCaptureStage.class).error(ex);
      }
    });
  }

  @Override
  public void exit() {
    getStudioProfilers().getUpdater().unregister(myCpuCaptureHandler);
  }

  public void addCpuAnalysisModel(@NotNull CpuAnalysisModel model) {
    myAnalysisModels.add(model);
    myAspect.changed(Aspect.ANALYSIS_MODEL_UPDATED);
  }

  private void onCaptureParsed(@NotNull CpuCapture capture) {
    myTrackGroupTimeline.getDataRange().set(capture.getRange());
    myMinimapModel = new CpuCaptureMinimapModel(getStudioProfilers(), capture, myTrackGroupTimeline.getViewRange());
    initTrackGroupList(myMinimapModel.getRangeSelectionModel().getSelectionRange(), capture);
    addCpuAnalysisModel(new CpuFullTraceAnalysisModel(capture, myMinimapModel.getRangeSelectionModel().getSelectionRange()));
  }

  /**
   * The order of track groups dictates their default order in the UI.
   */
  private void initTrackGroupList(@NotNull Range selectionRange, @NotNull CpuCapture capture) {
    myTrackGroupModels.clear();

    // Interaction events, e.g. user interaction, app lifecycle.
    myTrackGroupModels.add(createInteractionTrackGroup(selectionRange));

    // Display pipeline events, e.g. frames, surfaceflinger. Systrace only.
    if (capture instanceof AtraceCpuCapture) {
      myTrackGroupModels.add(createDisplayTrackGroup(selectionRange, (AtraceCpuCapture)capture));
    }

    // Thread states and trace events.
    myTrackGroupModels.add(createThreadsTrackGroup(selectionRange, capture));

    // CPU per-core usage and event etc. Systrace only.
    if (capture instanceof AtraceCpuCapture) {
      myTrackGroupModels.add(createCpuCoresTrackGroup(selectionRange, (AtraceCpuCapture)capture));
    }
  }

  private TrackGroupModel createInteractionTrackGroup(@NotNull Range selectionRange) {
    TrackGroupModel interaction = TrackGroupModel.newBuilder().setTitle("Interaction").build();
    interaction.addTrackModel(
      TrackModel.newBuilder(
        new EventModel<>(new RangedSeries<>(selectionRange, new UserEventDataSeries(getStudioProfilers()))),
        ProfilerTrackRendererType.USER_INTERACTION,
        "User"));
    interaction.addTrackModel(
      TrackModel.newBuilder(
        new LifecycleEventModel(
          new RangedSeries<>(selectionRange, new LifecycleEventDataSeries(getStudioProfilers(), false)),
          new RangedSeries<>(selectionRange, new LifecycleEventDataSeries(getStudioProfilers(), true))),
        ProfilerTrackRendererType.APP_LIFECYCLE,
        "Lifecycle"));
    return interaction;
  }

  private static TrackGroupModel createDisplayTrackGroup(@NotNull Range selectionRange, @NotNull AtraceCpuCapture atraceCapture) {
    TrackGroupModel display = TrackGroupModel.newBuilder().setTitle("Display").build();
    display.addTrackModel(
      TrackModel.newBuilder(
        new CpuFramesModel.FrameState("Main", atraceCapture.getMainThreadId(), AtraceFrame.FrameThread.MAIN, atraceCapture, selectionRange),
        ProfilerTrackRendererType.FRAMES,
        "Frames"));
    display.addTrackModel(
      TrackModel.newBuilder(
        new StateChartModel<EventAction>(),
        ProfilerTrackRendererType.SURFACEFLINGER,
        "Surfaceflinger"));
    display.addTrackModel(
      TrackModel.newBuilder(
        new StateChartModel<EventAction>(),
        ProfilerTrackRendererType.VSYNC,
        "Vsync"));
    return display;
  }

  private TrackGroupModel createThreadsTrackGroup(@NotNull Range selectionRange, @NotNull CpuCapture capture) {
    List<CpuThreadInfo> threadInfos = capture.getThreads().stream().sorted().collect(Collectors.toList());
    String threadsTitle = String.format(Locale.getDefault(), "Threads (%d)", threadInfos.size());
    TrackGroupModel threads = TrackGroupModel.newBuilder().setTitle(threadsTitle).setTrackSelectable(true).build();
    for (CpuThreadInfo threadInfo : threadInfos) {
      threads.addTrackModel(
        TrackModel.newBuilder(
          new CpuThreadTrackModel(getStudioProfilers(), selectionRange, capture, threadInfo),
          ProfilerTrackRendererType.CPU_THREAD,
          threadInfo.getName()));
    }
    return threads;
  }

  private TrackGroupModel createCpuCoresTrackGroup(@NotNull Range selectionRange, @NotNull AtraceCpuCapture capture) {
    int cpuCount = capture.getCpuCount();
    String coresTitle = String.format(Locale.getDefault(), "CPU cores (%d)", cpuCount);
    TrackGroupModel cores = TrackGroupModel.newBuilder().setTitle(coresTitle).setCollapsedInitially(true).build();
    for (int cpuId = 0; cpuId < cpuCount; ++cpuId) {
      cores.addTrackModel(
        TrackModel.newBuilder(
          new CpuCoreTrackModel(selectionRange, capture, cpuId, getStudioProfilers().getSession().getPid()),
          ProfilerTrackRendererType.CPU_CORE,
          "CPU " + cpuId));
    }
    return cores;
  }
}
