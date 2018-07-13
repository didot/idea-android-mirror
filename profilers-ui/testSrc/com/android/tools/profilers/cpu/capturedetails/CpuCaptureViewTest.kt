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
package com.android.tools.profilers.cpu.capturedetails

import com.android.tools.adtui.FilterComponent
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.ART
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType.ATRACE
import com.android.tools.profilers.*
import com.android.tools.profilers.cpu.*
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CpuCaptureViewTest {
  @JvmField
  @Rule
  val grpcChannel: FakeGrpcChannel

  @JvmField
  @Rule
  val cpuProfiler: FakeCpuProfiler

  init {
    val cpuService = FakeCpuService()
    grpcChannel = FakeGrpcChannel("CpuCaptureViewTestChannel", cpuService, FakeProfilerService(),
                                  FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build())

    cpuProfiler = FakeCpuProfiler(grpcChannel = grpcChannel, cpuService = cpuService)
  }

  private lateinit var captureView: CpuCaptureView
  private lateinit var stageView: CpuProfilerStageView

  @Before
  fun setUp() {
    val profilersView = StudioProfilersView(cpuProfiler.stage.studioProfilers, FakeIdeProfilerComponents())

    stageView = CpuProfilerStageView(profilersView, cpuProfiler.stage)
    captureView = CpuCaptureView(stageView)
  }

  @Test
  fun whenSelectingCallChartThereShouldBeInstanceOfTreeChartView() {
    val stage = cpuProfiler.stage

    cpuProfiler.apply {
      setTrace(CpuProfilerUITestUtils.VALID_TRACE_PATH)
      captureTrace(profilerType = ART)
    }

    stage.setCaptureDetails(CaptureDetails.Type.BOTTOM_UP)
    assertThat(stage.captureDetails?.type).isEqualTo(CaptureDetails.Type.BOTTOM_UP)
    ReferenceWalker(captureView).assertNotReachable(ChartDetailsView.CallChartDetailsView::class.java)

    stage.setCaptureDetails(CaptureDetails.Type.CALL_CHART)
    assertThat(stage.captureDetails?.type).isEqualTo(CaptureDetails.Type.CALL_CHART)
    ReferenceWalker(captureView).assertReachable(ChartDetailsView.CallChartDetailsView::class.java)

    val tabPane = TreeWalker(captureView.component).descendants().filterIsInstance<CommonTabbedPane>()[0]
    assertThat(tabPane.selectedIndex).isEqualTo(0)
    assertThat(tabPane.getTitleAt(0)).matches("Call Chart")
  }

  @Test
  fun testTraceEventTitleForATrace() {
    cpuProfiler.apply {
      setTrace(CpuProfilerUITestUtils.ATRACE_PID1_PATH)
      captureTrace(profilerType = ATRACE)
    }

    val tabPane = TreeWalker(captureView.component).descendants().filterIsInstance(CommonTabbedPane::class.java)[0]
    tabPane.selectedIndex = 0
    ReferenceWalker(captureView).assertReachable(ChartDetailsView.CallChartDetailsView::class.java)
    assertThat(tabPane.getTitleAt(0)).matches("Trace Events")
  }

  @Test
  fun interactionDisabledWhenHelpTipPane() {
    val capturePane = CpuCaptureView.HelpTipPane(stageView)
    val toolbar = TreeWalker(capturePane).descendants().filterIsInstance<CapturePane.Toolbar>().first()
    val tab = TreeWalker(capturePane).descendants().filterIsInstance<CommonTabbedPane>().first()
    assertThat(toolbar.isEnabled).isFalse()
    assertThat(tab.isEnabled).isFalse()
  }

  @Test
  fun interactionDisabledWhenLoadingPane() {
    val capturePane = CpuCaptureView.LoadingPane(stageView)
    val toolbar = TreeWalker(capturePane).descendants().filterIsInstance<CapturePane.Toolbar>().first()
    val tab = TreeWalker(capturePane).descendants().filterIsInstance<CommonTabbedPane>().first()
    assertThat(toolbar.isEnabled).isFalse()
    assertThat(tab.isEnabled).isFalse()
  }

  @Test
  fun showsHelpTipPaneWhenSelectingRangeWithNoCapture() {
    stageView.timeline.apply {
      dataRange.set(0.0, 200.0)
      viewRange.set(0.0, 200.0)
    }

    cpuProfiler.apply {
      setTrace(CpuProfilerUITestUtils.VALID_TRACE_PATH)
      captureTrace(id = 1, fromUs = 0, toUs = 100, profilerType = ART)
    }

    stageView.stage.selectionModel.apply {
      // Simulates the selection creation
      clear()
      set(105.0, 110.0)
    }
    assertThat(getCapturePane()).isInstanceOf(CpuCaptureView.HelpTipPane::class.java)
  }

  @Test
  fun showsDetailsPaneWhenSelectingCapture() {
    cpuProfiler.apply {
      setTrace(CpuProfilerUITestUtils.VALID_TRACE_PATH)
      captureTrace(profilerType = ART)
    }

    assertThat(getCapturePane()).isInstanceOf(DetailsCapturePane::class.java)
  }

  @Test
  fun showsLoadingPaneWhenParsing() {
    val observer = AspectObserver()
    var parsingCalled = false
    stageView.stage.aspect.addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_STATE) {
      if (stageView.stage.captureState == CpuProfilerStage.CaptureState.PARSING) {
        parsingCalled = true
        assertThat(getCapturePane()).isInstanceOf(CpuCaptureView.LoadingPane::class.java)
      }
    }
    assertThat(parsingCalled).isFalse()
    cpuProfiler.apply {
      setTrace(CpuProfilerUITestUtils.VALID_TRACE_PATH)
      captureTrace(profilerType = ART)
    }
    assertThat(parsingCalled).isTrue()
  }

  @Test
  fun filterSurvivesAcrossCaptures() {
    val stage = cpuProfiler.stage

    cpuProfiler.apply {
      ideServices.enableCpuCaptureFilter(true)
      setTrace(CpuProfilerUITestUtils.VALID_TRACE_PATH)
      captureTrace(profilerType = ART)
    }

    // In one chart, we'll open our filter and set it to something
    assertThat(stage.captureDetails?.type).isEqualTo(CaptureDetails.Type.CALL_CHART)
    (getCapturePane() as DetailsCapturePane).let { detailsCapturePane ->
      val filterComponent = TreeWalker(detailsCapturePane).descendants().filterIsInstance<FilterComponent>().first()

      assertThat(filterComponent.isVisible).isFalse()

      detailsCapturePane.myToolbar.filterButton.doClick()
      assertThat(filterComponent.isVisible).isTrue()

      filterComponent.setFilterText("ABC")
      filterComponent.waitForFilterUpdated()
    }

    // In another chart, we'll make sure our filter settings carried over
    stage.setCaptureDetails(CaptureDetails.Type.BOTTOM_UP)
    (getCapturePane() as DetailsCapturePane).let { detailsCapturePane ->
      val filterComponent = TreeWalker(detailsCapturePane).descendants().filterIsInstance<FilterComponent>().first()

      assertThat(filterComponent.isVisible).isTrue()
      assertThat(filterComponent.searchField.text).isEqualTo("ABC")
    }

    // Simulate selecting a new capture (without clearing captures in between). The filter should
    // be preserved
    run {
      val prevCapture = stage.capture
      stage.capture = CpuProfilerUITestUtils.validCapture()
      assertThat(stage.capture).isNotEqualTo(prevCapture)
    }

    (getCapturePane() as DetailsCapturePane).let { detailsCapturePane ->
      val filterComponent = TreeWalker(detailsCapturePane).descendants().filterIsInstance<FilterComponent>().first()
      filterComponent.waitForFilterUpdated()
      assertThat(filterComponent.isVisible).isTrue()
      assertThat(filterComponent.searchField.text).isEqualTo("ABC")
    }

    // Clearing a capture should also clear the current filter
    run {
      val oldCapture = stage.capture
      stage.capture = null
      stage.capture = oldCapture
    }
    stage.setCaptureDetails(CaptureDetails.Type.BOTTOM_UP)
    (getCapturePane() as DetailsCapturePane).let { detailsCapturePane ->
      val filterComponent = TreeWalker(detailsCapturePane).descendants().filterIsInstance<FilterComponent>().first()
      filterComponent.waitForFilterUpdated()

      assertThat(filterComponent.isVisible).isFalse()
      assertThat(filterComponent.searchField.text).isEmpty()
    }
  }

  @Test
  fun filterShowsMatchCount() {
    val stage = cpuProfiler.stage

    cpuProfiler.apply {
      ideServices.enableCpuCaptureFilter(true)
      setTrace(CpuProfilerUITestUtils.VALID_TRACE_PATH)
      captureTrace(profilerType = ART)
    }

    assertThat(stage.captureDetails?.type).isEqualTo(CaptureDetails.Type.CALL_CHART)
    (getCapturePane() as DetailsCapturePane).let { detailsCapturePane ->
      val filterComponent = TreeWalker(detailsCapturePane).descendants().filterIsInstance<FilterComponent>().first()

      var matchCount = 0
      filterComponent.model.addMatchResultListener { result -> matchCount = result.matchCount }

      detailsCapturePane.myToolbar.filterButton.doClick()
      assertThat(filterComponent.isVisible).isTrue()

      filterComponent.setFilterText("android") // Open trace file at CpuProfilerUITestUtils.VALID_TRACE_PATH
      filterComponent.waitForFilterUpdated()

      assertThat(matchCount).isGreaterThan(0)
      assertThat(filterComponent.countLabel.text).isNotEmpty()
    }
  }

  private fun getCapturePane() = TreeWalker(captureView.component)
    .descendants()
    .filterIsInstance<CapturePane>()
    .first()
}