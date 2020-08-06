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
package com.android.tools.idea.sqlite

import androidx.sqlite.inspection.SqliteInspectorProtocol
import com.android.testutils.MockitoKt.any
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureException
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DatabaseInspectorClientTest : LightPlatformTestCase() {
  private lateinit var databaseInspectorClient: DatabaseInspectorClient
  private lateinit var mockMessenger: AppInspectorClient.CommandMessenger

  private lateinit var openDatabaseFunction: (SqliteDatabaseId, DatabaseConnection) -> Unit
  private var openDatabaseInvoked = false

  private lateinit var handleErrorFunction: (String) -> Unit
  private var handleErrorInvoked = false

  private lateinit var hasDatabasePossiblyChangedFunction: () -> Unit
  private var hasDatabasePossiblyChangedInvoked = false

  private lateinit var handleDatabaseClosedFunction: (SqliteDatabaseId) -> Unit
  private lateinit var databaseClosedInvocations: MutableList<SqliteDatabaseId>

  private lateinit var executor: ExecutorService
  private lateinit var scope: CoroutineScope

  override fun setUp() {
    super.setUp()

    mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)
    `when`(mockMessenger.rawEventFlow).thenReturn(emptyFlow())
    openDatabaseInvoked = false
    openDatabaseFunction = { _, _ -> openDatabaseInvoked = true }

    handleErrorInvoked = false
    handleErrorFunction = { _ -> handleErrorInvoked = true }

    hasDatabasePossiblyChangedInvoked = false
    hasDatabasePossiblyChangedFunction = { hasDatabasePossiblyChangedInvoked = true }

    databaseClosedInvocations = mutableListOf()
    handleDatabaseClosedFunction = { databaseId -> databaseClosedInvocations.add(databaseId) }

    executor = Executors.newSingleThreadExecutor()
    scope = CoroutineScope(executor.asCoroutineDispatcher() + SupervisorJob())

    databaseInspectorClient = DatabaseInspectorClient(
      mockMessenger,
      testRootDisposable,
      handleErrorFunction,
      openDatabaseFunction,
      hasDatabasePossiblyChangedFunction,
      handleDatabaseClosedFunction,
      executor,
      scope
    )
  }

  override fun tearDown() {
    scope.cancel()
    executor.shutdownNow()
    super.tearDown()
  }

  fun testStartTrackingDatabaseConnectionSendsMessage() = runBlocking<Unit> {
    // Prepare
    val emptyResponse = SqliteInspectorProtocol.Response.newBuilder().build().toByteArray()
    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java))).thenReturn(emptyResponse)

    val trackDatabasesCommand = SqliteInspectorProtocol.Command.newBuilder()
      .setTrackDatabases(SqliteInspectorProtocol.TrackDatabasesCommand.getDefaultInstance())
      .build()
      .toByteArray()

    // Act
    databaseInspectorClient.startTrackingDatabaseConnections()

    // Assert
    verify(mockMessenger).sendRawCommand(trackDatabasesCommand)
  }

  fun testOnDatabaseOpenedEventOpensDatabase() {
    // Prepare
    val databaseOpenEvent = SqliteInspectorProtocol.DatabaseOpenedEvent.newBuilder().setDatabaseId(1).setPath("path").build()
    val event = SqliteInspectorProtocol.Event.newBuilder().setDatabaseOpened(databaseOpenEvent).build()

    // Act
    databaseInspectorClient.onRawEvent(event.toByteArray())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertTrue(openDatabaseInvoked)
  }

  fun testRecoverableErrorMessageShowsError() {
    // Prepare
    val errorOccurredEvent = SqliteInspectorProtocol.ErrorOccurredEvent.newBuilder().setContent(
      SqliteInspectorProtocol.ErrorContent.newBuilder()
        .setMessage("errorMessage")
        .setRecoverability(SqliteInspectorProtocol.ErrorRecoverability.newBuilder().setIsRecoverable(true).build())
        .setStackTrace("stackTrace")
        .build()
    ).build()
    val event = SqliteInspectorProtocol.Event.newBuilder().setErrorOccurred(errorOccurredEvent).build()

    // Act
    databaseInspectorClient.onRawEvent(event.toByteArray())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertTrue(handleErrorInvoked)
  }

  fun testUnrecoverableErrorMessageShowsError() {
    // Prepare
    val errorOccurredEvent = SqliteInspectorProtocol.ErrorOccurredEvent.newBuilder().setContent(
      SqliteInspectorProtocol.ErrorContent.newBuilder()
        .setMessage("errorMessage")
        .setRecoverability(SqliteInspectorProtocol.ErrorRecoverability.newBuilder().setIsRecoverable(false).build())
        .setStackTrace("stackTrace")
        .build()
    ).build()
    val event = SqliteInspectorProtocol.Event.newBuilder().setErrorOccurred(errorOccurredEvent).build()

    // Act
    databaseInspectorClient.onRawEvent(event.toByteArray())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertTrue(handleErrorInvoked)
  }

  fun testUnknownRecoverableErrorMessageShowsError() {
    // Prepare
    val errorOccurredEvent = SqliteInspectorProtocol.ErrorOccurredEvent.newBuilder().setContent(
      SqliteInspectorProtocol.ErrorContent.newBuilder()
        .setMessage("errorMessage")
        .setRecoverability(SqliteInspectorProtocol.ErrorRecoverability.newBuilder().build())
        .setStackTrace("stackTrace")
        .build()
    ).build()
    val event = SqliteInspectorProtocol.Event.newBuilder().setErrorOccurred(errorOccurredEvent).build()

    // Act
    databaseInspectorClient.onRawEvent(event.toByteArray())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertTrue(handleErrorInvoked)
  }

  fun testHasDatabasePossiblyChangedCallsCallback() {
    // Prepare
    val databasePossiblyChangedEvent = SqliteInspectorProtocol.DatabasePossiblyChangedEvent.newBuilder().build()
    val event = SqliteInspectorProtocol.Event.newBuilder().setDatabasePossiblyChanged(databasePossiblyChangedEvent).build()

    // Act
    databaseInspectorClient.onRawEvent(event.toByteArray())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertTrue(hasDatabasePossiblyChangedInvoked)
  }

  fun testDatabaseClosedCallsCallback() {
    // Prepare
    val databaseClosedEvent = SqliteInspectorProtocol.DatabaseClosedEvent.newBuilder().setDatabaseId(1).build()
    val event = SqliteInspectorProtocol.Event.newBuilder().setDatabaseClosed(databaseClosedEvent).build()

    // Act
    databaseInspectorClient.onRawEvent(event.toByteArray())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertSize(1, databaseClosedInvocations)
    assertEquals(SqliteDatabaseId.fromLiveDatabase("", 1), databaseClosedInvocations.first())
  }

  fun testKeepConnectionOpenSuccess() = runBlocking {
    // Prepare
    val keepDbsOpenResponse = SqliteInspectorProtocol.Response.newBuilder()
      .setKeepDatabasesOpen(SqliteInspectorProtocol.KeepDatabasesOpenResponse.newBuilder().build())
      .build()
      .toByteArray()

    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java))).thenReturn(keepDbsOpenResponse)

    val trackDatabasesCommand = SqliteInspectorProtocol.Command.newBuilder()
      .setKeepDatabasesOpen(SqliteInspectorProtocol.KeepDatabasesOpenCommand.newBuilder().setSetEnabled(true).build())
      .build()
      .toByteArray()

    // Act
    val result = pumpEventsAndWaitForFuture(databaseInspectorClient.keepConnectionsOpen(true))

    // Assert
    verify(mockMessenger).sendRawCommand(trackDatabasesCommand)
    assertEquals(true, result)
  }

  fun testKeepConnectionOpenError() = runBlocking<Unit> {
    // Prepare
    val keepDbsOpenResponse = SqliteInspectorProtocol.Response.newBuilder()
      .setKeepDatabasesOpen(SqliteInspectorProtocol.KeepDatabasesOpenResponse.newBuilder().build())
      .setErrorOccurred(
        SqliteInspectorProtocol.ErrorOccurredResponse.newBuilder().setContent(
          SqliteInspectorProtocol.ErrorContent.newBuilder()
            .setRecoverability(SqliteInspectorProtocol.ErrorRecoverability.newBuilder().setIsRecoverable(true).build())
            .setMessage("msg")
            .setStackTrace("stk")
            .build()
        ).build())
      .build()
      .toByteArray()

    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java))).thenReturn(keepDbsOpenResponse)

    val trackDatabasesCommand = SqliteInspectorProtocol.Command.newBuilder()
      .setKeepDatabasesOpen(SqliteInspectorProtocol.KeepDatabasesOpenCommand.newBuilder().setSetEnabled(true).build())
      .build()
      .toByteArray()

    // Act
    pumpEventsAndWaitForFutureException(databaseInspectorClient.keepConnectionsOpen(true))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockMessenger).sendRawCommand(trackDatabasesCommand)
  }

  fun testKeepConnectionOpenNotSetInResponse() = runBlocking {
    // Prepare
    val keepDbsOpenResponse = SqliteInspectorProtocol.Response.newBuilder()
      .build()
      .toByteArray()

    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java))).thenReturn(keepDbsOpenResponse)

    val trackDatabasesCommand = SqliteInspectorProtocol.Command.newBuilder()
      .setKeepDatabasesOpen(SqliteInspectorProtocol.KeepDatabasesOpenCommand.newBuilder().setSetEnabled(true).build())
      .build()
      .toByteArray()

    // Act
    val result = pumpEventsAndWaitForFuture(databaseInspectorClient.keepConnectionsOpen(true))

    // Assert
    verify(mockMessenger).sendRawCommand(trackDatabasesCommand)
    assertEquals(null, result)
  }
}