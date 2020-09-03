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
package com.android.tools.idea.profilers.perfetto.traceprocessor

import com.android.tools.profiler.perfetto.proto.Memory
import com.android.tools.profiler.perfetto.proto.TraceProcessor.LoadTraceRequest
import com.android.tools.profiler.perfetto.proto.TraceProcessor.QueryBatchRequest
import com.android.tools.profiler.perfetto.proto.TraceProcessor.QueryBatchResponse
import com.android.tools.profiler.perfetto.proto.TraceProcessor.QueryParameters
import com.android.tools.profiler.perfetto.proto.TraceProcessor.QueryResult
import com.android.tools.profilers.analytics.FeatureTracker
import com.android.tools.profilers.cpu.systemtrace.ProcessModel
import com.android.tools.profilers.cpu.systemtrace.SystemTraceModelAdapter
import com.android.tools.profilers.memory.adapters.classifiers.NativeMemoryHeapSet
import com.android.tools.profilers.perfetto.traceprocessor.TraceProcessorModel
import com.android.tools.profilers.perfetto.traceprocessor.TraceProcessorService
import com.android.tools.profilers.stacktrace.NativeFrameSymbolizer
import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import com.google.wireless.android.sdk.stats.TraceProcessorDaemonQueryStats
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * See {@link TraceProcessorService} for API details.
 */
@Service
class TraceProcessorServiceImpl(
    private val ticker: Ticker = Ticker.systemTicker(),
    private val client: TraceProcessorDaemonClient = TraceProcessorDaemonClient(ticker)) : TraceProcessorService, Disposable {
  private val loadedTraces = mutableMapOf<Long, File>()

  init {
    Disposer.register(this, client)
  }

  companion object {
    private val LOGGER = Logger.getInstance(TraceProcessorServiceImpl::class.java)

    @JvmStatic
    fun getInstance(): TraceProcessorService {
      return ServiceManager.getService(TraceProcessorServiceImpl::class.java)
    }
  }

  override fun loadTrace(traceId: Long, traceFile: File, tracker: FeatureTracker): Boolean {
    // load trace had no business logic in Java side, so we use a single stopwatch to track both query and method timings.
    val stopwatch = Stopwatch.createStarted(ticker)

    LOGGER.info("TPD Service: Loading trace $traceId: ${traceFile.absolutePath}")
    val requestProto = LoadTraceRequest.newBuilder()
      .setTraceId(traceId)
      .setTracePath(traceFile.absolutePath)
      .build()

    val queryResult = client.loadTrace(requestProto, tracker)
    stopwatch.stop()

    val queryTimeMs = stopwatch.elapsed(TimeUnit.MILLISECONDS)
    val traceSizeBytes = traceFile.length()

    if (!queryResult.completed) {
      tracker.trackTraceProcessorLoadTrace(TraceProcessorDaemonQueryStats.QueryReturnStatus.QUERY_FAILED,
                                           queryTimeMs,
                                           queryTimeMs,
                                           traceSizeBytes)
      val failureReason = queryResult.failure!!
      LOGGER.warn("TPD Service: Fail to load trace $traceId: ${failureReason.message}")
      throw RuntimeException("TPD Service: Fail to load trace $traceId: ${failureReason.message}", failureReason)
    }

    val response = queryResult.response!!

    val queryStatus =
      if (response.ok) TraceProcessorDaemonQueryStats.QueryReturnStatus.OK
      else TraceProcessorDaemonQueryStats.QueryReturnStatus.QUERY_ERROR

    tracker.trackTraceProcessorLoadTrace(queryStatus, queryTimeMs, queryTimeMs, traceSizeBytes)
    if (response.ok) {
      LOGGER.info("TPD Service: Trace $traceId loaded.")
      loadedTraces[traceId] = traceFile
      return true
    } else {
      LOGGER.info("TPD Service: Error loading trace $traceId: ${response.error}")
      return false
    }
  }

  override fun getProcessMetadata(traceId: Long, tracker: FeatureTracker): List<ProcessModel> {
    val methodStopwatch = Stopwatch.createStarted(ticker)
    val query = QueryBatchRequest.newBuilder()
      // Query metadata for all processes.
      .addQuery(QueryParameters.newBuilder()
                  .setTraceId(traceId)
                  .setProcessMetadataRequest(QueryParameters.ProcessMetadataParameters.getDefaultInstance()))
      .build()

    LOGGER.info("TPD Service: Querying process metadata for trace $traceId.")
    val queryStopwatch = Stopwatch.createStarted(ticker)
    val queryResult = executeBatchQuery(traceId, query, tracker)
    queryStopwatch.stop()
    val queryTimeMs = queryStopwatch.elapsed(TimeUnit.MILLISECONDS)

    if (!queryResult.completed) {
      methodStopwatch.stop()
      val methodTimeMs = methodStopwatch.elapsed(TimeUnit.MILLISECONDS)
      tracker.trackTraceProcessorProcessMetadata(TraceProcessorDaemonQueryStats.QueryReturnStatus.QUERY_FAILED, methodTimeMs, queryTimeMs)
      val failureReason = queryResult.failure!!
      LOGGER.info("TPD Service: Fail to get process metadata for trace $traceId: ${failureReason.message}")
      throw RuntimeException("TPD Service: Fail to get process metadata for trace $traceId: ${failureReason.message}", failureReason)
    }

    val response = queryResult.response!!
    var queryError = false
    response.resultList.forEach {
      if (!it.ok) {
        queryError = true
        LOGGER.warn("TPD Service: Process metadata query error - ${it.failureReason} - ${it.error}")
      }
    }

    val modelBuilder = TraceProcessorModel.Builder()
    response.resultList
      .filter { it.hasProcessMetadataResult() }
      .forEach { modelBuilder.addProcessMetadata(it.processMetadataResult) }
    val model = modelBuilder.build()

    // Report metrics for OK or ERROR query
    methodStopwatch.stop()
    val methodTimeMs = methodStopwatch.elapsed(TimeUnit.MILLISECONDS)
    val queryStatus =
      if (queryError) TraceProcessorDaemonQueryStats.QueryReturnStatus.QUERY_ERROR
      else TraceProcessorDaemonQueryStats.QueryReturnStatus.OK
    tracker.trackTraceProcessorProcessMetadata(queryStatus, methodTimeMs, queryTimeMs)

    return model.getProcesses()
  }

  override fun loadCpuData(traceId: Long, processIds: List<Int>, tracker: FeatureTracker): SystemTraceModelAdapter {
    val methodStopwatch = Stopwatch.createStarted(ticker)
    val queryBuilder = QueryBatchRequest.newBuilder()
      // Query metadata for all processes, as we need the info from everything to reference in the scheduling events.
      .addQuery(QueryParameters.newBuilder()
                  .setTraceId(traceId)
                  .setProcessMetadataRequest(QueryParameters.ProcessMetadataParameters.getDefaultInstance()))
      // Query scheduling for all processes, as we need it to build the cpu/core data series anyway.
      .addQuery(QueryParameters.newBuilder()
                  .setTraceId(traceId)
                  .setSchedRequest(QueryParameters.SchedulingEventsParameters.getDefaultInstance()))

    // Now let's add the queries that we limit for the processes we're interested in:
    for (id in processIds) {
      queryBuilder.addQuery(QueryParameters.newBuilder()
                              .setTraceId(traceId)
                              .setTraceEventsRequest(QueryParameters.TraceEventsParameters.newBuilder().setProcessId(id.toLong())))
      queryBuilder.addQuery(QueryParameters.newBuilder()
                              .setTraceId(traceId)
                              .setCountersRequest(QueryParameters.CountersParameters.newBuilder().setProcessId(id.toLong())))
    }

    LOGGER.info("TPD Service: Querying cpu data for trace $traceId.")
    val queryStopwatch = Stopwatch.createStarted(ticker)
    val queryResult = executeBatchQuery(traceId, queryBuilder.build(), tracker)
    queryStopwatch.stop()
    val queryTimeMs = queryStopwatch.elapsed(TimeUnit.MILLISECONDS)

    if (!queryResult.completed) {
      methodStopwatch.stop()
      val methodTimeMs = methodStopwatch.elapsed(TimeUnit.MILLISECONDS)
      tracker.trackTraceProcessorCpuData(TraceProcessorDaemonQueryStats.QueryReturnStatus.QUERY_FAILED, methodTimeMs, queryTimeMs)
      val failureReason = queryResult.failure!!
      LOGGER.info("TPD Service: Fail to get cpu data for trace $traceId: ${failureReason.message}")
      throw RuntimeException("TPD Service: Fail to get cpu data for trace $traceId: ${failureReason.message}", failureReason)
    }

    val response = queryResult.response!!
    var queryError = false
    response.resultList.forEach {
      if (!it.ok) {
        queryError = true
        LOGGER.warn("TPD Service: Load cpu data query error - ${it.failureReason} - ${it.error}")
      }
    }

    val modelBuilder = TraceProcessorModel.Builder()
    response.resultList.filter { it.hasProcessMetadataResult() }.forEach { modelBuilder.addProcessMetadata(it.processMetadataResult) }
    response.resultList.filter { it.hasTraceEventsResult() }.forEach { modelBuilder.addTraceEvents(it.traceEventsResult) }
    response.resultList.filter { it.hasSchedResult() }.forEach { modelBuilder.addSchedulingEvents(it.schedResult) }
    response.resultList.filter { it.hasCountersResult() }.forEach { modelBuilder.addCounters(it.countersResult) }

    val model = modelBuilder.build()

    // Report metrics for OK or ERROR query
    methodStopwatch.stop()
    val methodTimeMs = methodStopwatch.elapsed(TimeUnit.MILLISECONDS)
    val queryStatus =
      if (queryError) TraceProcessorDaemonQueryStats.QueryReturnStatus.QUERY_ERROR
      else TraceProcessorDaemonQueryStats.QueryReturnStatus.OK
    tracker.trackTraceProcessorCpuData(queryStatus, methodTimeMs, queryTimeMs)

    return model
  }

  override fun loadMemoryData(traceId: Long,
                              abi: String,
                              symbolizer: NativeFrameSymbolizer,
                              memorySet: NativeMemoryHeapSet,
                              tracker: FeatureTracker) {
    val methodStopwatch = Stopwatch.createStarted(ticker)
    val converter = HeapProfdConverter(abi, symbolizer, memorySet, WindowsNameDemangler())
    val query = QueryBatchRequest.newBuilder()
      .addQuery(QueryParameters.newBuilder()
                  .setTraceId(traceId)
                  .setMemoryRequest(Memory.AllocationDataRequest.getDefaultInstance()))
      .build()

    LOGGER.info("TPD Service: Querying process metadata for trace $traceId.")
    val queryStopwatch = Stopwatch.createStarted(ticker)
    val queryResult = executeBatchQuery(traceId, query, tracker)
    queryStopwatch.stop()
    val queryTimeMs = queryStopwatch.elapsed(TimeUnit.MILLISECONDS)

    if (!queryResult.completed) {
      methodStopwatch.stop()
      val methodTimeMs = methodStopwatch.elapsed(TimeUnit.MILLISECONDS)
      tracker.trackTraceProcessorMemoryData(TraceProcessorDaemonQueryStats.QueryReturnStatus.QUERY_FAILED, methodTimeMs, queryTimeMs)
      val failureReason = queryResult.failure!!
      LOGGER.info("TPD Service: Fail to get memory data for trace $traceId: ${failureReason.message}")
      throw RuntimeException("TPD Service: Fail to get memory data for trace $traceId: ${failureReason.message}", failureReason)
    }

    val response = queryResult.response!!
    var queryError = false
    response.resultList.forEach {
      if (!it.ok) {
        queryError = true
        LOGGER.warn("TPD Service: Load memory data query error - ${it.failureReason} - ${it.error}")
      }
    }

    response.resultList.filter { it.hasMemoryEvents() }.forEach { converter.populateHeapSet(it.memoryEvents) }

    // Report metrics for OK or ERROR query
    methodStopwatch.stop()
    val methodTimeMs = methodStopwatch.elapsed(TimeUnit.MILLISECONDS)
    val queryStatus =
      if (queryError) TraceProcessorDaemonQueryStats.QueryReturnStatus.QUERY_ERROR
      else TraceProcessorDaemonQueryStats.QueryReturnStatus.OK
    tracker.trackTraceProcessorMemoryData(queryStatus, methodTimeMs, queryTimeMs)
  }

  /**
   * Execute {@code query} on TPD, reloading the trace if has been unloaded (e.g. TPD crashed between loading and the query request).
   */
  private fun executeBatchQuery(traceId: Long,
                                query: QueryBatchRequest,
                                tracker: FeatureTracker): TraceProcessorDaemonQueryResult<QueryBatchResponse> {
    var queryResult = client.queryBatchRequest(query, tracker)

    // If we got a response from TPD, we check if TPD could execute the query correctly or if there was any error we can try to
    // recover from, like for example when the trace was not loaded.
    if (queryResult.response?.resultList?.any { it.failureReason == QueryResult.QueryFailureReason.TRACE_NOT_FOUND} == true) {
      val loadedTrace = loadedTraces[traceId]
      if (loadedTrace != null) {
        // We loaded this trace before, but something happened and the trace is not there anymore. Let's try to reload it:
        loadTrace(traceId, loadedTrace, tracker)
        queryResult = client.queryBatchRequest(query, tracker)
      } else {
        // If we don't know about the target trace we're trying to query against, we replace the result with a failed one.
        return TraceProcessorDaemonQueryResult(
          IllegalStateException("Trace $traceId needs to be loaded before querying."))
      }
    }
    return queryResult
  }

  override fun dispose() {}
}

