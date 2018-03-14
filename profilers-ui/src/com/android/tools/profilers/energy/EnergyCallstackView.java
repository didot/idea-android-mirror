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
package com.android.tools.profilers.energy;

import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.ui.HideablePanel;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profilers.stacktrace.*;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.util.ui.JBEmptyBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class EnergyCallstackView extends JPanel {

  private final EnergyProfilerStageView myStageView;

  public EnergyCallstackView(EnergyProfilerStageView stageView) {
    super(new VerticalFlowLayout());
    myStageView = stageView;
  }

  /**
   * Set the details view for all callstacks of a duration, if given {@code duration} is {@code null}, this clears the view.
   */
  public void setDuration(@Nullable EnergyDuration duration) {
    removeAll();
    if (duration == null) {
      return;
    }

    long startTimeNs = myStageView.getStage().getStudioProfilers().getSession().getStartTimestamp();
    for (EnergyProfiler.EnergyEvent event : duration.getEventList()) {
      if (event.getTraceId().isEmpty()) {
        continue;
      }

      String callstackString = myStageView.getStage().requestBytes(event.getTraceId()).toStringUtf8();
      List<CodeLocation> codeLocationList = Arrays.stream(callstackString.split("\\n"))
        .filter(line -> !line.trim().isEmpty())
        .map(line -> new StackFrameParser(line).toCodeLocation())
        .collect(Collectors.toList());
      StackTraceModel model = new StackTraceModel(myStageView.getStage().getStudioProfilers().getIdeServices().getCodeNavigator());
      StackTraceView stackTraceView = myStageView.getIdeComponents().createStackView(model);
      stackTraceView.getModel().setStackFrames(ThreadId.INVALID_THREAD_ID, codeLocationList);
      JComponent traceComponent = stackTraceView.getComponent();

      String time = TimeAxisFormatter.DEFAULT.getClockFormattedString(TimeUnit.NANOSECONDS.toMicros(event.getTimestamp() - startTimeNs));
      String metadataCase = event.getMetadataCase().name();
      String description = metadataCase.substring(metadataCase.lastIndexOf('_') + 1) + " - " + time;
      HideablePanel hideablePanel = new HideablePanel.Builder(description, traceComponent).build();
      hideablePanel.setBorder(new JBEmptyBorder(0, 0, 5, 0));
      add(hideablePanel);
    }
  }
}
