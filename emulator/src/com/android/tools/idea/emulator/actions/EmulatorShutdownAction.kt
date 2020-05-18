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
package com.android.tools.idea.emulator.actions

import com.android.emulator.control.VmRunState
import com.android.tools.idea.emulator.EmulatorController
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Initiates a graceful shutdown of the Emulator.
 */
class EmulatorShutdownAction : AbstractEmulatorAction() {

  override fun actionPerformed(event: AnActionEvent) {
    val emulatorController: EmulatorController = getEmulatorController(event) ?: return
    val vmRunState = VmRunState.newBuilder().setState(VmRunState.RunState.SHUTDOWN).build()
    // Emulator shutdown takes few seconds due to snapshot creation.
    // The "Shutting down..." indicator will go away together with the Emulator tab.
    getEmulatorView(event)?.showLongRunningOperationIndicator("Shutting down...")
    emulatorController.setVmState(vmRunState)
  }
}