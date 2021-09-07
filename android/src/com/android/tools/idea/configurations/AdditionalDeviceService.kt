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
package com.android.tools.idea.configurations

import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRound
import com.android.resources.ScreenSize
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.tools.idea.avdmanager.AvdScreenData
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import kotlin.math.sqrt

/**
 * Provide the additional [Device]s which exist and be used in Android Studio only.
 * These devices can also be found in the return list of [ConfigurationManager.getDevices].
 */
class AdditionalDeviceService: DumbAware {

  private val windowDevices: List<Device> by lazy { createWindowDevices() }

  companion object {
    @JvmStatic
    fun getInstance(): AdditionalDeviceService? {
      return ApplicationManager.getApplication().getService(AdditionalDeviceService::class.java)
    }
  }

  fun getWindowSizeDevices(): List<Device> = windowDevices
}


data class WindowSizeData(val id: String, val name: String, val widthDp: Double, val heightDp: Double, val density: Density) {
  val widthPx: Int = widthDp.toPx(density)
  val heightPx: Int = heightDp.toPx(density)
}

/**
 * The device definitions used by Android Studio only
 */
val PREDEFINED_WINDOW_SIZES_DEFINITIONS: List<WindowSizeData> = Density.XXHIGH.let { density -> listOf(
  WindowSizeData("_device_class_phone", "Phone", 360.0, 640.0, density),
  WindowSizeData("_device_class_foldable", "Foldable", 673.5, 841.0, density),
  WindowSizeData("_device_class_tablet", "Tablet", 1280.0, 800.0, density),
  WindowSizeData("_device_class_desktop", "Desktop", 1920.0, 1080.0, density))
}

private fun createWindowDevices(): List<Device> =
  PREDEFINED_WINDOW_SIZES_DEFINITIONS.map { windowSizeDef ->
    val deviceHardware = Hardware().apply {
      screen = Screen().apply {
        xDimension = windowSizeDef.widthPx
        yDimension = windowSizeDef.heightPx
        pixelDensity = windowSizeDef.density

        val dpi = pixelDensity.dpiValue.toDouble()
        val width = xDimension / dpi
        val height = yDimension / dpi
        diagonalLength = sqrt(width * width + height * height)
        size = ScreenSize.getScreenSize(diagonalLength)
        ratio = AvdScreenData.getScreenRatio(xDimension, yDimension)
        screenRound = ScreenRound.NOTROUND
        chin = 0
      }
    }

    Device.Builder().apply {
      setTagId("")
      setId(windowSizeDef.id)
      setName(windowSizeDef.name)
      setManufacturer("")
      addSoftware(Software())
      addState(State().apply {
        isDefaultState = true

        hardware = deviceHardware
        name = "portrait"
        orientation = ScreenOrientation.PORTRAIT
      })
      addState(State().apply {
        hardware = deviceHardware
        name = "landscape"
        orientation = ScreenOrientation.LANDSCAPE
      })
    }.build()
  }