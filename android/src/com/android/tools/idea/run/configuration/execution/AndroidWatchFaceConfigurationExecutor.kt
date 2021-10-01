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
package com.android.tools.idea.run.configuration.execution

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.IDevice
import com.android.ddmlib.NullOutputReceiver
import com.android.tools.deployer.model.component.AppComponent
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProgressIndicator
import java.util.concurrent.TimeUnit


class AndroidWatchFaceConfigurationExecutor(private val environment: ExecutionEnvironment) :
  AndroidWearConfigurationExecutorBase(environment) {

  companion object {
    private val SHOW_WATCH_FACE_COMMAND =
      "am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-watchface"
  }

  @WorkerThread
  override fun doOnDevices(devices: List<IDevice>, indicator: ProgressIndicator): RunContentDescriptor? {
    val isDebug = environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID
    if (isDebug && devices.size > 1) {
      throw ExecutionException("Debugging is allowed only for single device")
    }
    val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console

    val applicationInstaller = ApplicationInstaller(configuration)
    val mode = if (isDebug) AppComponent.Mode.DEBUG else AppComponent.Mode.RUN
    devices.forEach {
      indicator.checkCanceled()
      val app = applicationInstaller.installAppOnDevice(it, indicator, console)
      val receiver = AndroidLaunchReceiver(indicator, console)
      app.activateComponent(configuration.componentType, configuration.componentName!!, mode, receiver)
      console.print("$ adb shell command $SHOW_WATCH_FACE_COMMAND", ConsoleViewContentType.NORMAL_OUTPUT)
      it.executeShellCommand(SHOW_WATCH_FACE_COMMAND, NullOutputReceiver(), 5, TimeUnit.SECONDS)
    }
    indicator.checkCanceled()
    val runContentDescriptor = if (isDebug) {
      DebugSessionStarter(environment).attachDebuggerToClient(devices.single(), console, indicator)
    }
    else {
      invokeAndWaitIfNeeded { showRunContent(DefaultExecutionResult(console, EmptyProcessHandler()), environment) }
    }

    return runContentDescriptor
  }
}