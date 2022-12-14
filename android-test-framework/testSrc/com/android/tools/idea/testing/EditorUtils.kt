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
package com.android.tools.idea.testing

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import org.junit.Assert.assertNotNull

/**
 * Execute the given callback in the context of the [Editor] and commit all documents.
 */
fun Editor.executeAndSave(callback: Editor.() -> Unit) {
  assertNotNull("Project can not be null to save the editor changes", project)
  WriteCommandAction.runWriteCommandAction(project) {
    callback()
    PsiDocumentManager.getInstance(project!!).commitAllDocuments()
  }
}

/**
 * Moves the caret to the first occurrence of the given [text].
 */
fun Editor.moveCaretToFirstOccurrence(text: String) {
  caretModel.moveToOffset(document.text.indexOf(text))
}

fun Editor.moveCaretToEnd() {
  caretModel.moveToOffset(document.textLength)
}

fun Editor.insertText(text: String) {
  document.insertString(caretModel.offset, text)
}

/**
 * Deletes the first occurrence of the given [text].
 */
fun Editor.deleteText(text: String) {
  val offset = document.text.indexOf(text)
  require(offset != -1)
  document.deleteString(offset, offset + text.length)
}

/**
 * Deletes the current line.
 */
fun Editor.deleteLine() {
  val line = document.getLineNumber(caretModel.offset)
  document.deleteString(
    document.getLineStartOffset(line),
    document.getLineEndOffset(line))
}

/**
 * Moves the caret the number of [lines] specified. A negative number indicates the caret moving up, a positive one moving down.
 * If [setToEnd] is false the method, when possible, will try to set the caret at the same distance from the start as it was in
 * the current line.
 */
fun Editor.moveCaretLines(lines: Int, setToEnd: Boolean = true) {
  val line = document.getLineNumber(caretModel.offset)
  val lineEndOffset = document.getLineStartOffset(line + lines)
  val newOffset = if (setToEnd) {
    lineEndOffset
  }
  else {
    val positionInCurrentLine = caretModel.offset - document.getLineStartOffset(line)
    val lineStartOffset = document.getLineStartOffset(line + lines)
    (lineStartOffset + positionInCurrentLine).coerceAtMost(lineEndOffset)
  }
  caretModel.moveToOffset(newOffset)
}

/**
 * Replaces the first occurrence of [old] text with [new].
 */
fun Editor.replaceText(old: String, new: String) {
  val offset = document.text.indexOf(old)
  require(offset != -1)
  document.replaceString(offset, offset + old.length, new)
}