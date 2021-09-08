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
package com.android.tools.idea.logcat

import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.AndroidDispatchers.ioThread
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.annotations.TestOnly
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

private val DATE_TIME_FORMATTER = DateTimeFormatterBuilder()
  .append(DateTimeFormatter.ISO_LOCAL_DATE)
  .appendLiteral(' ')
  .appendValue(ChronoField.HOUR_OF_DAY, 2)
  .appendLiteral(':')
  .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
  .appendLiteral(':')
  .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
  .appendFraction(ChronoField.MILLI_OF_SECOND, 3, 3, true)
  .toFormatter(Locale.ROOT)

const val CHANNEL_CAPACITY = 10

/**
 * Prints formatted [LogCatMessage]s to a [Document] with coloring provided by a [LogcatColors].
 */
internal class LogcatDocumentPrinter(
  project: Project,
  parentDisposable: Disposable,
  private val document: Document,
  private val logcatColors: LogcatColors,
  private val zoneId: ZoneId = ZoneId.systemDefault()
) {
  private val markupModel = DocumentMarkupModel.forDocument(document, project, true)
  private val channel = Channel<List<LogCatMessage>>(CHANNEL_CAPACITY)

  // Keeps track of the previous tag so we can omit on consecutive lines
  // TODO(aalbert): This was borrowed from Pidcat. Should we do it too? Should we also do it for app?
  private var previousTag: String? = null

  // Keeps track of the max app name & tag so we can align the log level & message. Pidcat allows 23 chars for the tag and doesn't show the
  // app name but the alignment makes it look much prettier.
  private var maxAppNameLength = 0
  private var maxTagNameLength = 0

  init {
    AndroidCoroutineScope(parentDisposable, ioThread).launch {
      while (true) {
        processMessages(channel.receive())
      }
    }
  }

  internal suspend fun appendMessages(messages: List<LogCatMessage>) {
    channel.send(messages)
  }

  private suspend fun processMessages(messages: List<LogCatMessage>) {
    val buffer = MessageTextBuffer()
    for (message in messages) {
      val header = message.header
      buffer.append(DATE_TIME_FORMATTER.format(LocalDateTime.ofInstant(header.timestamp, zoneId)))
      // According to /proc/sys/kernel/[pid_max/threads-max], the max values of pid/tid are 32768/57136
      buffer.append(" %6d-%-6d".format(header.pid, header.tid))

      val tag = if (previousTag == header.tag) "" else header.tag.also { previousTag = header.tag }
      if (maxTagNameLength < header.tag.length) {
        maxTagNameLength = header.tag.length
      }
      if (maxAppNameLength < header.appName.length) {
        maxAppNameLength = header.appName.length
      }

      buffer.append(" %-${maxTagNameLength}s".format(tag), logcatColors.getTagColor(header.tag))
      buffer.append(" %-${maxAppNameLength}s".format(header.appName))
      buffer.append(" ${header.logLevel.priorityLetter} ", logcatColors.getLogLevelColor(header.logLevel))
      buffer.append("${message.message}\n")
    }

    withContext(AndroidDispatchers.uiThread) {
      appendToDocument(buffer)
    }
  }

  private fun appendToDocument(buffer: MessageTextBuffer) {
    document.insertString(document.textLength, buffer.text)

    // Document has cyclic buffer so we need get document.textLength again after inserting text.
    val start = document.textLength - buffer.text.length
    buffer.ranges.forEach {
      val rangeStart = start + it.start
      // Under extreme conditions, we could be inserting text that is longer than the cyclic buffer.
      // TODO(aalbert): Consider optimizing by truncating text to not be longer than cyclic buffer.
      if (rangeStart >= 0) {
        markupModel.addRangeHighlighter(
          rangeStart, start + it.end, HighlighterLayer.SYNTAX, it.textAttributes, HighlighterTargetArea.EXACT_RANGE)
      }
    }
  }

  @ExperimentalCoroutinesApi
  @TestOnly
  internal fun isChannelEmpty() = channel.isEmpty
}

private class HighlighterRange(val start: Int, val end: Int, val textAttributes: TextAttributes)

private class MessageTextBuffer {
  private val stringBuilder = StringBuilder()

  val text: String get() = stringBuilder.toString()

  val ranges = mutableListOf<HighlighterRange>()

  fun append(text: String, textAttributes: TextAttributes? = null) {
    val start = stringBuilder.length
    stringBuilder.append(text)
    textAttributes?.let { ranges.add(HighlighterRange(start, start + text.length, it)) }
  }
}