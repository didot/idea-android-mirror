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

import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.KVALUE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.REGEX_KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.REGEX_KVALUE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.STRING_KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.STRING_KVALUE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.VALUE
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [LogcatFilterSyntaxHighlighter]
 */
class LogcatFilterSyntaxHighlighterTest {
  @Test
  fun factory() {
    assertThat(LogcatFilterSyntaxHighlighterFactory().getSyntaxHighlighter(project = null, virtualFile = null))
      .isInstanceOf(LogcatFilterSyntaxHighlighter::class.java)
  }

  @Test
  fun getTokenHighlights() {
    for (token in listOf(KEY, KVALUE, STRING_KEY, STRING_KVALUE, REGEX_KEY, REGEX_KVALUE, VALUE)) {
      assertThat(LogcatFilterSyntaxHighlighter().getTokenHighlights(token)).isNotEmpty()
    }
  }
}
