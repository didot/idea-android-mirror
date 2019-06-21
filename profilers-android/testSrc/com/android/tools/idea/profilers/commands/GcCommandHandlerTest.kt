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
package com.android.tools.idea.profilers.commands

import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.tools.profiler.proto.Commands
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class GcCommandHandlerTest {
  @Test
  fun testExecute() {
    val testPid = 1
    val device = mock(IDevice::class.java)
    val client = mock(Client::class.java)
    val clientData = mock(ClientData::class.java)
    `when`<ClientData>(client.getClientData()).thenReturn(clientData)
    `when`<Int>(clientData.getPid()).thenReturn(testPid)
    `when`<Array<Client>>(device.getClients()).thenReturn(arrayOf(client))
    `when`<Boolean>(device.isOnline()).thenReturn(true)
    val gcCommand = GcCommandHandler(device)

    var command = Commands.Command.newBuilder().apply {
      pid = 2
    }.build()
    gcCommand.execute(command)
    verify<Client>(client, times(0)).executeGarbageCollector()

    command = Commands.Command.newBuilder().apply {
      pid = testPid
    }.build()
    gcCommand.execute(command)
    verify<Client>(client, times(1)).executeGarbageCollector()
  }
}