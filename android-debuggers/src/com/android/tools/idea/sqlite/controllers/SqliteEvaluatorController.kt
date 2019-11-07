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
package com.android.tools.idea.sqlite.controllers

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorView
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorViewListener
import com.google.common.util.concurrent.FutureCallback
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

/**
 * Implementation of the application logic related to running queries and updates on a sqlite database.
 *
 * All methods are assumed to run on the UI (EDT) thread.
 */
@UiThread
class SqliteEvaluatorController(
  parentDisposable: Disposable,
  private val view: SqliteEvaluatorView,
  private val edtExecutor: FutureCallbackExecutor
) : Disposable {

  private var currentTableController: TableController? = null
  private val sqliteEvaluatorViewListener: SqliteEvaluatorViewListener = SqliteEvaluatorViewListenerImpl()
  private val listeners = mutableListOf<SqliteEvaluatorControllerListener>()

  init {
    Disposer.register(parentDisposable, this)
  }

  fun setUp() {
    view.addListener(sqliteEvaluatorViewListener)
  }

  fun removeDatabase(index: Int) {
    view.removeDatabase(index)
  }

  override fun dispose() {
    view.removeListener(sqliteEvaluatorViewListener)
    listeners.clear()
  }

  fun addListener(listener: SqliteEvaluatorControllerListener) {
    listeners.add(listener)
  }

  fun removeListener(listener: SqliteEvaluatorControllerListener) {
    listeners.remove(listener)
  }

  fun removeListeners() {
    listeners.clear()
  }

  fun addDatabase(database: SqliteDatabase, index: Int) {
    view.addDatabase(database, index)
  }

  fun evaluateSqlStatement(database: SqliteDatabase, sqliteStatement: SqliteStatement) {
    view.showSqliteStatement(sqliteStatement.toString())
    view.selectDatabase(database)

    if (sqliteStatement.isUpdateStatement()) {
      executeUpdate(database, sqliteStatement)
    } else {
      executeQuery(database, sqliteStatement) {
        view.tableView.reportError("Error executing sqlQueryCommand", it)
      }
    }
  }

  private fun executeUpdate(database: SqliteDatabase, sqliteStatement: SqliteStatement) {
    val sqliteService = database.sqliteService
    edtExecutor.addCallback(sqliteService.executeUpdate(sqliteStatement), object : FutureCallback<Int> {
      override fun onSuccess(result: Int?) {
        view.tableView.resetView()
        listeners.forEach { it.onSchemaUpdated(database) }
      }

      override fun onFailure(t: Throwable) {
        view.tableView.reportError("Error executing update", t)
      }
    })
  }

  private fun executeQuery(database: SqliteDatabase, sqliteStatement: SqliteStatement, doOnFailure: (Throwable) -> Unit) {
    val sqliteService = database.sqliteService

    currentTableController = TableController(
      parentDisposable = this@SqliteEvaluatorController,
      view = view.tableView,
      tableName = null,
      sqliteService = sqliteService,
      sqliteStatement = sqliteStatement,
      edtExecutor = edtExecutor
    )

    edtExecutor.catching(currentTableController!!.setUp(), Throwable::class.java) { throwable ->
      doOnFailure(throwable)
    }
  }

  private inner class SqliteEvaluatorViewListenerImpl : SqliteEvaluatorViewListener {
    override fun evaluateSqlActionInvoked(database: SqliteDatabase, sqliteStatement: String) {
      // TODO(b/143341562) handle SQLite statements with templates for ad-hoc queries.
      evaluateSqlStatement(database, SqliteStatement(sqliteStatement, emptyList()))
    }
  }
}

interface SqliteEvaluatorControllerListener {
  fun onSchemaUpdated(database: SqliteDatabase)
}
