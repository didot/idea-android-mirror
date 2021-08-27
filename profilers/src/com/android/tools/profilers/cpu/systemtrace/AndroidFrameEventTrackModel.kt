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
package com.android.tools.profilers.cpu.systemtrace

import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedSeries
import com.android.tools.adtui.model.SeriesData
import com.android.tools.adtui.model.StateChartModel
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profilers.cpu.LazyDataSeries
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameEvent.Data
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameEvent.Padding
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.TimeUnit

/**
 * Track model for a frame lifecycle track representing Android frames in a specific phase.
 */
class AndroidFrameEventTrackModel
@VisibleForTesting
constructor(phaseName: String, eventSeries: List<RangedSeries<AndroidFrameEvent>>, val vsyncSeries: RangedSeries<Long>)
          : StateChartModel<AndroidFrameEvent>() {

  constructor(phase: TraceProcessor.AndroidFrameEventsResult.Phase,
              viewRange: Range,
              vsyncSeries: List<SeriesData<Long>>)
    : this(phase.phaseName,
           phase.frameEventList.groupBy { it.depth }
             .toSortedMap(compareByDescending { it }) // Display lower depth on top.
             .values
             .map { it.padded() }
             .filterNot { it.isEmpty() }
             .map { series -> RangedSeries(viewRange, LazyDataSeries { series }) },
           RangedSeries(viewRange, LazyDataSeries { vsyncSeries }))

  val androidFramePhase = AndroidFramePhase.valueOf(phaseName)
  var activeSeriesIndex = -1
    set(index) {
      if (field != index) {
        field = index
        changed(Aspect.MODEL_CHANGED)
      }
    }

  init {
    eventSeries.forEach(::addSeries)
  }

  companion object {
    /**
     * Fill in the gaps between events
     */
    private fun Iterable<TraceProcessor.AndroidFrameEventsResult.FrameEvent>.padded(): List<SeriesData<AndroidFrameEvent>> =
      padded({ TimeUnit.NANOSECONDS.toMicros(it.timestampNanoseconds) },
             {
               // Frame events from Perfetto may have -1 duration when the event is still ongoing (or if it's missing the end slice) so we
               // assign max long to the end timestamp.
               if (it.durationNanoseconds >= 0) TimeUnit.NANOSECONDS.toMicros(it.timestampNanoseconds + it.durationNanoseconds)
               else Long.MAX_VALUE
             },
             ::Data, { _, _ -> Padding })
  }
}

/**
 * Wrapper class for use with [StateChartModel].
 *
 * Use [Data] for events that contain [TraceProcessor.AndroidFrameEventsResult.FrameEvent] and [Padding] for padding events.
 */
sealed class AndroidFrameEvent {
  data class Data(val frameNumber: Int, val timestampUs: Long, val durationUs: Long) : AndroidFrameEvent() {
    constructor(frameEvent: TraceProcessor.AndroidFrameEventsResult.FrameEvent) : this(
      frameEvent.frameNumber,
      TimeUnit.NANOSECONDS.toMicros(frameEvent.timestampNanoseconds),
      // Frame events from Perfetto may have -1 duration when the event is still ongoing (or if it's missing the end slice).
      if (frameEvent.durationNanoseconds >= 0) TimeUnit.NANOSECONDS.toMicros(frameEvent.durationNanoseconds) else Long.MAX_VALUE)
  }

  object Padding : AndroidFrameEvent()
}

enum class AndroidFramePhase(val displayName: String, val tooltipText: String) {
  App("Application", "<html><b>The time from when the buffer was dequeued by the app to when it<br>" +
                     "was enqueued back.</b></html>"),
  GPU("Wait for GPU", "<html><b>Duration for which the buffer was owned by GPU. This is the time from when the buffer<br>" +
                      "was sent to GPU to the time when GPU finishes its work on the buffer.<br>" +
                      "This does not mean the time GPU was working solely on the buffer during this time.</b></html>"),
  Composition("Composition",
              "<html><b>The time from when SurfaceFlinger latched on to the buffer and sent<br>" +
              "for composition to when it was sent to the display.</b></html>"),
  Display("Frames on display", "<html><b>The time when this frame was on screen.</b></html>");
}
