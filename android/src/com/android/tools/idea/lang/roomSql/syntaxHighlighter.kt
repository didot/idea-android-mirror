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
package com.android.tools.idea.lang.roomSql

import com.android.tools.idea.lang.roomSql.parser.RoomSqlLexer
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.*
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

val KEYWORDS = setOf(ABORT, ACTION, ADD, AFTER, ALL, ALTER, ANALYZE, AND, AS, ASC, ATTACH, AUTOINCREMENT, BEFORE, BEGIN, BETWEEN, BY,
    CASCADE, CASE, CAST, CHECK, COLLATE, COLUMN, COMMIT, CONFLICT, CONSTRAINT, CREATE, CROSS, CURRENT_DATE, CURRENT_TIME, CURRENT_TIMESTAMP,
    DATABASE, DEFAULT, DEFERRABLE, DEFERRED, DELETE, DESC, DETACH, DISTINCT, DROP, EACH, ELSE, END, ESCAPE, EXCEPT, EXCLUSIVE, EXISTS,
    EXPLAIN, FAIL, FOR, FOREIGN, FROM, GLOB, GROUP, HAVING, IF, IGNORE, IMMEDIATE, IN, INDEX, INDEXED, INITIALLY, INNER, INSERT, INSTEAD,
    INTERSECT, INTO, IS, ISNULL, JOIN, KEY, LEFT, LIKE, LIMIT, MATCH, NATURAL, NO, NOT, NOTNULL, NULL, OF, OFFSET, ON, OR, ORDER, OUTER,
    PLAN, PRAGMA, PRIMARY, QUERY, RAISE, RECURSIVE, REFERENCES, REGEXP, REINDEX, RELEASE, RENAME, REPLACE, RESTRICT, ROLLBACK, ROW, ROWID,
    SAVEPOINT, SELECT, SET, TABLE, TEMP, TEMPORARY, THEN, TO, TRANSACTION, TRIGGER, UNION, UNIQUE, UPDATE, USING, VACUUM, VALUES, VIEW,
    VIRTUAL, WHEN, WHERE, WITH, WITHOUT)

enum class RoomSqlTextAttributes(fallback: TextAttributesKey) {
  BAD_CHARACTER(HighlighterColors.BAD_CHARACTER),
  KEYWORD(DefaultLanguageHighlighterColors.KEYWORD),
  NUMBER(DefaultLanguageHighlighterColors.NUMBER),
  PARAMETER(DefaultLanguageHighlighterColors.INSTANCE_FIELD),
  STRING(DefaultLanguageHighlighterColors.STRING),
  BLOCK_COMMENT(DefaultLanguageHighlighterColors.BLOCK_COMMENT),
  LINE_COMMENT(DefaultLanguageHighlighterColors.LINE_COMMENT),
  ;

  val key = TextAttributesKey.createTextAttributesKey("ROOM_SQL_${name}", fallback)
  val keys = arrayOf(key)
}

val EMPTY_KEYS = emptyArray<TextAttributesKey>()

class RoomSqlSyntaxHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = RoomSqlLexer()

  override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = when (tokenType) {
    in KEYWORDS -> RoomSqlTextAttributes.KEYWORD.keys
    STRING_LITERAL -> RoomSqlTextAttributes.STRING.keys
    NUMERIC_LITERAL -> RoomSqlTextAttributes.NUMBER.keys
    PARAMETER_NAME -> RoomSqlTextAttributes.PARAMETER.keys
    LINE_COMMENT -> RoomSqlTextAttributes.LINE_COMMENT.keys
    COMMENT -> RoomSqlTextAttributes.BLOCK_COMMENT.keys
    TokenType.BAD_CHARACTER -> RoomSqlTextAttributes.BAD_CHARACTER.keys
    else -> EMPTY_KEYS
  }

}

class RoomSqlSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
  override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = RoomSqlSyntaxHighlighter()
}