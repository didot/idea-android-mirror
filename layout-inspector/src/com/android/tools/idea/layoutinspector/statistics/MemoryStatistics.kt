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
package com.android.tools.idea.layoutinspector.statistics

import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorMemory

// 1 Mb in bytes
@VisibleForTesting
const val ONE_MB = 1000000

class MemoryStatistics {
  /**
   * The size in bytes required by the model on the initial display.
   */
  private var initialModelSize: Long = 0

  /**
   * The time in milliseconds it took to compute the size of the model on the initial display.
   */
  private var initialModelTime: Long = 0

  /**
   * The size in bytes required by the largest model in the session.
   */
  private var largestModelSize: Long = 0

  /**
   * The time in milliseconds it took to compute the size of the largest model in the session.
   *
   * This number can be used to determine if we are using too much time on computing the size in the field.
   */
  private var largestModelTime: Long = 0

  fun start() {
    initialModelSize = 0L
    initialModelTime = 0L
    largestModelSize = 0L
    largestModelTime = 0L
  }

  /**
   * Save the session data recorded since [start].
   */
  fun save(data: DynamicLayoutInspectorMemory.Builder) {
    data.initialSnapshotBuilder.captureSizeMb = initialModelSize / ONE_MB
    data.initialSnapshotBuilder.measurementDurationMs = initialModelTime
    data.largestSnapshotBuilder.captureSizeMb = largestModelSize / ONE_MB
    data.largestSnapshotBuilder.measurementDurationMs = largestModelTime
  }

  fun recordModelSize(size: Long, time: Long) {
    if (size > 0 && initialModelSize == 0L) {
      initialModelSize = size
      initialModelTime = time
    }
    if (size > largestModelSize) {
      largestModelSize = size
      largestModelTime = time
    }
  }
}
