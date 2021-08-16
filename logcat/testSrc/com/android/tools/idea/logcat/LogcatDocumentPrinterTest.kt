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

import com.android.ddmlib.Log.LogLevel
import com.android.ddmlib.Log.LogLevel.ASSERT
import com.android.ddmlib.Log.LogLevel.DEBUG
import com.android.ddmlib.Log.LogLevel.ERROR
import com.android.ddmlib.Log.LogLevel.INFO
import com.android.ddmlib.Log.LogLevel.VERBOSE
import com.android.ddmlib.Log.LogLevel.WARN
import com.android.ddmlib.logcat.LogCatHeader
import com.android.ddmlib.logcat.LogCatMessage
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

private val timestamp = Instant.ofEpochMilli(1000)

/**
 * Tests for [LogcatDocumentPrinter]
 */
@RunsInEdt
class LogcatDocumentPrinterTest {
  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  val edtRule = EdtRule()

  private val logcatColors = LogcatColors()
  private val document by lazy { (EditorFactory.getInstance() as EditorFactoryImpl).createDocument(true) }
  private val printer by lazy { LogcatDocumentPrinter(projectRule.project, document, logcatColors, ZoneId.of("Asia/Yerevan")) }

  @Test
  fun print_alignment() {
    printer.print(LogCatMessage(LogCatHeader(WARN, 1, 2, "app", "tag", timestamp), "message"))
    printer.print(LogCatMessage(LogCatHeader(WARN, 12345, 12345, "app", "tag", timestamp), "message"))
    printer.print(LogCatMessage(LogCatHeader(WARN, 12345, 12345, "long app", "tag", timestamp), "message"))
    printer.print(LogCatMessage(LogCatHeader(WARN, 12345, 12345, "app", "long tag", timestamp), "message"))
    printer.print(LogCatMessage(LogCatHeader(WARN, 12345, 12345, "long app", "long tag", timestamp), "message"))
    printer.print(LogCatMessage(LogCatHeader(WARN, 1, 2, "app", "tag", timestamp), "message"))

    assertThat(document.text).isEqualTo("""
    1970-01-01 04:00:01.000      1-2      tag app W message
    1970-01-01 04:00:01.000  12345-12345      app W message
    1970-01-01 04:00:01.000  12345-12345      long app W message
    1970-01-01 04:00:01.000  12345-12345  long tag app      W message
    1970-01-01 04:00:01.000  12345-12345           long app W message
    1970-01-01 04:00:01.000      1-2      tag      app      W message

    """.trimIndent())
  }

  @Test
  fun print_omitSameTag() {
    printer.print(LogCatMessage(LogCatHeader(WARN, 1, 2, "app", "tag1", timestamp), "message1"))
    printer.print(LogCatMessage(LogCatHeader(WARN, 1, 2, "app", "tag1", timestamp), "message2"))
    printer.print(LogCatMessage(LogCatHeader(WARN, 1, 2, "app", "tag1", timestamp), "message3"))
    printer.print(LogCatMessage(LogCatHeader(WARN, 1, 2, "app", "tag2", timestamp), "message4"))

    assertThat(document.text).isEqualTo("""
    1970-01-01 04:00:01.000      1-2      tag1 app W message1
    1970-01-01 04:00:01.000      1-2           app W message2
    1970-01-01 04:00:01.000      1-2           app W message3
    1970-01-01 04:00:01.000      1-2      tag2 app W message4

    """.trimIndent())
  }

  @Test
  fun print_levelColors() {
    for (level in LogLevel.values()) {
      printer.print(LogCatMessage(LogCatHeader(level, 1, 2, "app", "tag", timestamp), "message"))
    }

    val markupModel = DocumentMarkupModel.forDocument(document, projectRule.project, false)
    // Filter the ranges corresponding to a LogLevel and build a map level -> color.
    val textAttributes = markupModel.allHighlighters.filter { getRangeText(it).matches(" [VDIWEA] ".toRegex()) }
      .associate { getRangeText(it).trim() to it.getTextAttributes(null) }

    assertThat(textAttributes).containsExactly(
      "V", logcatColors.getLogLevelColor(VERBOSE),
      "D", logcatColors.getLogLevelColor(DEBUG),
      "I", logcatColors.getLogLevelColor(INFO),
      "W", logcatColors.getLogLevelColor(WARN),
      "E", logcatColors.getLogLevelColor(ERROR),
      "A", logcatColors.getLogLevelColor(ASSERT),
    )
  }

  @Test
  fun print_tagColors() {
    // Print with 10 different tags and then assert that there are 10 highlight ranges corresponding to the tags with the proper color.
    val numTags = 10
    for (t in 1..numTags) {
      printer.print(LogCatMessage(LogCatHeader(INFO, 1, 2, "app", "tag$t", timestamp), "message"))
    }

    val markupModel = DocumentMarkupModel.forDocument(document, projectRule.project, false)
    // Filter the ranges corresponding to a tag and build a map tag -> color.
    val tagColors = markupModel.allHighlighters.filter { getRangeText(it).matches(" tag\\d+ *".toRegex()) }
      .associate { getRangeText(it).trim() to it.getTextAttributes(null) }
    assertThat(tagColors).hasSize(numTags)
    tagColors.forEach { (tag, color) ->
      assertThat(color).isEqualTo(logcatColors.getTagColor(tag))
    }
  }
}

private fun getRangeText(range: RangeHighlighter) =
  range.document.text.substring(range.startOffset, range.endOffset)
