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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.view

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.launch.LaunchParameters
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeLayoutInspectorClient
import com.android.tools.idea.layoutinspector.snapshots.saveAppInspectorSnapshot
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.intellij.openapi.progress.ProgressManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetAllParametersResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetComposablesResponse
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.CaptureSnapshotCommand
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.ErrorEvent
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Event
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesCommand
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesResponse
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.LayoutEvent
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.PropertiesEvent
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Screenshot
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StartFetchCommand
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StopFetchCommand
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.WindowRootsEvent
import java.nio.file.Path

const val VIEW_LAYOUT_INSPECTOR_ID = "layoutinspector.view.inspection"
private val JAR = AppInspectorJar("layoutinspector-view-inspection.jar",
                                  developmentDirectory = "bazel-bin/tools/base/dynamic-layout-inspector/agent/appinspection/",
                                  releaseDirectory = "plugins/android/resources/app-inspection/")

/**
 * The client responsible for interacting with the view layout inspector running on the target
 * device.
 *
 * @param scope A coroutine scope used for asynchronously responding to events coming in from
 *     the inspector and other miscellaneous coroutine tasks.
 *
 * @param messenger The messenger that lets us communicate with the view inspector.
 *
 * @param composeInspector An inspector which, if provided, lets us fetch additional data useful
 *     for compose related values contained within our view tree.
 */
class ViewLayoutInspectorClient(
  model: InspectorModel,
  private val processDescriptor: ProcessDescriptor,
  private val scope: CoroutineScope,
  private val messenger: AppInspectorMessenger,
  private val composeInspector: ComposeLayoutInspectorClient?,
  private val fireError: (String) -> Unit = {},
  private val fireTreeEvent: (Data) -> Unit = {},
) {

  /**
   * Data packaged up and sent via [fireTreeEvent], needed for constructing the tree view in the
   * layout inspector.
   */
  class Data(
    val generation: Int,
    val rootIds: List<Long>,
    val viewEvent: LayoutEvent,
    val composeEvent: GetComposablesResponse?
  )

  companion object {
    /**
     * Helper function for launching the view layout inspector and creating a client to interact
     * with it.
     *
     * @param eventScope Scope which will be used for processing incoming inspector events. It's
     *     expected that this will be a scope associated with a background dispatcher.
     */
    suspend fun launch(apiServices: AppInspectionApiServices,
                       process: ProcessDescriptor,
                       model: InspectorModel,
                       eventScope: CoroutineScope,
                       composeLayoutInspectorClient: ComposeLayoutInspectorClient?,
                       fireError: (String) -> Unit,
                       fireTreeEvent: (Data) -> Unit): ViewLayoutInspectorClient {
      // Set force = true, to be more aggressive about connecting the layout inspector if an old version was
      // left running for some reason. This is a better experience than silently falling back to a legacy client.
      val params = LaunchParameters(process, VIEW_LAYOUT_INSPECTOR_ID, JAR, model.project.name, force = true)
      val messenger = apiServices.launchInspector(params)
      return ViewLayoutInspectorClient(model, process, eventScope, messenger, composeLayoutInspectorClient, fireError, fireTreeEvent)
    }
  }

  val propertiesCache = LiveViewPropertiesCache(this, model)

  /**
   * Whether this client is continuously receiving layout events or not.
   *
   * This will be true between calls to `startFetching(continuous = true)` and
   * `stopFetching`.
   */
  private var isFetchingContinuously: Boolean = false
    set(value) {
      field = value
      propertiesCache.allowFetching = value
      composeInspector?.parametersCache?.allowFetching = value
      lastData.clear()
      lastProperties.clear()
      lastComposeParameters.clear()
    }

  private var generation = 0 // Update the generation each time we get a new LayoutEvent
  private val currRoots = mutableListOf<Long>()

  var lastData: MutableMap<Long, Data> = mutableMapOf()
  var lastProperties: MutableMap<Long, PropertiesEvent> = mutableMapOf()
  var lastComposeParameters: MutableMap<Long, GetAllParametersResponse> = mutableMapOf()

  init {
    scope.launch {
      // Layout events are very expensive to process and we may get a bunch of intermediate layouts while still processing an older one.
      // We skip over rendering these obsolete frames, which makes the UX feel much more responsive.
      val recentLayouts = mutableMapOf<Long, LayoutEvent>() // Map of root IDs to their layout
      messenger.eventFlow
        .map { eventBytes -> Event.parseFrom(eventBytes) }
        .onEach { event ->
          if (event.specializedCase == Event.SpecializedCase.LAYOUT_EVENT) recentLayouts[event.layoutEvent.rootView.id] = event.layoutEvent
        }
        .buffer(capacity = UNLIMITED) // Buffering allows event collection to keep happening even as we're still processing older ones
        .filter { event ->
          event.specializedCase != Event.SpecializedCase.LAYOUT_EVENT || event.layoutEvent === recentLayouts[event.layoutEvent.rootView.id]
        }
        .collect { event ->
          when (event.specializedCase) {
            Event.SpecializedCase.ERROR_EVENT -> handleErrorEvent(event.errorEvent)
            Event.SpecializedCase.ROOTS_EVENT -> handleRootsEvent(event.rootsEvent)
            Event.SpecializedCase.LAYOUT_EVENT -> {
              recentLayouts.remove(event.layoutEvent.rootView.id)
              handleLayoutEvent(event.layoutEvent)
            }
            Event.SpecializedCase.PROPERTIES_EVENT -> handlePropertiesEvent(event.propertiesEvent)
            else -> error { "Unhandled event case: ${event.specializedCase}" }
          }
        }
    }
  }

  suspend fun startFetching(continuous: Boolean) {
    isFetchingContinuously = continuous
    messenger.sendCommand {
      startFetchCommand = StartFetchCommand.newBuilder().apply {
        this.continuous = continuous
        skipSystemViews = TreeSettings.skipSystemNodesInAgent
      }.build()
    }
  }

  fun updateScreenshotType(type: Screenshot.Type?, scale: Float = 1.0f) {
    scope.launch {
      messenger.sendCommand {
        updateScreenshotTypeCommandBuilder.apply {
          if (type != null) {
            this.type = type
          }
          this.scale = if (scale == 0f) 1f else scale
        }
      }
    }
  }

  suspend fun stopFetching() {
    isFetchingContinuously = false
    messenger.sendCommand {
      stopFetchCommand = StopFetchCommand.getDefaultInstance()
    }
  }

  suspend fun getProperties(rootViewId: Long, viewId: Long): GetPropertiesResponse {
    val response = messenger.sendCommand {
      getPropertiesCommand = GetPropertiesCommand.newBuilder().apply {
        this.rootViewId = rootViewId
        this.viewId = viewId
      }.build()
    }
    return response.getPropertiesResponse
  }

  fun disconnect() {
    messenger.scope.cancel()
  }

  private fun handleErrorEvent(errorEvent: ErrorEvent) {
    fireError(errorEvent.message)
  }

  private fun handleRootsEvent(rootsEvent: WindowRootsEvent) {
    currRoots.clear()
    currRoots.addAll(rootsEvent.idsList)

    propertiesCache.retain(currRoots)
    composeInspector?.parametersCache?.retain(currRoots)
    lastData.keys.retainAll(currRoots)
    lastComposeParameters.keys.retainAll(currRoots)
    lastProperties.keys.retainAll(currRoots)
  }

  private suspend fun handleLayoutEvent(layoutEvent: LayoutEvent) {
    generation++
    propertiesCache.clearFor(layoutEvent.rootView.id)
    composeInspector?.parametersCache?.clearFor(layoutEvent.rootView.id)

    val composablesResponse = composeInspector?.getComposeables(layoutEvent.rootView.id, generation)

    val data = Data(
      generation,
      currRoots,
      layoutEvent,
      composablesResponse)
    if (!isFetchingContinuously) {
      lastData[layoutEvent.rootView.id] = data
    }
    fireTreeEvent(data)
  }

  private suspend fun handlePropertiesEvent(propertiesEvent: PropertiesEvent) {
    if (!isFetchingContinuously) {
      lastProperties[propertiesEvent.rootId] = propertiesEvent
    }
    propertiesCache.setAllFrom(propertiesEvent)

    composeInspector?.let {
      val composeParameters = it.getAllParameters(propertiesEvent.rootId)
      if (!isFetchingContinuously) {
        lastComposeParameters[propertiesEvent.rootId] = composeParameters
      }
      it.parametersCache.setAllFrom(composeParameters)
    }
  }

  @Slow
  fun saveSnapshot(path: Path) {
    if (isFetchingContinuously) {
      fetchAndSaveSnapshot(path)
    }
    else {
      saveAppInspectorSnapshot(path, lastData, lastProperties, lastComposeParameters, processDescriptor)
    }
  }

  private fun fetchAndSaveSnapshot(path: Path) {
    try {
      val job = scope.launch { fetchAndSaveSnapshotAsync(path) } // TODO: error handling
      // Watch for the progress indicator to be canceled and cancel the fetchAndSave job if so.
      val progress = ProgressManager.getInstance().progressIndicator
      scope.launch {
        while (true) {
          delay(300)
          if (progress.isCanceled) {
            job.cancel()
            break
          }
          if (!job.isActive) {
            break
          }
        }
      }
      job.asCompletableFuture().get()
    }
    catch (cancellationException: CancellationException) {
      // ignore
      return
    }
  }

  private suspend fun fetchAndSaveSnapshotAsync(path: Path) {
    messenger.sendCommand {
      captureSnapshotCommand = CaptureSnapshotCommand.newBuilder().apply {
        // TODO: support bitmap
        screenshotType = Screenshot.Type.SKP
      }.build()
    }.captureSnapshotResponse?.let { snapshotResponse ->
      val composeInfo = composeInspector?.let { composeInspector ->
        generation++
        snapshotResponse.windowRoots.idsList.associateWith { id ->
          Pair(composeInspector.getComposeables(id, generation),
               composeInspector.getAllParameters(id))
        }
      } ?: mapOf()

      saveAppInspectorSnapshot(path, snapshotResponse, composeInfo, processDescriptor)
    } ?: throw Exception()
  }
}

/**
 * Convenience method for wrapping a specific view-inspector command inside a parent
 * app inspection command.
 */
private suspend fun AppInspectorMessenger.sendCommand(initCommand: Command.Builder.() -> Unit): Response {
  val command = Command.newBuilder()
  command.initCommand()
  return Response.parseFrom(sendRawCommand(command.build().toByteArray()))
}
