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
package com.android.tools.idea.logcat.filters

import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.logcat.messages.formatMessageForFilter
import java.time.ZoneId

/**
 * A [LogcatFilter] that matches the full text rendering of a [LogCatMessage] against a given regex.
 */
internal class FullMessageRegexFilter(regex: String, private val zoneId: ZoneId = ZoneId.systemDefault()) : LogcatFilter {
  private val regex = regex.toRegex(RegexOption.IGNORE_CASE)

  override fun filter(messages: List<LogCatMessage>) =
    messages.filter { regex.containsMatchIn(it.formatMessageForFilter(zoneId)) }
}
