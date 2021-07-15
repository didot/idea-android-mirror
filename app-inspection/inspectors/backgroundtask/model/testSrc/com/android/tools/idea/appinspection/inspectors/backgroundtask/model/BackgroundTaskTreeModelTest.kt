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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.model

import androidx.work.inspection.WorkManagerInspectorProtocol
import backgroundtask.inspection.BackgroundTaskInspectorProtocol
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.swing.tree.DefaultMutableTreeNode

class BackgroundTaskTreeModelTest {

  private class FakeAppInspectorMessenger(
    override val scope: CoroutineScope,
  ) : AppInspectorMessenger {
    override suspend fun sendRawCommand(rawData: ByteArray): ByteArray = rawData
    override val eventFlow = emptyFlow<ByteArray>()
  }

  private lateinit var executor: ExecutorService
  private lateinit var scope: CoroutineScope
  private lateinit var backgroundTaskInspectorMessenger: FakeAppInspectorMessenger
  private lateinit var workManagerInspectorMessenger: FakeAppInspectorMessenger
  private lateinit var client: BackgroundTaskInspectorClient
  private lateinit var model: BackgroundTaskTreeModel

  @Before
  fun setUp() {
    executor = Executors.newSingleThreadExecutor()
    scope = CoroutineScope(executor.asCoroutineDispatcher() + SupervisorJob())
    backgroundTaskInspectorMessenger = FakeAppInspectorMessenger(scope)
    workManagerInspectorMessenger = FakeAppInspectorMessenger(scope)
    client = BackgroundTaskInspectorClient(backgroundTaskInspectorMessenger,
                                           WmiMessengerTarget.Resolved(workManagerInspectorMessenger),
                                           scope, executor.asCoroutineDispatcher())
    model = BackgroundTaskTreeModel(client)
  }

  @After
  fun tearDown() {
    scope.cancel()
    executor.shutdownNow()
  }

  @Test
  fun addTreeNodes() = runBlocking {
    val newWorkEvent = WorkManagerInspectorProtocol.Event.newBuilder().apply {
      workAddedBuilder.workBuilder.apply {
        id = "test"
        state = WorkManagerInspectorProtocol.WorkInfo.State.ENQUEUED
      }
    }.build()

    val newJobEvent = BackgroundTaskInspectorProtocol.Event.newBuilder().apply {
      backgroundTaskEventBuilder.apply {
        taskId = 0L
        jobScheduledBuilder.apply {
          jobBuilder.backoffPolicy = BackgroundTaskInspectorProtocol.JobInfo.BackoffPolicy.UNDEFINED_BACKOFF_POLICY
        }
      }
    }.build()

    val newAlarmEvent = BackgroundTaskInspectorProtocol.Event.newBuilder().apply {
      backgroundTaskEventBuilder.apply {
        taskId = 1L
        alarmSetBuilder.apply {
          type = BackgroundTaskInspectorProtocol.AlarmSet.Type.UNDEFINED_ALARM_TYPE
        }
      }
    }.build()

    val newWakeLockEvent = BackgroundTaskInspectorProtocol.Event.newBuilder().apply {
      backgroundTaskEventBuilder.apply {
        taskId = 2L
        wakeLockAcquiredBuilder.apply {
          level = BackgroundTaskInspectorProtocol.WakeLockAcquired.Level.UNDEFINED_WAKE_LOCK_LEVEL
        }
      }
    }.build()

    listOf(newWakeLockEvent, newAlarmEvent, newJobEvent).forEach { event ->
      client.handleEvent(EventWrapper(EventWrapper.Case.BACKGROUND_TASK, event.toByteArray()))
    }
    client.handleEvent(EventWrapper(EventWrapper.Case.WORK, newWorkEvent.toByteArray()))

    scope.launch {
      val root = model.root as DefaultMutableTreeNode
      assertThat(root.childCount).isEqualTo(4)
      val workChild = root.firstChild as DefaultMutableTreeNode
      assertThat(workChild.childCount).isEqualTo(1)
      assertThat(workChild.userObject).isEqualTo("Works")
      assertThat(workChild.firstChild as DefaultMutableTreeNode).isEqualTo(model.getTreeNode("test"))
      val jobChild = root.getChildAfter(workChild) as DefaultMutableTreeNode
      assertThat(jobChild.childCount).isEqualTo(1)
      assertThat(jobChild.userObject).isEqualTo("Jobs")
      assertThat(jobChild.firstChild as DefaultMutableTreeNode).isEqualTo(model.getTreeNode("0"))
      val alarmChild = root.getChildAfter(jobChild) as DefaultMutableTreeNode
      assertThat(alarmChild.childCount).isEqualTo(1)
      assertThat(alarmChild.userObject).isEqualTo("Alarms")
      assertThat(alarmChild.firstChild as DefaultMutableTreeNode).isEqualTo(model.getTreeNode("1"))
      val wakeLockChild = root.getChildAfter(alarmChild) as DefaultMutableTreeNode
      assertThat(wakeLockChild.childCount).isEqualTo(1)
      assertThat(wakeLockChild.userObject).isEqualTo("WakeLocks")
      assertThat(wakeLockChild.firstChild as DefaultMutableTreeNode).isEqualTo(model.getTreeNode("2"))
    }.join()
  }

  @Test
  fun removeTreeNode(): Unit = runBlocking {
    val newWorkEvent = WorkManagerInspectorProtocol.Event.newBuilder().apply {
      workAddedBuilder.workBuilder.apply {
        id = "test"
        state = WorkManagerInspectorProtocol.WorkInfo.State.ENQUEUED
      }
    }.build()

    client.handleEvent(EventWrapper(EventWrapper.Case.WORK, newWorkEvent.toByteArray()))

    var entryNode: DefaultMutableTreeNode? = null
    scope.launch {
      val root = model.root as DefaultMutableTreeNode
      assertThat(root.childCount).isEqualTo(4)
      val workChild = root.firstChild as DefaultMutableTreeNode
      assertThat(workChild.childCount).isEqualTo(1)
      assertThat(workChild.userObject).isEqualTo("Works")
      entryNode = workChild.firstChild as DefaultMutableTreeNode
      assertThat(entryNode).isEqualTo(model.getTreeNode("test"))
    }.join()

    val removeWorkEvent = WorkManagerInspectorProtocol.Event.newBuilder().apply {
      workRemovedBuilder.apply {
        id = "test"
      }
    }.build()
    client.handleEvent(EventWrapper(EventWrapper.Case.WORK, removeWorkEvent.toByteArray()))

    scope.launch {
      assertThat(entryNode?.parent).isNull()
      assertThat(model.getTreeNode("test")).isNull()
    }
  }
}
