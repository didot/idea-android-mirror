/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.perfetto.traceprocessor

import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profiler.perfetto.proto.TraceProcessor.AndroidFrameEventsResult.*
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profilers.cpu.ThreadState
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineEvent
import com.android.tools.profilers.cpu.systemtrace.CounterModel
import com.android.tools.profilers.cpu.systemtrace.CpuCoreModel
import com.android.tools.profilers.cpu.systemtrace.ProcessModel
import com.android.tools.profilers.cpu.systemtrace.SchedulingEventModel
import com.android.tools.profilers.cpu.systemtrace.SystemTraceModelAdapter
import com.android.tools.profilers.cpu.systemtrace.ThreadModel
import com.android.tools.profilers.cpu.systemtrace.TraceEventModel
import perfetto.protos.PerfettoTrace
import java.io.Serializable
import java.util.Deque
import java.util.LinkedList
import java.util.concurrent.TimeUnit

class TraceProcessorModel(builder: Builder) : SystemTraceModelAdapter, Serializable {

  companion object {
    // generated by serialver
    @JvmStatic
    val serialVersionUID = -2228053132990163476L
  }

  private val processMap: Map<Int, ProcessModel>
  private val cpuCores: List<CpuCoreModel>
  private val androidFrameLayers: List<Layer>
  private val androidFrameTimelineEvents: List<AndroidFrameTimelineEvent>

  private val danglingThreads = builder.danglingThreads

  private val startCaptureTimestamp = builder.startCaptureTimestamp
  private val endCaptureTimestamp = builder.endCaptureTimestamp

  init {
    // Build processMap
    val processMapBuilder = mutableMapOf<Int, ProcessModel>()
    for (process in builder.processById.values) {
      val updatedThreadMap = process.threadById.mapValues { entry ->
        entry.value.copy(
          traceEvents = builder.threadToEventsMap.getOrDefault(entry.key, listOf()),
          schedulingEvents = builder.threadToScheduling.getOrDefault(entry.key, listOf())
        )
      }.toSortedMap()
      val counterMap = builder.processToCounters.getOrDefault(process.id, listOf())
        .map { it.name to it }
        .toMap()
      processMapBuilder[process.id] = process.copy(threadById = updatedThreadMap, counterByName = counterMap)
    }
    processMap = processMapBuilder.toSortedMap()

    // Build cpuCores
    cpuCores = (0 until builder.cpuCoresCount).map {
      val cpuCountersMap = builder.coreToCpuCounters.getOrDefault(it, listOf())
        .map { counterModel -> counterModel.name to counterModel }
        .toMap()
      CpuCoreModel(it, builder.coreToScheduling.getOrDefault(it, listOf()), cpuCountersMap)
    }
    androidFrameTimelineEvents = builder.androidFrameTimelineEvents
    androidFrameLayers = when {
      androidFrameTimelineEvents.isEmpty() -> builder.androidFrameLayers
      else -> builder.androidFrameLayers.renumbered(androidFrameTimelineEvents, builder.surfaceflingerDisplayTokenToEndNs)
    }
  }

  override fun getCaptureStartTimestampUs() = startCaptureTimestamp
  override fun getCaptureEndTimestampUs() = endCaptureTimestamp

  override fun getProcessById(id: Int) = processMap[id]
  override fun getProcesses() = processMap.values.toList()
  override fun getDanglingThread(tid: Int): ThreadModel? = danglingThreads[tid]

  override fun getCpuCores() = cpuCores

  override fun getSystemTraceTechnology() = Cpu.CpuTraceType.PERFETTO

  // TODO(b/156578844): Fetch data from TraceProcessor error table to populate this.
  override fun isCapturePossibleCorrupted() = false
  override fun getAndroidFrameLayers() = androidFrameLayers
  override fun getAndroidFrameTimelineEvents() = androidFrameTimelineEvents

  class Builder {
    internal var startCaptureTimestamp = Long.MAX_VALUE
    internal var endCaptureTimestamp = Long.MIN_VALUE
    internal var cpuCoresCount = 0
    internal val processById = mutableMapOf<Int, ProcessModel>()
    internal val danglingThreads = mutableMapOf<Int, ThreadModel>()
    internal val threadToEventsMap = mutableMapOf<Int, List<TraceEventModel>>()
    internal val threadToScheduling = mutableMapOf<Int, List<SchedulingEventModel>>()
    internal val coreToScheduling = mutableMapOf<Int, List<SchedulingEventModel>>()
    internal val coreToCpuCounters = mutableMapOf<Int, List<CounterModel>>()
    internal val processToCounters = mutableMapOf<Int, List<CounterModel>>()
    internal val androidFrameLayers = mutableListOf<Layer>()
    internal val androidFrameTimelineEvents = mutableListOf<AndroidFrameTimelineEvent>()
    internal var surfaceflingerDisplayTokenToEndNs = mapOf<Long, Long>()

    fun addProcessMetadata(processMetadataResult: TraceProcessor.ProcessMetadataResult) {
      for (process in processMetadataResult.processList) {
        processById[process.id.toInt()] = ProcessModel(
          process.id.toInt(),
          process.name,
          process.threadList.map { t -> t.id.toInt() to ThreadModel(t.id.toInt(), process.id.toInt(), t.name, listOf(), listOf()) }
            .toMap().toSortedMap(),
          mapOf())
      }

      for (thread in processMetadataResult.danglingThreadList) {
        danglingThreads[thread.id.toInt()] = ThreadModel(thread.id.toInt(), 0, thread.name, emptyList(), emptyList())
      }
    }

    fun addTraceEvents(traceEventsResult: TraceProcessor.TraceEventsResult) {
      for (thread in traceEventsResult.threadList) {
        val rootIds = mutableSetOf<Long>()
        val eventToChildrenIds = mutableMapOf<Long, MutableList<Long>>()
        val eventPerId = mutableMapOf<Long, TraceEventModel>()

        for (event in thread.traceEventList) {
          if (event.depth > 0) {
            eventToChildrenIds.getOrPut(event.parentId) { mutableListOf() }.add(event.id)
          }
          else {
            rootIds.add(event.id)
          }

          val startTimestampUs = convertToUs(event.timestampNanoseconds)
          val durationTimestampUs = convertToUs(event.durationNanoseconds)
          val endTimestampUs = startTimestampUs + durationTimestampUs

          eventPerId[event.id] = TraceEventModel(event.name,
                                                 startTimestampUs,
                                                 endTimestampUs,
                                                 durationTimestampUs,
                                                 listOf())
        }

        val reconstructedTree = reconstructTraceTree(rootIds, eventToChildrenIds, eventPerId)
        threadToEventsMap[thread.threadId.toInt()] = rootIds.mapNotNull { reconstructedTree[it] }
      }
    }

    // Runs through the partially computed events to rebuild the whole trace trees, by doing a DFS from the root nodes.
    private fun reconstructTraceTree(
      rootIds: Set<Long>, eventToChildrenIds: Map<Long, List<Long>>, eventPerId: Map<Long, TraceEventModel>): Map<Long, TraceEventModel> {

      val reconstructedEventsPerId = mutableMapOf<Long, TraceEventModel>()

      val visitedAllChildren = mutableSetOf<Long>()
      val eventIdStack: Deque<Long> = LinkedList(rootIds)

      while (eventIdStack.isNotEmpty()) {
        val eventId = eventIdStack.first

        // If we have not visited this node yet, then we need to push all its children to the front of the queue
        // and continue the main loop. Next time we pass on this one, we will process it as we know all its children
        // have been processed already.
        if (!visitedAllChildren.contains(eventId)) {
          eventToChildrenIds.getOrDefault(eventId, mutableListOf()).forEach { eventIdStack.addFirst(it) }
          visitedAllChildren.add(eventId)
          continue
        }

        eventIdStack.removeFirst()
        val children = eventToChildrenIds.getOrDefault(eventId, mutableListOf())
          .map { reconstructedEventsPerId[it] ?: error("Children should have been computed already") }
          .sortedBy { it.startTimestampUs }

        val event = eventPerId[eventId] ?: error("Trace Event should be present in the map")

        val myStart = event.startTimestampUs
        val maxEndTs = children.lastOrNull()?.endTimestampUs ?: 0L
        val myCpuTime = event.cpuTimeUs

        val updatedEvent = event.copy(
          // Our end time is either the end of our last children or our start + how much time we took.
          endTimestampUs = maxOf(myStart + myCpuTime, maxEndTs),
          childrenEvents = children)
        reconstructedEventsPerId[eventId] = updatedEvent

        // Update the global start/end of the capture.
        startCaptureTimestamp = minOf(startCaptureTimestamp, updatedEvent.startTimestampUs)
        endCaptureTimestamp = maxOf(endCaptureTimestamp, updatedEvent.endTimestampUs)
      }

      return reconstructedEventsPerId
    }

    fun addSchedulingEvents(schedEvents: TraceProcessor.SchedulingEventsResult) {
      cpuCoresCount = maxOf(cpuCoresCount, schedEvents.numCores)

      val perThreadScheduling = mutableMapOf<Int, MutableList<SchedulingEventModel>>()
      val perCoreScheduling = mutableMapOf<Int, MutableList<SchedulingEventModel>>()
      schedEvents.schedEventList
        .groupBy { it.threadId }
        .forEach { (tid, events) ->
          events.forEachIndexed { index, event ->
            val startTimestampUs = convertToUs(event.timestampNanoseconds)
            val durationUs = convertToUs(event.durationNanoseconds)
            val endTimestampUs = startTimestampUs + durationUs
            startCaptureTimestamp = minOf(startCaptureTimestamp, startTimestampUs)
            endCaptureTimestamp = maxOf(endCaptureTimestamp, endTimestampUs)

            // Scheduling events from the Trace Processor encode thread states differently from ftrace.
            // TP only records the end_state of the event and implies RUNNING as the start_state always:
            // https://perfetto.dev/docs/data-sources/cpu-scheduling#decoding-code-end_state-code-
            //
            // So for every event except the last one, we need to insert a RUNNING event + an end_state event.
            val schedEvent = SchedulingEventModel(ThreadState.RUNNING_CAPTURED,
                                                  startTimestampUs,
                                                  endTimestampUs,
                                                  durationUs,
                                                  durationUs,
                                                  event.processId.toInt(),
                                                  event.threadId.toInt(),
                                                  event.cpu)
            // Add a RUNNING event and an [end_state] event to thread scheduling events.
            perThreadScheduling.getOrPut(tid.toInt()) { mutableListOf() }.apply {
              // The RUNNING thread state event.
              add(schedEvent)
              if (index < events.size - 1) {
                val nextStartTimestampUs = convertToUs(events[index + 1].timestampNanoseconds)
                val nextDurationTimeUs = nextStartTimestampUs - endTimestampUs
                // The [end_state] thread state event.
                add(SchedulingEventModel(convertSchedulingState(event.endState),
                                         endTimestampUs,
                                         nextStartTimestampUs,
                                         nextDurationTimeUs,
                                         nextDurationTimeUs,
                                         event.processId.toInt(),
                                         event.threadId.toInt(),
                                         event.cpu))
              }
            }
            // Add just the RUNNING event to core scheduling events.
            perCoreScheduling.getOrPut(event.cpu) { mutableListOf() }.add(schedEvent)
          }
        }

      perThreadScheduling.forEach {
        val previousList = threadToScheduling[it.key] ?: listOf()
        threadToScheduling[it.key] = previousList.plus(it.value).sortedBy { s -> s.startTimestampUs }
      }
      perCoreScheduling.forEach {
        val previousList = coreToScheduling[it.key] ?: listOf()
        coreToScheduling[it.key] = previousList.plus(it.value).sortedBy { s -> s.startTimestampUs }
      }
    }

    private fun convertSchedulingState(state: TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState): ThreadState {
      return when (state) {
        TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState.RUNNABLE -> ThreadState.RUNNABLE_CAPTURED
        TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState.RUNNABLE_PREEMPTED -> ThreadState.RUNNABLE_CAPTURED
        TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState.DEAD -> ThreadState.DEAD_CAPTURED
        TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState.SLEEPING -> ThreadState.SLEEPING_CAPTURED
        TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState.SLEEPING_UNINTERRUPTIBLE -> ThreadState.WAITING_CAPTURED
        TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState.WAKING -> ThreadState.RUNNABLE_CAPTURED
        TraceProcessor.SchedulingEventsResult.SchedulingEvent.SchedulingState.WAKE_KILL -> ThreadState.RUNNABLE_CAPTURED
        else -> ThreadState.UNKNOWN
      }
    }

    fun addCpuCounters(result: TraceProcessor.CpuCoreCountersResult) {
      cpuCoresCount = maxOf(cpuCoresCount, result.numCores)

      result.countersPerCoreList.forEach { countersPerCore ->
        coreToCpuCounters[countersPerCore.cpu] = countersPerCore.counterList.map { counter ->
          CounterModel(counter.name,
                       counter.valueList.map { convertToUs(it.timestampNanoseconds) to it.value }
                         .toMap().toSortedMap())
        }
      }
    }

    fun addProcessCounters(counters: TraceProcessor.ProcessCountersResult) {
      processToCounters[counters.processId.toInt()] = counters.counterList.map { counter ->
        CounterModel(counter.name,
                     counter.valueList.map { convertToUs(it.timestampNanoseconds) to it.value }
                       .toMap().toSortedMap())
      }
    }

    fun addAndroidFrameEvents(frameEventsResult: TraceProcessor.AndroidFrameEventsResult) {
      androidFrameLayers.addAll(frameEventsResult.layerList)
    }

    /**
     * Process the Frame Timeline query result and turn it into [AndroidFrameTimelineEvent]s, ordered by
     * [AndroidFrameTimelineEvent.expectedStartUs]
     */
    fun addAndroidFrameTimelineEvents(frameTimelineResult: TraceProcessor.AndroidFrameTimelineResult) {
      // Match actual timeline slices to expected timeline slices by surfaceFrameToken and construct an AndroidFrameTimelineEvent
      // from two matching slices.
      val expectedSlicesBySurfaceFrame = frameTimelineResult.expectedSliceList.associateBy { it.surfaceFrameToken }
      frameTimelineResult.actualSliceList.forEach { actualSlice ->
        expectedSlicesBySurfaceFrame[actualSlice.surfaceFrameToken]?.let { expectedSlice ->
          val expectedEndUs = convertToUs(expectedSlice.timestampNanoseconds + expectedSlice.durationNanoseconds)
          val actualEndUs = convertToUs(actualSlice.timestampNanoseconds + actualSlice.durationNanoseconds)
          androidFrameTimelineEvents.add(AndroidFrameTimelineEvent(expectedSlice.displayFrameToken,
                                                                   expectedSlice.surfaceFrameToken,
                                                                   convertToUs(expectedSlice.timestampNanoseconds),
                                                                   expectedEndUs,
                                                                   actualEndUs,
                                                                   actualSlice.layerName,
                                                                   parsePresentType(actualSlice.presentType),
                                                                   parseAppJankType(actualSlice.jankType),
                                                                   actualSlice.onTimeFinish,
                                                                   actualSlice.gpuComposition))
        }
      }
      androidFrameTimelineEvents.sortBy { it.expectedStartUs }
    }

    fun indexSurfaceflingerFrameTimelineEvents(frameTimelineResult: TraceProcessor.AndroidFrameTimelineResult) {
      surfaceflingerDisplayTokenToEndNs = frameTimelineResult.actualSliceList.associate {
        it.displayFrameToken to it.timestampNanoseconds + it.durationNanoseconds
      }
    }

    fun build(): TraceProcessorModel {
      return TraceProcessorModel(this)
    }

    private fun convertToUs(tsNanos: Long) = TimeUnit.NANOSECONDS.toMicros(tsNanos)

    private fun parsePresentType(presetTypeStr: String) = when (presetTypeStr) {
      "On-time Present" -> PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_ON_TIME
      "Late Present" -> PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_LATE
      "Early Present" -> PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_EARLY
      "Dropped Frame" -> PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_DROPPED
      "Unknown Present" -> PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_UNKNOWN
      else -> PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_UNSPECIFIED
    }

    private fun parseAppJankType(jankTypeStr: String): PerfettoTrace.FrameTimelineEvent.JankType {
      // jankTypeStr is a comma-separated string of both an app jank type and a surfaceflinger jank type (if exists).
      // In SystemTrace we only care about the app jank type, so once a match is found we can return early.
      jankTypeStr.split(", ").forEach {
        when (it) {
          "None" -> return PerfettoTrace.FrameTimelineEvent.JankType.JANK_NONE
          "App Deadline Missed" -> return PerfettoTrace.FrameTimelineEvent.JankType.JANK_APP_DEADLINE_MISSED
          "Buffer Stuffing" -> return PerfettoTrace.FrameTimelineEvent.JankType.JANK_BUFFER_STUFFING
          "Unknown Jank" -> return PerfettoTrace.FrameTimelineEvent.JankType.JANK_UNKNOWN
        }
      }
      return PerfettoTrace.FrameTimelineEvent.JankType.JANK_UNSPECIFIED
    }
  }
}

private fun List<Layer>.renumbered(timelineEvents: List<AndroidFrameTimelineEvent>,
                                   surfaceflingerDisplayTokenToEndNs: Map<Long, Long>): List<Layer> =
  mapFrameNumber(this, timelineEvents, surfaceflingerDisplayTokenToEndNs).let { frameNumberMap ->
    fun timelineNumberOf(lifecycleNumber: Int) = frameNumberMap[lifecycleNumber]?.toInt()
    fun transformFrame(evt: FrameEvent): FrameEvent? =
      timelineNumberOf(evt.frameNumber)?.let { evt.toBuilder().setFrameNumber(it).build() }
    fun transformPhase(phase: Phase): Phase =
      phase.toBuilder().clearFrameEvent().addAllFrameEvent(phase.frameEventList.mapNotNull(::transformFrame)).build()
    fun transformLayer(layer: Layer): Layer =
      layer.toBuilder().clearPhase().addAllPhase(layer.phaseList.mapNotNull(::transformPhase)).build()
    map(::transformLayer)
  }

/**
 * Map lifecycle's frame numbers to timeline's frame numbers by:
 * - identifying the surfaceflinger event sharing the same display-token as the app event
 * - looking up the lifecycle's event whose start matches the surfaceflinger event's end
 */
private fun mapFrameNumber(layers: List<Layer>,
                           timelineEvents: List<AndroidFrameTimelineEvent>,
                           surfaceflingerDisplayTokenToEndNs: Map<Long, Long>): Map<Int, Long> {
  val lifeCycleEventStartNsToNumber = mutableMapOf<Long, Int>()
  layers.forEach { layer ->
    layer.phaseList.forEach { phase ->
      phase.frameEventList.forEach { event ->
        lifeCycleEventStartNsToNumber[event.timestampNanoseconds] = event.frameNumber
      }
    }
  }
  return timelineEvents.asSequence()
    .mapNotNull { appEvent ->
      val surfaceflingerEndNs = surfaceflingerDisplayTokenToEndNs[appEvent.displayFrameToken]
      lifeCycleEventStartNsToNumber[surfaceflingerEndNs]?.let { lifecycleFrameNumber ->
        lifecycleFrameNumber to appEvent.surfaceFrameToken
      }
    }
    .toMap()
}