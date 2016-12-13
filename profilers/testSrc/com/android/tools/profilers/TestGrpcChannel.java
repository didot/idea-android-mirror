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
package com.android.tools.profilers;

import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.inprocess.InProcessServerBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.rules.ExternalResource;

/**
 * JUnit rule for creating a light, in-process GRPC client / server connection that is initialized
 * with a single fake service which provides it test data. In addition to starting up / shutting
 * down this connection automatically, it also creates a {@link StudioProfilers} instance, which is
 * a useful handle for many tests to read from the service.
 */
public final class TestGrpcChannel<S extends BindableService> extends ExternalResource {
  private final String myName;
  private final S myService;
  private Server myServer;
  private ProfilerClient myClient;
  private StudioProfilers myProfilers;
  private ProfilerServiceGrpc.ProfilerServiceImplBase myProfilerService;

  public TestGrpcChannel(String name, S service) {
    myName = name;
    myService = service;
  }

  public TestGrpcChannel(String name, S service, ProfilerServiceGrpc.ProfilerServiceImplBase profilerService) {
    this(name, service);
    myProfilerService = profilerService;
  }

  @Override
  protected void before() throws Throwable {
    InProcessServerBuilder serverBuilder = InProcessServerBuilder.forName(myName).addService(myService);
    if (myProfilerService != null) {
      serverBuilder.addService(myProfilerService);
    }
    myServer = serverBuilder.build();
    myServer.start();
    myClient = new ProfilerClient(myName);
    myProfilers = new StudioProfilers(myClient, new IdeProfilerServices() {
      @Override
      public boolean navigateToStackTraceLine(@NotNull String line) {
        return false;
      }
    });
  }

  @Override
  protected void after() {
    myServer.shutdownNow();
  }

  public ProfilerClient getClient() {
    return myClient;
  }

  public S getService() {
    return myService;
  }

  public StudioProfilers getProfilers() {
    return myProfilers;
  }
}
