/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.internal.process.toDeviceDescriptor
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportDeviceManager
import com.android.tools.idea.transport.TransportProxy
import com.android.tools.idea.transport.manager.StreamConnected
import com.android.tools.idea.transport.manager.StreamDisconnected
import com.android.tools.idea.transport.manager.StreamEvent
import com.android.tools.idea.transport.manager.StreamEventQuery
import com.android.tools.idea.transport.manager.TransportStreamChannel
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.android.tools.profiler.proto.Agent
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps track of the currently selected device.
 *
 * The selected device is controlled by [ForegroundProcessDetection].
 */
class DeviceModel(private val processesModel: ProcessesModel) {
  var selectedDevice: DeviceDescriptor? = null
    internal set

  val devices: Set<DeviceDescriptor>
    get() {
      return processesModel.devices
    }
}

/**
 * Listener used to set the feature flag to true or false in the Transport Daemon.
 */
class TransportDeviceManagerListenerImpl : TransportDeviceManager.TransportDeviceManagerListener, ProjectManagerListener {

  override fun projectOpened(project: Project) {
    ApplicationManager.getApplication().messageBus.connect().subscribe(TransportDeviceManager.TOPIC, this)
  }

  override fun onPreTransportDaemonStart(device: Common.Device) { }
  override fun onStartTransportDaemonFail(device: Common.Device, exception: Exception) { }
  override fun onTransportProxyCreationFail(device: Common.Device, exception: Exception) { }
  override fun customizeProxyService(proxy: TransportProxy) { }
  override fun customizeAgentConfig(configBuilder: Agent.AgentConfig.Builder, runConfig: AndroidRunConfigurationBase?) { }

  override fun customizeDaemonConfig(configBuilder: Transport.DaemonConfig.Builder) {
    configBuilder
      .setLayoutInspectorConfig(
        configBuilder.layoutInspectorConfigBuilder.setAutoconnectEnabled(
          StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED.get()
        )
      )
  }
}

/**
 * This class contains information about a foreground process on an Android device.
 */
data class ForegroundProcess(val pid: Int, val processName: String)

interface ForegroundProcessListener {
  fun onNewProcess(device: DeviceDescriptor, foregroundProcess: ForegroundProcess)
}

/**
 * This class is responsible for establishing a connection to the transport, sending commands to it and receiving events from it.
 *
 * The code that tracks the foreground process on the device is a library added to the transport's daemon, which runs on Android.
 * When it receives the start command, the library starts tracking the foreground process, it stops when it receives the stop command.
 * Information about the foreground process is sent by the transport as an event.
 *
 * @param deviceModel At any time reflects on which device we are polling for foreground process.
 */
class ForegroundProcessDetection(
  private val deviceModel: DeviceModel,
  private val transportClient: TransportClient,
  private val foregroundProcessListener: ForegroundProcessListener,
  scope: CoroutineScope,
  workDispatcher: CoroutineDispatcher = AndroidDispatchers.workerThread) {

  /**
   * Maps groupId to connected stream. Each stream corresponds to a device.
   * The groupId is the only information available when the stream disconnects.
   */
  private val connectedStreams = ConcurrentHashMap<Long, TransportStreamChannel>()

  init {
    val manager = TransportStreamManager.createManager(transportClient.transportStub, workDispatcher)

    scope.launch {
      manager.streamActivityFlow()
        .collect { activity ->
          val streamChannel = activity.streamChannel
          if (activity is StreamConnected) {
            connectedStreams[streamChannel.stream.streamId] = streamChannel

            launch {
              // start listening for LAYOUT_INSPECTOR_FOREGROUND_PROCESS events
              streamChannel.eventFlow(StreamEventQuery(eventKind = Common.Event.Kind.LAYOUT_INSPECTOR_FOREGROUND_PROCESS))
                .collect { streamEvent ->
                  val foregroundProcess = streamEvent.toForegroundProcess()
                  if (foregroundProcess != null) {
                    foregroundProcessListener.onNewProcess(streamChannel.stream.device.toDeviceDescriptor(), foregroundProcess)
                  }
                }
            }

            // start polling on the new device, if we're not already polling on another device.
            val streamDevice = activity.streamChannel.stream.device.toDeviceDescriptor()
            if (deviceModel.selectedDevice == null && deviceModel.devices.contains(streamDevice)) {
              startPollingDevice(streamDevice)
            }
          }
          else if (activity is StreamDisconnected) {
            val stream = activity.streamChannel.stream
            connectedStreams.remove(stream.streamId)!!

            if (stream.device.serial == deviceModel.selectedDevice?.serial) {
              deviceModel.selectedDevice = null
            }
          }
        }
    }
  }

  /**
   * Start polling for foreground process on [newDevice].
   *
   * If we are already polling on another device, we send a stop command to that device
   * before sending a start command to the new device.
   */
  fun startPollingDevice(newDevice: DeviceDescriptor) {
    if (newDevice == deviceModel.selectedDevice) {
      return
    }

    val oldStream = connectedStreams.values.find { it.stream.device.serial == deviceModel.selectedDevice?.serial }
    val newStream = connectedStreams.values.find { it.stream.device.serial == newDevice.serial }

    if (oldStream != null) {
      sendStopOnDevicePollingCommand(oldStream.stream)
    }

    if (newStream != null) {
      sendStartOnDevicePollingCommand(newStream.stream)
      deviceModel.selectedDevice = newDevice
    }
  }

  fun startListeningForEvents() {
    // TODO stop/resume on-device polling
  }

  fun stopListeningForEvents() {
    // TODO stop/resume on-device polling
  }

  /**
   * Tell the device connected to this stream to start the on-device detection of foreground process.
   */
  private fun sendStartOnDevicePollingCommand(stream: Common.Stream) {
    sendCommand(Commands.Command.CommandType.START_TRACKING_FOREGROUND_PROCESS, stream.streamId)
  }

  /**
   * Tell the device connected to this stream to stop the on-device detection of foreground process.
   */
  private fun sendStopOnDevicePollingCommand(stream: Common.Stream) {
    sendCommand(Commands.Command.CommandType.STOP_TRACKING_FOREGROUND_PROCESS, stream.streamId)
  }

  /**
   * Send a command to the transport.
   */
  private fun sendCommand(commandType: Commands.Command.CommandType, streamId: Long) {
    val command = Commands.Command
      .newBuilder()
      .setType(commandType)
      .setStreamId(streamId)
      .build()

    transportClient.transportStub.execute(
      Transport.ExecuteRequest.newBuilder().setCommand(command).build()
    )
  }
}

private fun StreamEvent.toForegroundProcess(): ForegroundProcess? {
  return if (
    !event.hasLayoutInspectorForegroundProcess() ||
    event.layoutInspectorForegroundProcess.pid == null ||
    event.layoutInspectorForegroundProcess.processName == null
  ) {
    null
  }
  else {
    val foregroundProcessEvent = event.layoutInspectorForegroundProcess
    val pid = foregroundProcessEvent.pid.toInt()
    val packageName = foregroundProcessEvent.processName
    ForegroundProcess(pid, packageName)
  }
}
