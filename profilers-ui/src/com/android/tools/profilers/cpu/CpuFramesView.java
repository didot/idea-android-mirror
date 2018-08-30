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
import com.android.tools.adtui.event.DelegateMouseEventHandler;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.ui.HideablePanel;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTooltipMouseAdapter;
import com.android.tools.profilers.cpu.atrace.AtraceFrame;
import com.android.tools.profilers.cpu.atrace.CpuFrameTooltip;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.jetbrains.annotations.NotNull;

/**
 * Creates a view containing a {@link HideablePanel} composed by a {@link CpuListScrollPane} displaying a list of Frames. Each row
 * represents a thread composed by a {@link com.android.tools.adtui.chart.statechart.StateChart} whose data is
 * a list of {@link AtraceFrame} associated with that thread filtered by name.
 */
public class CpuFramesView {

  @NotNull
  private final HideablePanel myPanel;

  @NotNull
  private final CpuProfilerStage myStage;

  @NotNull
  private JBList<CpuFramesModel.FrameState> myFrames;

  public CpuFramesView(@NotNull CpuProfilerStage stage) {
    myFrames = new JBList<>(stage.getFramesModel());
    myStage = stage;
    myPanel = createPanel();

    setupListeners();
    myFrames.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    myFrames.setCellRenderer(new CpuFramesCellRenderer(myStage.getStudioProfilers().getIdeServices().getFeatureConfig(), myFrames));
    myStage.getFramesModel().addListDataListener(new ListDataListener() {
      @Override
      public void contentsChanged(ListDataEvent e) {
        int size = myStage.getFramesModel().getSize();
        boolean hasElements = size != 0;
        myPanel.setVisible(hasElements);
        myPanel.setExpanded(hasElements);
        myPanel.getParent().revalidate();
      }

      @Override
      public void intervalAdded(ListDataEvent e) {
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
      }
    });
    // |myPanel| does not receive any mouse events, because all mouse events are consumed by |myFrames|.
    // We're dispatching them manually, so that |CpuProfilerStageView| could register CPU mouse events
    // directly into the top-level component (i.e to |myPanel|) instead of its child.
    DelegateMouseEventHandler.delegateTo(myPanel)
                             .installListenerOn(myFrames)
                             .installMotionListenerOn(myFrames);
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  private void setupListeners() {
    // Handle selection.
    myFrames.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        frameSelected();
      }
    });

    // Handle Tooltip
    myFrames.addMouseListener(new ProfilerTooltipMouseAdapter(myStage, () -> new CpuFrameTooltip(myStage)));
    myFrames.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int row = myFrames.locationToIndex(e.getPoint());
        if (row != -1) {
          CpuFramesModel.FrameState model = myStage.getFramesModel().getElementAt(row);
          if (myStage.getTooltip() instanceof CpuFrameTooltip) {
            CpuFrameTooltip tooltip = (CpuFrameTooltip)myStage.getTooltip();
            tooltip.setFrameSeries(model.getSeries());
          }
        }
      }
    });
  }

  @NotNull
  private HideablePanel createPanel() {
    // Create hideable panel for frames list.
    final JPanel frames = new JPanel(new TabularLayout("*", "*"));
    HideablePanel framesPanel = new HideablePanel.Builder("FRAMES", frames)
      .setShowSeparator(false)
      .setClickableComponent(HideablePanel.ClickableComponent.TITLE)
      .build();

    CpuListScrollPane scrollingFrames = new CpuListScrollPane(myFrames, framesPanel);

    frames.add(scrollingFrames, new TabularLayout.Constraint(0,0));
    frames.setBorder(JBUI.Borders.empty());

    // Hide panel by default
    framesPanel.setVisible(false);

    // Clear border set by default on the hideable panel.
    framesPanel.setBorder(JBUI.Borders.empty());
    framesPanel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    return framesPanel;
  }

  /**
   * Update view selection, and selected thread when the user selects a frame.
   */
  private void frameSelected() {
    int selectedIndex = myFrames.getSelectedIndex();
    if (selectedIndex < 0) {
      return;
    }
    CpuFramesModel.FrameState state = myFrames.getModel().getElementAt(selectedIndex);
    Range tooltipRange = myStage.getStudioProfilers().getTimeline().getTooltipRange();
    List<SeriesData<AtraceFrame>> process = state.getModel().getSeries().get(0).getDataSeries().getDataForXRange(tooltipRange);
    if (process.isEmpty() || process.get(0).value == AtraceFrame.EMPTY) {
      return;
    }
    // Select the range of this frame.
    myStage.getStudioProfilers().getTimeline().getSelectionRange()
           .set(process.get(0).x, process.get(0).x + process.get(0).value.getDurationUs());
    // Select the thread associated with this frame.
    myStage.setSelectedThread(state.getThreadId());
  }
}
