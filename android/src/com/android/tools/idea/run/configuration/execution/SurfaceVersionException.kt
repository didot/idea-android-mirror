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
package com.android.tools.idea.run.configuration.execution

import com.intellij.execution.ExecutionException

class SurfaceVersionException(minVersion: Int, currentVersion: Int, isEmulator: Boolean) :
  ExecutionException(
    "<html><p>Device software is out of date. Check for updates using the <b>Play Store app</b>." +
    (if (isEmulator) "<br/>You can also check to see if there is an update available for the emulator system image via <b>SDK Manager</b>"
    else "") + "</html>",
    IllegalStateException("minVersion: $minVersion currentVersion: $currentVersion"))
