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
package com.android.tools.idea.logcat.service

import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.message.LogcatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Reads and clears a logcat from a device
 */
internal interface LogcatService {
  suspend fun readLogcat(device: Device) : Flow<List<LogcatMessage>>

  suspend fun clearLogcat(device: Device)
}