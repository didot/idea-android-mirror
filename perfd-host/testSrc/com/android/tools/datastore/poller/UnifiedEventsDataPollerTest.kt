/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.datastore.poller

import com.android.testutils.TestUtils
import com.android.tools.datastore.DataStorePollerTest
import com.android.tools.datastore.DataStoreService
import com.android.tools.datastore.FakeLogService
import com.android.tools.datastore.database.UnifiedEventsTable
import com.android.tools.profiler.proto.Profiler
import com.android.tools.profiler.proto.ProfilerServiceGrpc
import com.google.common.truth.Truth.assertThat
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class UnifiedEventsDataPollerTest : DataStorePollerTest() {

  private lateinit var dataStore: DataStoreService
  private lateinit var profilerService: FakeProfilerService
  private lateinit var table: UnifiedEventsTable
  private lateinit var server: Server
  private lateinit var poller: UnifiedEventsDataPoller

  @Before
  fun setup() {
    val servicePath = TestUtils.createTempDirDeletedOnExit().absolutePath
    dataStore = DataStoreService(javaClass.simpleName, servicePath, pollTicker::run, FakeLogService())
    profilerService = FakeProfilerService()
    val namespace = DataStoreService.BackingNamespace.DEFAULT_SHARED_NAMESPACE
    val database = dataStore.createDatabase(servicePath + namespace.myNamespace, namespace.myCharacteristic) { _ -> }
    table = UnifiedEventsTable()
    table.initialize(database.connection)

    server = InProcessServerBuilder.forName("UnifiedEventsPollerServer").addService(profilerService).build()
    server.start()
    val managedChannel = InProcessChannelBuilder.forName("UnifiedEventsPollerServer").build()
    val serviceStub = ProfilerServiceGrpc.newBlockingStub(managedChannel)

    poller = UnifiedEventsDataPoller(1, table, serviceStub)
  }

  @After
  fun teardown() {
    server.shutdownNow()
    dataStore.shutdown()
    poller.stop()
  }

  @Test
  fun pollerIsStoppedWhenStopCalled() {
    val thread = Thread(poller)
    thread.start()
    assertThat(thread.isAlive).isTrue()
    poller.stop()
    thread.join(TimeUnit.SECONDS.toMillis(1))
    assertThat(thread.isAlive).isFalse()
  }

  @Test
  fun pollerQueriesForEvents() {
    // Due to the threaded nature of streaming rpcs we may need to try and query the data store multiple times for the events.
    var retryAttempts = 5
    val thread = Thread(poller)
    thread.start()
    profilerService.eventsLock.lock()
    assertThat(profilerService.eventsPopulated.await(1, TimeUnit.SECONDS)).isTrue()
    profilerService.eventsLock.unlock()
    var response = table.queryUnifiedEvents()
    while (response.size != FakeProfilerService.eventsList.size && retryAttempts-- >= 0) {
      response = table.queryUnifiedEvents()
      Thread.sleep(100)
    }
    assertThat(response).containsExactlyElementsIn(FakeProfilerService.eventsList)
  }


  private class FakeProfilerService : ProfilerServiceGrpc.ProfilerServiceImplBase() {

    val eventsLock = ReentrantLock()
    val eventsPopulated = eventsLock.newCondition()

    companion object {
      val eventsList = mutableListOf(Profiler.Event.newBuilder()
                                       .setTimestamp(10)
                                       .setSessionId(1)
                                       .setEventId(1)
                                       .setKind(Profiler.Event.Kind.SESSION)
                                       .build(),
                                     Profiler.Event.newBuilder()
                                       .setTimestamp(11)
                                       .setSessionId(1)
                                       .setEventId(2)
                                       .setKind(Profiler.Event.Kind.SESSION)
                                       .build(),
                                     Profiler.Event.newBuilder()
                                       .setTimestamp(12)
                                       .setSessionId(1)
                                       .setEventId(3)
                                       .setKind(Profiler.Event.Kind.SESSION)
                                       .build())
    }

    override fun getEvents(request: Profiler.GetEventsRequest?, responseObserver: StreamObserver<Profiler.Event>) {
      eventsList.forEach { responseObserver.onNext(it) }
      responseObserver.onCompleted()
      eventsLock.lock()
      eventsPopulated.signal()
      eventsLock.unlock()
    }
  }
}