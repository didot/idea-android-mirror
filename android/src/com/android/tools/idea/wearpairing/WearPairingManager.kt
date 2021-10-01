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
package com.android.tools.idea.wearpairing

import com.android.annotations.concurrency.Slow
import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.EmulatorConsole
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.HardwareFeature
import com.android.ddmlib.NullOutputReceiver
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.repository.targets.SystemImage
import com.android.tools.idea.AndroidStartupActivity
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.concurrency.AndroidDispatchers.ioThread
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.ddms.DevicePropertyUtil.getManufacturer
import com.android.tools.idea.ddms.DevicePropertyUtil.getModel
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.android.tools.idea.wearpairing.GmscoreHelper.refreshEmulatorConnection
import com.google.common.util.concurrent.Futures
import com.google.wireless.android.sdk.stats.WearPairingEvent
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.net.NetUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.jetbrains.android.util.AndroidBundle.message
import java.util.concurrent.TimeUnit

private val LOG get() = logger<WearPairingManager>()

object WearPairingManager : AndroidDebugBridge.IDeviceChangeListener, AndroidStartupActivity {
  private val updateDevicesChannel = Channel<Unit>(Channel.CONFLATED)

  private var runningJob: Job? = null
  private var model = WearDevicePairingModel()
  private var wizardAction: WizardAction? = null

  data class PhoneWearPair(
    val phone: PairingDevice,
    val wear: PairingDevice,
    var allDevicesOnline: Boolean,
    var hostPort: Int
  )

  private val pairedDevicesTable = hashMapOf<String, PhoneWearPair>()

  private fun isTestMode(): Boolean =
    ApplicationManager.getApplication()?.isUnitTestMode != false

  @UiThread
  override fun runActivity(project: Project, disposable: Disposable) {
    NonUrgentExecutor.getInstance().execute {
      synchronized(this) {
        if (runningJob == null) {
          loadSettings()
        }
      }
    }
  }

  @WorkerThread
  private fun loadSettings() {
    if (isTestMode()) return
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    WearPairingSettings.getInstance().apply {
      loadSettings(pairedDevicesState, pairedDeviceConnectionsState)
    }

    val wizardAction = object : WizardAction {
      override fun restart(project: Project?) {
        WearDevicePairingWizard().show(project, null)
      }
    }
    // Launch WearPairingManager
    setDeviceListListener(WearDevicePairingModel(), wizardAction)
  }

  internal fun loadSettings(pairedDevices: List<PairingDeviceState>, pairedDeviceConnections: List<PairingConnectionsState>) {
    pairedDevicesTable.clear()
    val deviceMap = pairedDevices.associateBy { it.deviceID }

    pairedDeviceConnections.forEach { connection ->
      // Note: At the moment we only support one phone connected to one wear
      assert(connection.wearDeviceIds.size == 1) {"At the moment one phone connected to one wear is supported"}
      val phoneId = connection.phoneId
      val wearId = connection.wearDeviceIds[0]
      val phoneWearPair = PhoneWearPair(
        phone = deviceMap[phoneId]!!.toPairingDevice(ConnectionState.DISCONNECTED),
        wear = deviceMap[wearId]!!.toPairingDevice(ConnectionState.DISCONNECTED),
        allDevicesOnline = false,
        hostPort = 0
      )
      pairedDevicesTable[phoneId] = phoneWearPair
      pairedDevicesTable[wearId] = phoneWearPair
    }
  }

  private fun saveSettings() {
    if (isTestMode()) return
    val pairedDevicesState = mutableListOf<PairingDeviceState>()
    val pairedDeviceConnectionsState = ArrayList<PairingConnectionsState>()

    pairedDevicesTable.forEach { (key, value) ->
      // Only save values where the key is a phone (other entries are just for performance)
      if (key == value.phone.deviceID) {
        pairedDevicesState.add(value.phone.toPairingDeviceState())
        pairedDevicesState.add(value.wear.toPairingDeviceState())
        pairedDeviceConnectionsState.add(
          PairingConnectionsState().apply {
            phoneId = value.phone.deviceID
            wearDeviceIds.add(value.wear.deviceID)
          }
        )
      }
    }

    WearPairingSettings.getInstance().let {
      it.pairedDevicesState = pairedDevicesState
      it.pairedDeviceConnectionsState = pairedDeviceConnectionsState
    }
  }

  @Synchronized
  fun setDeviceListListener(model: WearDevicePairingModel,
                            wizardAction: WizardAction) {
    this.model = model
    this.wizardAction = wizardAction

    AndroidDebugBridge.addDeviceChangeListener(this)
    runningJob?.cancel(null) // Don't reuse pending job, in case it's stuck on a slow operation (eg bridging devices)
    runningJob = GlobalScope.launch(ioThread) {
      for (operation in updateDevicesChannel) {
        try {
          updateListAndForwardState()
        }
        catch (ex: Throwable) {
          LOG.warn(ex)
        }
      }
    }

    updateDevicesChannel.offer(Unit)
  }

  @Synchronized
  fun getPairedDevices(deviceID: String): PhoneWearPair? = pairedDevicesTable[deviceID]

  suspend fun createPairedDeviceBridge(phone: PairingDevice, phoneDevice: IDevice, wear: PairingDevice, wearDevice: IDevice, connect: Boolean = true) {
    LOG.warn("Starting device bridge {connect = $connect}")
    removePairedDevices(wear.deviceID, restartWearGmsCore = false)

    val hostPort = NetUtils.tryToFindAvailableSocketPort(5602)
    val phoneWearPair = PhoneWearPair(
      phone = phone.disconnectedCopy(isPaired = true),
      wear = wear.disconnectedCopy(isPaired = true),
      allDevicesOnline = true,
      hostPort = hostPort
    )

    pairedDevicesTable[phone.deviceID] = phoneWearPair
    pairedDevicesTable[wear.deviceID] = phoneWearPair
    saveSettings()

    if (connect) {
      LOG.warn("Creating adb bridge")
      phoneDevice.runCatching { createForward(hostPort, 5601) }
      wearDevice.runCatching { createReverse(5601, hostPort) }

      wearDevice.refreshEmulatorConnection()
    }
  }

  @Synchronized
  suspend fun removePairedDevices(deviceID: String, restartWearGmsCore: Boolean = true) {
    try {
      val phoneWearPair = pairedDevicesTable[deviceID]
      val phoneDeviceID = phoneWearPair?.phone?.deviceID ?: return
      val wearDeviceID = phoneWearPair.wear.deviceID

      pairedDevicesTable.remove(phoneDeviceID)
      pairedDevicesTable.remove(wearDeviceID)
      saveSettings()

      val connectedDevices = getConnectedDevices()
      connectedDevices[phoneDeviceID]?.apply {
        LOG.warn("[$name] Remove AUTO-forward")
        runCatching { removeForward(5601) } // Make sure there is no manual connection hanging around
        runCatching { if (phoneWearPair.hostPort > 0) removeForward(phoneWearPair.hostPort) }
      }

      connectedDevices[wearDeviceID]?.apply {
        LOG.warn("[$name] Remove AUTO-reverse")
        runCatching { removeReverse(5601) }
        if (restartWearGmsCore) {
          refreshEmulatorConnection()
        }
      }
    }
    catch (ex: Throwable) {
      LOG.warn(ex)
    }

    updateDevicesChannel.offer(Unit)
  }

  suspend fun checkCloudSyncIsEnabled(phone: PairingDevice): Boolean {
    getConnectedDevices()[phone.deviceID]?.also {
      val localIdPattern = "Cloud Sync setting: true"
      val output = it.runShellCommand("dumpsys activity service WearableService | grep '$localIdPattern'")
      return output.isNotEmpty()
    }
    return false
  }

  override fun deviceConnected(device: IDevice) {
    updateDevicesChannel.offer(Unit)
  }

  override fun deviceDisconnected(device: IDevice) {
    updateDevicesChannel.offer(Unit)
  }

  override fun deviceChanged(device: IDevice, changeMask: Int) {
    updateDevicesChannel.offer(Unit)
  }

  @Slow
  internal fun findDevice(deviceID: String): PairingDevice? = getAvailableDevices().second[deviceID]

  @Slow
  private fun getAvailableDevices(): Pair<Map<String, IDevice>, HashMap<String, PairingDevice>> {
    @Suppress("UnstableApiUsage")
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    val deviceTable = hashMapOf<String, PairingDevice>()

    // Collect list of all available AVDs
    AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(false).filter { it.isWearOrPhone() }.forEach { avdInfo ->
      val deviceID = avdInfo.name
      deviceTable[deviceID] = avdInfo.toPairingDevice(deviceID, isPaired(deviceID))
    }

    // Collect list of all connected devices. Enrich data with previous collected AVDs.
    val connectedDevices = getConnectedDevices()
    connectedDevices.forEach { (deviceID, iDevice) ->
      val avdDevice = deviceTable[deviceID]
      // Note: Emulators IDevice "Hardware" feature returns "emulator" (instead of TV, WEAR, etc.), so we only check for physical devices
      if (iDevice.isPhysicalPhone() || avdDevice != null) {
        deviceTable[deviceID] = iDevice.toPairingDevice(deviceID, isPaired(deviceID), avdDevice = avdDevice)
      }
    }

    return Pair(connectedDevices, deviceTable)
  }

  @Slow
  private suspend fun updateListAndForwardState() {
    val (connectedDevices, deviceTable) = getAvailableDevices()

    pairedDevicesTable.forEach { (_, phoneWearPair) ->
      addDisconnectedPairedDeviceIfMissing(phoneWearPair.phone, deviceTable)
      addDisconnectedPairedDeviceIfMissing(phoneWearPair.wear, deviceTable)
    }

    withContext(uiThread(ModalityState.any())) {
      // Broadcast data to listeners
      val (wears, phones) = deviceTable.values.sortedBy { it.displayName }.partition { it.isWearDevice }
      model.phoneList.set(phones)
      model.wearList.set(wears)
      updateSelectedDevice(phones, model.selectedPhoneDevice)
      updateSelectedDevice(wears, model.selectedWearDevice)

      // Don't loop directly on the map, because its values may be updated (ie added/removed)
      pairedDevicesTable.map { it.value }.forEach { phoneWearPair ->
        updateForwardState(phoneWearPair, connectedDevices)
      }
    }
  }

  suspend fun PairingDevice.supportsMultipleWatchConnections(): Boolean =
    getConnectedDevices()[deviceID]?.hasPairingFeature(PairingFeature.MULTI_WATCH_SINGLE_PHONE_PAIRING, null) == true

  private fun findAdb() : AndroidDebugBridge? {
    AndroidDebugBridge.getBridge()?.also {
      return it // Instance found, just return it
    }
    AndroidSdkUtils.findAdb(null).adbPath?.apply {
      AdbService.getInstance().getDebugBridge(this).get(1, TimeUnit.SECONDS) // Create new instance
    }
    return AndroidDebugBridge.getBridge() // Return current instance
  }

  private fun getConnectedDevices(): Map<String, IDevice> {
    val connectedDevices = findAdb()?.devices ?: return emptyMap()
    return connectedDevices
      .filter { it.isEmulator || it.arePropertiesSet() } // Ignore un-populated physical devices (still loading properties)
      .filter { it.isOnline }
      .associateBy { it.getDeviceID() }
  }

  private suspend fun updateForwardState(phoneWearPair: PhoneWearPair, onlineDevices: Map<String, IDevice>) {
    val onlinePhone = onlineDevices[phoneWearPair.phone.deviceID]
    val onlineWear = onlineDevices[phoneWearPair.wear.deviceID]
    val bothDeviceOnline = onlinePhone != null && onlineWear != null
    try {
      if (bothDeviceOnline && !phoneWearPair.allDevicesOnline) {
        // Both devices are online, and before one (or both) were offline. Time to bridge
        createPairedDeviceBridge(phoneWearPair.phone, onlinePhone!!, phoneWearPair.wear, onlineWear!!)
        showReconnectMessageBalloon(phoneWearPair.phone.displayName, phoneWearPair.wear.displayName, wizardAction)
      }
      else if (!bothDeviceOnline && phoneWearPair.allDevicesOnline) {
        // One (or both) devices are offline, and before were online. Show "connection dropped" message
        val offlineName = if (onlinePhone == null) phoneWearPair.phone.displayName else phoneWearPair.wear.displayName
        showConnectionDroppedBalloon(offlineName, phoneWearPair.phone.displayName, phoneWearPair.wear.displayName, wizardAction)
      }
    }
    catch (ex: Throwable) {
      LOG.warn(ex)
    }

    phoneWearPair.allDevicesOnline = bothDeviceOnline
  }

  private fun isPaired(deviceID: String): Boolean = pairedDevicesTable.containsKey(deviceID)

  private suspend fun addDisconnectedPairedDeviceIfMissing(device: PairingDevice, deviceTable: HashMap<String, PairingDevice>) {
    val deviceID = device.deviceID
    if (!deviceTable.contains(deviceID)) {
      if (device.isEmulator) {
        removePairedDevices(deviceID) // Paired AVD was deleted/renamed - Don't add to the list and stop tracking its activity
      }
      else {
        deviceTable[deviceID] = device // Paired physical device - Add to be shown as "disconnected"
      }
    }
  }
}

suspend fun IDevice.executeShellCommand(cmd: String) {
  withContext(ioThread) {
    runCatching {
      executeShellCommand(cmd, NullOutputReceiver())
    }
  }
}

suspend fun IDevice.runShellCommand(cmd: String): String = withContext(ioThread) {
  val outputReceiver = CollectingOutputReceiver()
  runCatching {
    executeShellCommand(cmd, outputReceiver)
  }
  outputReceiver.output.trim()
}

suspend fun IDevice.loadNodeID(): String {
  val localIdPattern = "local: "
  val output = runShellCommand("dumpsys activity service WearableService | grep '$localIdPattern'")
  return output.replace(localIdPattern, "").trim()
}

suspend fun IDevice.loadCloudNetworkID(ignoreNullOutput: Boolean = true): String {
  val cloudNetworkIdPattern = "cloud network id: "
  val output = runShellCommand("dumpsys activity service WearableService | grep '$cloudNetworkIdPattern'")
  return output.replace(cloudNetworkIdPattern, "").run {
    // The Wear Device may have a "null" cloud ID until ADB forward is established and a properly setup phone connects to it.
    if (ignoreNullOutput) replace("null", "") else this
  }.trim()
}

suspend fun IDevice.retrieveUpTime(): Double {
  runCatching {
    val uptimeRes = runShellCommand("cat /proc/uptime")
    return uptimeRes.split(' ').firstOrNull()?.toDoubleOrNull() ?: 0.0
  }
  return 0.0
}

private fun IDevice.toPairingDevice(deviceID: String, isPared: Boolean, avdDevice: PairingDevice?): PairingDevice {
  return PairingDevice(
    deviceID = deviceID,
    displayName = avdDevice?.displayName ?: getDeviceName(name),
    apiLevel = avdDevice?.apiLevel ?: version.featureLevel,
    isEmulator = isEmulator,
    isWearDevice = avdDevice?.isWearDevice ?: supportsFeature(HardwareFeature.WATCH),
    state = if (isOnline) ConnectionState.ONLINE else ConnectionState.OFFLINE,
    hasPlayStore = avdDevice?.hasPlayStore ?: false,
    isPaired = isPared
  ).apply {
    launch = { Futures.immediateFuture(this@toPairingDevice) }
  }
}

private fun AvdInfo.toPairingDevice(deviceID: String, isPared: Boolean): PairingDevice {
  return PairingDevice(
    deviceID = deviceID,
    displayName = displayName,
    apiLevel = androidVersion.featureLevel,
    isEmulator = true,
    isWearDevice = SystemImage.WEAR_TAG == tag,
    state = ConnectionState.OFFLINE,
    hasPlayStore = hasPlayStore(),
    isPaired = isPared
  ).apply {
    launch = { project -> AvdManagerConnection.getDefaultAvdManagerConnection().startAvd(project, this@toPairingDevice) }
  }
}

private fun IDevice.isPhysicalPhone(): Boolean = when {
  isEmulator -> false
  supportsFeature(HardwareFeature.WATCH) -> false
  supportsFeature(HardwareFeature.TV) -> false
  supportsFeature(HardwareFeature.AUTOMOTIVE) -> false
  else -> true
}

internal fun AvdInfo.isWearOrPhone(): Boolean = when (tag) {
  SystemImage.WEAR_TAG -> true
  SystemImage.ANDROID_TV_TAG -> false
  SystemImage.GOOGLE_TV_TAG -> false
  SystemImage.AUTOMOTIVE_TAG -> false
  SystemImage.AUTOMOTIVE_PLAY_STORE_TAG -> false
  SystemImage.CHROMEOS_TAG -> false
  else -> true
}

private fun IDevice.getDeviceName(unknown: String): String {
  val deviceName = "${getManufacturer(this, "")} ${getModel(this, "")}"
  return deviceName.ifBlank { unknown }
}

private fun IDevice.getDeviceID(): String {
  return when {
    avdName != null -> avdName!!
    isEmulator -> EmulatorConsole.getConsole(this)?.avdName ?: name
    else -> this.serialNumber
  }
}

private fun updateSelectedDevice(deviceList: List<PairingDevice>, device: OptionalProperty<PairingDevice>) {
  val currentDevice = device.valueOrNull ?: return
  // Assign the new value from the list, or if missing, update the current state to DISCONNECTED
  device.value = deviceList.firstOrNull { currentDevice.deviceID == it.deviceID } ?: currentDevice.disconnectedCopy()
}

private fun showReconnectMessageBalloon(phoneName: String, wearName: String, wizardAction: WizardAction?) {
  showMessageBalloon(
    message("wear.assistant.device.connection.reconnected.title"),
    message("wear.assistant.device.connection.reconnected.message", wearName, phoneName),
    wizardAction
  )

  WearPairingUsageTracker.log(WearPairingEvent.EventKind.AUTOMATIC_RECONNECT)
}

private fun showConnectionDroppedBalloon(offlineName: String, phoneName: String, wearName: String, wizardAction: WizardAction?) =
  showMessageBalloon(
    message("wear.assistant.device.connection.dropped.title"),
    message("wear.assistant.device.connection.dropped.message", offlineName, wearName, phoneName),
    wizardAction
  )

private fun showMessageBalloon(title: String, text: String, wizardAction: WizardAction?) {
  val hyperlink = object : NotificationHyperlink("launchAssistant", message("wear.assistant.device.connection.balloon.link")) {
    override fun execute(project: Project) {
      wizardAction?.restart(project)
    }
  }

  LOG.warn(text)
  ProjectManager.getInstance().openProjects.forEach {
    AndroidNotification.getInstance(it).showBalloon(title, "$text<br/>", INFORMATION, hyperlink)
  }
}