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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.profilers.ProfilerFonts.TOOLTIP_BODY_FONT;

class CpuChartTooltipView extends CpuChartTooltipViewBase {
  public CpuChartTooltipView(@NotNull HTreeChart<CaptureNode> chart,
                             @NotNull CpuProfilerStageView stageView) {
    super(chart, stageView);
  }

  @Override
  protected void showTooltip(@NotNull CaptureNode node) {
    Range dataRange = myStageView.getTimeline().getDataRange();
    long start = (long)(node.getStart() - dataRange.getMin());
    long end = (long)(node.getEnd() - dataRange.getMin());
    long totalDuration = node.getDuration();

    getTooltipContainer().removeAll();
    JLabel nameLabel = new JLabel(node.getData().getFullName());
    nameLabel.setFont(TOOLTIP_BODY_FONT);
    nameLabel.setForeground(ProfilerColors.TOOLTIP_TEXT);
    getTooltipContainer().add(nameLabel, new TabularLayout.Constraint(0, 0));

    JLabel timelineLabel = new JLabel(String.format("%s - %s", TimeFormatter.getFullClockString(start),
                                                    TimeFormatter.getFullClockString(end)));
    timelineLabel.setFont(TOOLTIP_BODY_FONT);
    timelineLabel.setForeground(ProfilerColors.TOOLTIP_TEXT);
    timelineLabel.setBorder(JBUI.Borders.empty());

    JLabel durationLabel = new JLabel(String.format("%s", TimeFormatter.getSingleUnitDurationString(totalDuration)));
    durationLabel.setFont(TOOLTIP_BODY_FONT);
    durationLabel.setForeground(ProfilerColors.TOOLTIP_TEXT);
    durationLabel.setBorder(JBUI.Borders.empty());

    getTooltipContainer().add(timelineLabel, new TabularLayout.Constraint(1, 0));
    getTooltipContainer().add(durationLabel, new TabularLayout.Constraint(2, 0));
  }
}
