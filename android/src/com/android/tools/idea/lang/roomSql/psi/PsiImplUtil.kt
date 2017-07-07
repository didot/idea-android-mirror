/*
 * Copyright (C) 2017 The Android Open Source Project
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

@file:JvmName("PsiImplUtil")

package com.android.tools.idea.lang.roomSql.psi

import com.android.tools.idea.lang.roomSql.RoomTablePsiReference
import com.intellij.psi.PsiReference


fun getReference(table: RoomTableName): PsiReference? = RoomTablePsiReference(table)

fun getNameAsString(table: RoomTableName): String = when {
  table.identifier != null -> table.identifier!!.text
  table.bracketLiteral != null -> {
    val text = table.bracketLiteral!!.text
    text.substring(1, text.length - 1)
  }
  table.stringLiteral != null -> {
    val text = table.stringLiteral!!.text
    text.substring(1, text.length - 1).replace("''", "'")
  }
  else -> error("invalid RoomTableName")
}

/**
 * Checks if a given [RoomTableName] is using quoting.
 *
 * Unquoted table name references are case insensitive.
 */
fun isQuoted(table: RoomTableName) = table.identifier == null