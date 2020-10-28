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
package com.android.tools.idea.sqlite.controllers

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.sqlite.mocks.FakeExportToFileDialogView
import com.android.tools.idea.sqlite.mocks.OpenDatabaseRepository
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.Delimiter.VERTICAL_BAR
import com.android.tools.idea.sqlite.model.ExportFormat.CSV
import com.android.tools.idea.sqlite.model.ExportRequest
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.createSqliteStatement
import com.android.tools.idea.sqlite.repository.DatabaseRepository
import com.android.tools.idea.sqlite.utils.getJdbcDatabaseConnection
import com.android.tools.idea.sqlite.utils.toLineSequence
import com.android.tools.idea.testing.runDispatching
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import org.jetbrains.ide.PooledThreadExecutor
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import java.util.concurrent.Executor

class ExportToFileControllerTest : LightPlatformTestCase() {
  private val notifyExportComplete: (ExportRequest) -> Unit = mock()
  private val notifyExportError: (ExportRequest, Throwable?) -> Unit = mock()

  private lateinit var tempDirTestFixture: TempDirTestFixture

  private lateinit var edtExecutor: Executor
  private lateinit var taskExecutor: Executor

  private lateinit var projectScope: CoroutineScope
  private lateinit var databaseRepository: DatabaseRepository

  private lateinit var view: FakeExportToFileDialogView
  private lateinit var controller: ExportToFileController

  override fun setUp() {
    super.setUp()

    tempDirTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    tempDirTestFixture.setUp()

    edtExecutor = EdtExecutorService.getInstance()
    taskExecutor = PooledThreadExecutor.INSTANCE

    projectScope = project.coroutineScope
    databaseRepository = OpenDatabaseRepository(project, taskExecutor)

    view = FakeExportToFileDialogView()
    controller = ExportToFileController(
      view,
      databaseRepository,
      taskExecutor,
      edtExecutor,
      notifyExportComplete,
      notifyExportError
    )
    controller.setUp()
    Disposer.register(testRootDisposable, controller)
  }

  override fun tearDown() {
    runDispatching { databaseRepository.clear() }
    tempDirTestFixture.tearDown()
    super.tearDown()
  }

  fun testQueryToCsv() {
    // given: a database on disk and an export request
    val suffix = " ąę"
    val table1 = "t1$suffix"
    val column1 = "c1$suffix"
    val column2 = "c2$suffix"
    val databaseName = "db1$suffix"
    val outputFileName = "output$suffix.out$suffix"
    val values: List<Pair<String, String>> = (1..10).map { "$it$suffix" }.zipWithNext() // two columns with increasing numbers

    val database = createTestDatabase(databaseName)
    runDispatching {
      database.execute("create table '$table1' ('$column1' int, '$column2' text)")
      values.forEach { (v1, v2) -> database.execute("insert into '$table1' values ('$v1', '$v2')") }
    }

    val delimiter = VERTICAL_BAR
    val format = CSV(delimiter)
    val dstPath = tempDirTestFixture.createFile(outputFileName).toNioPath()
    val query = runDispatching { createSqliteStatement("select * from '$table1'") }
    val exportRequest = ExportRequest.ExportQueryResultsRequest(database, query, format, dstPath)

    val delimiterStr = delimiter.delimiter
    val expectedOutput = listOf("$column1$delimiterStr$column2") + values.map { (v1, v2) -> "$v1$delimiterStr$v2" }

    // when: an export request is submitted
    runDispatching { view.listeners.first().exportRequestSubmitted(exportRequest) }

    // then: compare output file with expected output
    val actualOutput = dstPath.toLineSequence().toList()

    assertThat(actualOutput).isEqualTo(expectedOutput)
    verify(notifyExportComplete).invoke(exportRequest)
    verifyNoMoreInteractions(notifyExportError)
  }

  @Suppress("SameParameterValue")
  private fun createTestDatabase(dbName: String): SqliteDatabaseId {
    val databaseFile = tempDirTestFixture.createFile(dbName)
    val connection = pumpEventsAndWaitForFuture(
      getJdbcDatabaseConnection(testRootDisposable, databaseFile, FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE))
    )
    val databaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(databaseFile))
    runDispatching { databaseRepository.addDatabaseConnection(databaseId, connection) }
    return databaseId
  }

  private suspend fun SqliteDatabaseId.execute(statement: String) =
    databaseRepository.executeStatement(this, createSqliteStatement(statement)).await()

  private suspend fun createSqliteStatement(statement: String): SqliteStatement = withContext(edtExecutor.asCoroutineDispatcher()) {
    createSqliteStatement(project, statement)
  }
}