/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.monitor.profilerclient;

import com.android.ddmlib.IDevice;
import com.android.tools.profiler.proto.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * The per-device handle/API for clients to communicate with the server on the device.
 */
public class DeviceProfilerService {
  private static final String DEVICE_UNIX_DOMAIN_NAME = "StudioProfiler";
  private static final int DEVICE_PORT = 12389;
  public static final int UNSELECTED_PROCESS_ID = Integer.MIN_VALUE;

  private static final Logger LOG = Logger.getInstance(DeviceProfilerService.class.getCanonicalName());

  @NotNull private final Set<Object> myUserKeys = new HashSet<>();
  @NotNull private final IDevice myDevice;
  @NotNull private final ManagedChannel myChannel;
  @NotNull private final DeviceServiceGrpc.DeviceServiceBlockingStub myDeviceService;
  @NotNull private final MemoryServiceGrpc.MemoryServiceBlockingStub myMemoryService;
  @NotNull private final CpuServiceGrpc.CpuServiceBlockingStub myCpuService;
  @NotNull private final NetworkServiceGrpc.NetworkServiceBlockingStub myNetworkService;
  @NotNull private final EventServiceGrpc.EventServiceBlockingStub myEventService;
  @NotNull private final EnergyServiceGrpc.EnergyServiceBlockingStub myEnergyService;
  private final int myPort;
  private int mySelectedProcessId = UNSELECTED_PROCESS_ID;

  private DeviceProfilerService(@NotNull IDevice device, int port) {
    myDevice = device;
    myPort = port;

    // Stash the currently set context class loader so ManagedChannelProvider can find an appropriate implementation.
    ClassLoader stashedContextClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(ManagedChannelBuilder.class.getClassLoader());
    myChannel = ManagedChannelBuilder.forAddress("localhost", myPort).usePlaintext(true).build();
    Thread.currentThread().setContextClassLoader(stashedContextClassLoader);

    myDeviceService = DeviceServiceGrpc.newBlockingStub(myChannel);
    myMemoryService = MemoryServiceGrpc.newBlockingStub(myChannel);
    myCpuService = CpuServiceGrpc.newBlockingStub(myChannel);
    myNetworkService = NetworkServiceGrpc.newBlockingStub(myChannel);
    myEventService = EventServiceGrpc.newBlockingStub(myChannel);
    myEnergyService = EnergyServiceGrpc.newBlockingStub(myChannel);
    Disposer.register(ApplicationManager.getApplication(), () -> {
      if (!myUserKeys.isEmpty()) {
        for (Object userKey : myUserKeys) {
          LOG.warn("DeviceProfilerService has not been fully unregistered from " + userKey);
        }
      }
    });
  }

  @Nullable
  static DeviceProfilerService createService(@NotNull IDevice device) {
    int port = DevicePortForwarder.forward(device, DEVICE_PORT);
    if (port < 0) {
      return null;
    }
    return new DeviceProfilerService(device, port);
  }

  void register(@NotNull Object userKey) throws IllegalArgumentException {
    if (myUserKeys.contains(userKey)) {
      throw new IllegalArgumentException("Duplicate keys detected: " + userKey.toString());
    }

    myUserKeys.add(userKey);
  }

  /**
   * @return true when {@code this} has been shut down.
   */
  boolean unregister(@NotNull Object userKey) {
    if (!myUserKeys.contains(userKey)) {
      throw new IllegalArgumentException("Key already unregistered: " + userKey.toString());
    }

    myUserKeys.remove(userKey);
    if (myUserKeys.isEmpty()) {
      DevicePortForwarder.removeForward(myDevice, myPort, DEVICE_PORT);
      myChannel.shutdown();
      return true;
    }
    return false;
  }

  public void setSelectedProcessId(int id) {
    mySelectedProcessId = id;
  }

  public int getSelectedProcessId() {
    return mySelectedProcessId;
  }

  @NotNull
  public DeviceServiceGrpc.DeviceServiceBlockingStub getDeviceService() {
    return myDeviceService;
  }

  @NotNull
  public MemoryServiceGrpc.MemoryServiceBlockingStub getMemoryService() {
    return myMemoryService;
  }

  @NotNull
  public CpuServiceGrpc.CpuServiceBlockingStub getCpuService() {
    return myCpuService;
  }

  @NotNull
  public NetworkServiceGrpc.NetworkServiceBlockingStub getNetworkService() {
    return myNetworkService;
  }

  @NotNull
  public EventServiceGrpc.EventServiceBlockingStub getEventService() {
    return myEventService;
  }

  @NotNull
  public EnergyServiceGrpc.EnergyServiceBlockingStub getEnergyService() {
    return myEnergyService;
  }

  @NotNull
  public IDevice getDevice() {
    return myDevice;
  }
}
