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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.LogcatBundle.message
import com.android.tools.idea.logcat.PACKAGE_NAMES_PROVIDER_KEY
import com.android.tools.idea.logcat.TAGS_PROVIDER_KEY
import com.android.tools.idea.logcat.filters.LogcatFilter.Companion.MY_PACKAGE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.REGEX_KVALUE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.STRING_KVALUE
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.settings.AndroidLogcatSettings
import com.android.tools.idea.logcat.util.AndroidProjectDetector
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.patterns.PlatformPatterns.or
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.TokenType.ERROR_ELEMENT
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.VisibleForTesting

private const val MY_PACKAGE_VALUE = "mine"
private const val LEVEL_KEY = "level:"
private const val AGE_KEY = "age:"
private const val IS_KEY = "is:"
private const val NAME_KEY = "name:"

private class StringKey(name: String, hint: String) {
  val normalKey = "$name:"
  val normalHint = message("logcat.filter.completion.hint.key", hint)

  val negatedKey = "-$name:"
  val negatedHint = message("logcat.filter.completion.hint.key.negated", hint)

  val regexKey = "$name~:"
  val regexHint = message("logcat.filter.completion.hint.key.regex", hint)

  val regexNegatedKey = "-$name~:"
  val regexNegatedHint = message("logcat.filter.completion.hint.key.regex.negated", hint)

  val exactKey = "$name=:"
  val exactHint = message("logcat.filter.completion.hint.key.exact", hint)

  val exactNegatedKey = "-$name=:"
  val exactNegatedHint = message("logcat.filter.completion.hint.key.exact.negated", hint)

  val keys = setOf(normalKey, negatedKey, regexKey, regexNegatedKey, exactKey, exactNegatedKey)
}

private val MESSAGE_KEY = StringKey("message", message("logcat.filter.completion.hint.key.message"))
private val PACKAGE_KEY = StringKey("package", message("logcat.filter.completion.hint.key.package"))
private val TAG_KEY = StringKey("tag", message("logcat.filter.completion.hint.key.tag"))

private val STRING_KEYS = listOf(MESSAGE_KEY, PACKAGE_KEY, TAG_KEY)

private val BASE_KEY_LOOKUPS = listOf(
  createLookupElement(LEVEL_KEY, message("logcat.filter.completion.hint.level")),
  createLookupElement(AGE_KEY, message("logcat.filter.completion.hint.age")),
  createLookupElement(NAME_KEY, message("logcat.filter.completion.hint.name")),
  createLookupElement(IS_KEY, message("logcat.filter.completion.hint.is")),
)

private val KEY_LOOKUPS = STRING_KEYS.map { createLookupElement(it.normalKey, it.normalHint) } + BASE_KEY_LOOKUPS
private val ALL_KEY_LOOKUPS = KEY_LOOKUPS + STRING_KEYS.flatMap {
  listOf(
    createLookupElement(it.negatedKey, it.negatedHint),
    createLookupElement(it.regexKey, it.regexHint),
    createLookupElement(it.regexNegatedKey, it.regexNegatedHint),
    createLookupElement(it.exactKey, it.exactHint),
    createLookupElement(it.exactNegatedKey, it.exactNegatedHint),
  )
}

private val LEVEL_LOOKUPS_LOWERCASE = LogLevel.values()
  .map { createLookupElement("${it.name.lowercase()} ", message("logcat.filter.completion.hint.level.value", it.name)) }

private val LEVEL_LOOKUPS_UPPERCASE = LogLevel.values()
  .map { createLookupElement("${it.name.uppercase()} ", message("logcat.filter.completion.hint.level.value", it.name)) }

private val IS_LOOKUPS = listOf(
  createLookupElement("crash ", message("logcat.filter.completion.hint.is.crash")),
  createLookupElement("stacktrace ", message("logcat.filter.completion.hint.is.stacktrace")),
)
private val AGE_LOOKUPS = listOf(
  createLookupElement("30s ", message("logcat.filter.completion.hint.age.30s")),
  createLookupElement("5m ", message("logcat.filter.completion.hint.age.5m")),
  createLookupElement("3h ", message("logcat.filter.completion.hint.age.3h")),
  createLookupElement("1d ", message("logcat.filter.completion.hint.age.1d")),
)

// Do not complete a key if previous char is one of these
private const val NON_KEY_MARKER = "'\")"

private val HINTS = listOf(
  message("logcat.filter.completion.hint1"),
  message("logcat.filter.completion.hint2"),
  message("logcat.filter.completion.hint3"),
  message("logcat.filter.completion.hint4"),
  message("logcat.filter.completion.hint5"),
  message("logcat.filter.completion.hint6"),
)

/**
 * A [CompletionContributor] for the Logcat Filter Language.
 */
internal class LogcatFilterCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, psiElement(LogcatFilterTypes.VALUE),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
               // We have to exclude a few special cases where we do not want to complete.

               val text = parameters.position.text
               if (text.startsWith('"') || text.startsWith('\'')) {
                 // Do not complete keys inside a quoted text.
                 return
               }
               if (PsiTreeUtil.findSiblingBackward(parameters.position, ERROR_ELEMENT, null) != null) {
                 // Do not complete a key if there is an error in the current level of the tree. This happens when we are in an
                 // unterminated quoted string.
                 return
               }
               // Offset of beginning of the current psi element.
               val pos = parameters.offset - parameters.getRealTextLength()
               if (pos > 0) {
                 // Do not complete a key right after certain chars.
                 val c = parameters.originalFile.text[pos - 1]
                 if (NON_KEY_MARKER.contains(c)) {
                   return
                 }
               }
               result.addAllElements((if (text == DUMMY_IDENTIFIER_TRIMMED) KEY_LOOKUPS else ALL_KEY_LOOKUPS) + historyLookups())
               if (hasAndroidProject(parameters.editor)) {
                 result.addElement(createLookupElement("$MY_PACKAGE ", message("logcat.filter.completion.hint.package.mine")))
               }
               result.addHints()
             }
           })
    extend(CompletionType.BASIC, psiElement(LogcatFilterTypes.KVALUE),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
               when {
                 parameters.findPreviousText() == LEVEL_KEY -> result.addLevelLookups()
                 parameters.findPreviousText() == IS_KEY && StudioFlags.LOGCAT_IS_FILTER.get() -> result.addAllElements(IS_LOOKUPS)
                 parameters.findPreviousText() == AGE_KEY -> result.addAllElements(AGE_LOOKUPS)
               }
               result.addHints()
             }
           })
    extend(CompletionType.BASIC, or(psiElement(STRING_KVALUE), psiElement(REGEX_KVALUE)),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
               when (parameters.findPreviousText()) {
                 PACKAGE_KEY.normalKey -> {
                   result.addAllElements((parameters.getPackageNames()).map { createLookupElement("$it ") })
                   if (hasAndroidProject(parameters.editor)) {
                     result.addElement(createLookupElement("$MY_PACKAGE_VALUE ", message("logcat.filter.completion.hint.package.mine")))
                   }
                 }
                 in PACKAGE_KEY.keys -> result.addAllElements((parameters.getPackageNames()).map { createLookupElement("$it ") })
                 in TAG_KEY.keys ->
                   result.addAllElements(parameters.getTags().filter(String::isNotBlank).map { createLookupElement("$it ") })
               }
               result.addHints()
             }
           })
  }
}

private fun CompletionResultSet.addLevelLookups() {
  val prefix = prefixMatcher.prefix
  val lookups = if (prefix.isEmpty() || prefix.first().isLowerCase()) LEVEL_LOOKUPS_LOWERCASE else LEVEL_LOOKUPS_UPPERCASE
  addAllElements(lookups)
}

private fun CompletionResultSet.addHints() {
  HINTS.forEach { addLookupAdvertisement(it) }
}

@VisibleForTesting
internal fun String.getKeyVariants() = listOf("$this:", "-$this:", "$this~:", "-$this~:", "$this=:", "-$this=:")

private fun createLookupElement(text: String, hint: String? = null) = LookupElementBuilder.create(text)
  .withRenderer(object : LookupElementRenderer<LookupElement>() {
    override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
      presentation.itemText = element.lookupString
      presentation.typeText = hint
    }
  })

private fun CompletionParameters.findPreviousText() = PsiTreeUtil.skipWhitespacesBackward(position)?.text

private fun CompletionParameters.getTags() =
  editor.getUserData(TAGS_PROVIDER_KEY)?.getTags() ?: throw IllegalStateException("Missing PackageNamesProvider")

private fun CompletionParameters.getPackageNames() =
  editor.getUserData(PACKAGE_NAMES_PROVIDER_KEY)?.getPackageNames() ?: throw IllegalStateException("Missing PackageNamesProvider")

private fun CompletionParameters.getRealTextLength(): Int {
  val text = position.text
  val len = text.indexOf(DUMMY_IDENTIFIER_TRIMMED)
  return if (len < 0) text.length else len
}

private fun hasAndroidProject(editor: Editor): Boolean {
  val project = editor.project ?: return false
  return editor.getUserData(AndroidProjectDetector.KEY)?.isAndroidProject(project) ?: false
}

private fun historyLookups(): List<LookupElement> {
  return if (AndroidLogcatSettings.getInstance().filterHistoryAutocomplete) {
    val history = AndroidLogcatFilterHistory.getInstance()
    (history.favorites + history.nonFavorites).map { createLookupElement(it) }
  }
  else {
    emptyList()
  }
}
