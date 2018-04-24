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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.sessions.SessionArtifact;
import org.jetbrains.annotations.NotNull;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profilers.memory.MemoryProfiler.saveLegacyAllocationToFile;

/**
 * A session artifact representation of a memory allocation recording (legacy).
 */
public class LegacyAllocationsSessionArtifact implements SessionArtifact<MemoryProfiler.AllocationsInfo> {

  @NotNull private final StudioProfilers myProfilers;
  @NotNull private final Common.Session mySession;
  @NotNull private final Common.SessionMetaData mySessionMetaData;
  @NotNull private final MemoryProfiler.AllocationsInfo myInfo;

  public LegacyAllocationsSessionArtifact(@NotNull StudioProfilers profilers,
                                          @NotNull Common.Session session,
                                          @NotNull Common.SessionMetaData sessionMetaData,
                                          @NotNull MemoryProfiler.AllocationsInfo info) {
    myProfilers = profilers;
    mySession = session;
    mySessionMetaData = sessionMetaData;
    myInfo = info;
  }

  @NotNull
  @Override
  public MemoryProfiler.AllocationsInfo getArtifactProto() {
    return myInfo;
  }

  @NotNull
  @Override
  public StudioProfilers getProfilers() {
    return myProfilers;
  }

  @Override
  @NotNull
  public Common.Session getSession() {
    return mySession;
  }

  @NotNull
  @Override
  public Common.SessionMetaData getSessionMetaData() {
    return mySessionMetaData;
  }

  @Override
  @NotNull
  public String getName() {
    return "Allocation Records";
  }

  @NotNull
  public String getSubtitle() {
    return isOngoingCapture()
           ? CAPTURING_SUBTITLE
           : TimeAxisFormatter.DEFAULT.getClockFormattedString(TimeUnit.NANOSECONDS.toMicros(getTimestampNs()));
  }

  @Override
  public long getTimestampNs() {
    return myInfo.getStartTime() - mySession.getStartTimestamp();
  }

  public boolean isOngoingCapture() {
    return myInfo.getEndTime() == Long.MAX_VALUE;
  }

  @Override
  public void onSelect() {
    // If the capture selected is not part of the currently selected session, we need to select the session containing the capture.
    boolean needsToChangeSession = mySession != myProfilers.getSession();
    if (needsToChangeSession) {
      myProfilers.getSessionsManager().setSession(mySession);
    }

    // If memory profiler is not yet open, we need to do it.
    boolean needsToOpenMemoryProfiler = !(myProfilers.getStage() instanceof MemoryProfilerStage);
    if (needsToOpenMemoryProfiler) {
      myProfilers.setStage(new MemoryProfilerStage(myProfilers));
    }

    long startTimestamp = TimeUnit.NANOSECONDS.toMicros(myInfo.getStartTime());
    long endTimestamp = TimeUnit.NANOSECONDS.toMicros(myInfo.getEndTime());
    if (isOngoingCapture()) {
      SessionArtifact.navigateTimelineToOngoingCapture(myProfilers.getTimeline(), startTimestamp);
    }
    else {
      // Adjust the view range to fit the capture object.
      assert myProfilers.getStage() instanceof MemoryProfilerStage;
      MemoryProfilerStage stage = (MemoryProfilerStage)myProfilers.getStage();
      Range captureRange = new Range(startTimestamp, endTimestamp);
      myProfilers.getTimeline().adjustRangeCloseToMiddleView(captureRange);

      // Finally, we set and select the capture in the MemoryProfilerStage, which should be the current stage of StudioProfilers.
      stage.getSelectionModel().set(captureRange.getMin(), captureRange.getMax());
    }

    myProfilers.getIdeServices().getFeatureTracker().trackSessionArtifactSelected(this, myProfilers.getSessionsManager().isSessionAlive());
  }

  void saveToFile(@NotNull OutputStream outputStream) {
    saveLegacyAllocationToFile(myProfilers.getClient().getMemoryClient(), mySession, myInfo, outputStream,
                               myProfilers.getIdeServices().getFeatureTracker());
  }

  public static List<SessionArtifact> getSessionArtifacts(@NotNull StudioProfilers profilers,
                                                          @NotNull Common.Session session,
                                                          @NotNull Common.SessionMetaData sessionMetaData) {
    MemoryProfiler.MemoryData response = profilers.getClient().getMemoryClient()
                                                  .getData(MemoryProfiler.MemoryRequest.newBuilder().setSession(session)
                                                                                       .setStartTime(session.getStartTimestamp())
                                                                                       .setEndTime(session.getEndTimestamp())
                                                                                       .build());

    List<SessionArtifact> artifacts = new ArrayList<>();
    for (MemoryProfiler.AllocationsInfo info : response.getAllocationsInfoList()) {
      // Skip AllocationsInfo's that represent live allocations.
      if (info.getLegacy()) {
        artifacts.add(new LegacyAllocationsSessionArtifact(profilers, session, sessionMetaData, info));
      }
    }

    return artifacts;
  }
}
