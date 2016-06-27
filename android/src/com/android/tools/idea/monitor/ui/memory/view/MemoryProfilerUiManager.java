/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.monitor.ui.memory.view;

import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.Range;
import com.android.tools.idea.monitor.datastore.DataAdapter;
import com.android.tools.idea.monitor.datastore.Poller;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.SeriesDataType;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.memory.model.MemoryDataCache;
import com.android.tools.idea.monitor.ui.memory.model.MemoryPoller;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.EventDispatcher;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

public final class MemoryProfilerUiManager extends BaseProfilerUiManager {

  private JButton myTriggerHeapDumpButton;

  public MemoryProfilerUiManager(@NotNull Range xRange, @NotNull Choreographer choreographer,
                                 @NotNull SeriesDataStore datastore, @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    super(xRange, choreographer, datastore, eventDispatcher);
  }

  @NotNull
  @Override
  public Poller createPoller(int pid) {
    MemoryPoller poller = new MemoryPoller(myDataStore, new MemoryDataCache(), pid);
    Map<SeriesDataType, DataAdapter> adapters = poller.createAdapters();
    for (Map.Entry<SeriesDataType, DataAdapter> entry : adapters.entrySet()) {
      // TODO these need to be de-registered
      myDataStore.registerAdapter(entry.getKey(), entry.getValue());
    }
    return poller;
  }

  @Override
  @NotNull
  protected BaseSegment createOverviewSegment(@NotNull Range xRange,
                                              @NotNull SeriesDataStore dataStore,
                                              @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    return new MemorySegment(xRange, dataStore, eventDispatcher);
  }

  @Override
  public void setupExtendedOverviewUi(@NotNull JPanel toolbar, @NotNull JPanel overviewPanel) {
    super.setupExtendedOverviewUi(toolbar, overviewPanel);

    myTriggerHeapDumpButton = new JButton(AndroidIcons.Ddms.DumpHprof);
    myTriggerHeapDumpButton.addActionListener(e -> ((MemoryPoller)myPoller).requestHeapDump());
    toolbar.add(myTriggerHeapDumpButton, HorizontalLayout.LEFT);
  }

  @Override
  public void resetProfiler(@NotNull JPanel toolbar, @NotNull JPanel overviewPanel, @NotNull JPanel detailPanel) {
    super.resetProfiler(toolbar, overviewPanel, detailPanel);

    if (myTriggerHeapDumpButton != null) {
      toolbar.remove(myTriggerHeapDumpButton);
      myTriggerHeapDumpButton = null;
    }
  }
}
