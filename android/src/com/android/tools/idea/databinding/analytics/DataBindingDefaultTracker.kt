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
package com.android.tools.idea.databinding.analytics

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.databinding.analytics.api.DataBindingTracker
import com.android.tools.idea.stats.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DataBindingEvent
import com.intellij.openapi.project.Project

/**
 * Tracker implementation that offers default (negative) implementation of [DataBindingTracker].
 * This class should only ever be invoked when data binding is disabled.
 */
class DataBindingDefaultTracker private constructor(private val project: Project): DataBindingTracker {

  /**
   * This method would only ever be called when data binding module is not enabled. Always track false.
   */
  override fun trackDataBindingEnabled() {
    val studioEventBuilder = AndroidStudioEvent.newBuilder().setKind(AndroidStudioEvent.EventKind.DATA_BINDING).setDataBindingEvent(
      DataBindingEvent.newBuilder().setType(DataBindingEvent.EventType.DATA_BINDING_SYNC_EVENT).setPollMetadata(
        DataBindingEvent.DataBindingPollMetadata.newBuilder().setDataBindingEnabled(false).build()))
    UsageTracker.log(studioEventBuilder.withProjectId(project))
  }

  override fun trackPolledMetaData() {
  }

  override fun trackDataBindingCompletion(eventType: DataBindingEvent.EventType, context: DataBindingEvent.DataBindingContext) {
  }
}