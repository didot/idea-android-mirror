/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.appinspection.api

import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.profiler.proto.Common

/**
 * The amount of time tests will wait for async calls and events to trigger. It is used primarily in asserting latches and futures.
 *
 * These calls normally take way less than the allotted time below, on the order of tens of milliseconds. The timeout we chose is extremely
 * generous and was only chosen to fail tests faster if something goes wrong. If you hit this timeout, please don't just increase it but
 * investigate the root cause.
 */
const val ASYNC_TIMEOUT_MS: Long = 10000

const val INSPECTOR_ID = "test.inspector"

/**
 * A collection of utility functions for inspection tests.
 */
object AppInspectionTestUtils {

  /**
   * Creates a successful service response proto.
   */
  fun createSuccessfulServiceResponse(commandId: Int): AppInspection.AppInspectionEvent = AppInspection.AppInspectionEvent.newBuilder()
    .setCommandId(commandId)
    .setResponse(
      AppInspection.ServiceResponse.newBuilder()
        .setStatus(AppInspection.ServiceResponse.Status.SUCCESS)
        .build()
    )
    .build()

  /**
   * Creates an [AppInspectionEvent] with the provided [data], [commandId], and inspector [name].
   */
  fun createRawAppInspectionEvent(
    data: ByteArray,
    commandId: Int = 0,
    name: String = INSPECTOR_ID
  ): AppInspection.AppInspectionEvent = AppInspection.AppInspectionEvent.newBuilder()
    .setCommandId(commandId)
    .setRawEvent(
      AppInspection.RawEvent.newBuilder()
        .setInspectorId(name)
        .setContent(ByteString.copyFrom(data))
        .build()
    )
    .build()

  /**
   * Creates an [Common.Event] with a raw app inspection event with the provided [data].
   */
  fun createRawEvent(data: ByteString, ts: Long): Common.Event =
    Common.Event.newBuilder()
      .setKind(Common.Event.Kind.APP_INSPECTION)
      .setTimestamp(ts)
      .setIsEnded(true)
      .setAppInspectionEvent(createRawAppInspectionEvent(data.toByteArray(), name = "test.inspector"))
      .build()
}