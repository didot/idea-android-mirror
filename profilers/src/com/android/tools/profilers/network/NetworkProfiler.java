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
package com.android.tools.profilers.network;

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.NetworkProfiler.NetworkStartRequest;
import com.android.tools.profiler.proto.NetworkProfiler.NetworkStartResponse;
import com.android.tools.profiler.proto.NetworkProfiler.NetworkStopRequest;
import com.android.tools.profiler.proto.NetworkProfiler.NetworkStopResponse;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfiler;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class NetworkProfiler implements StudioProfiler {

  @NotNull
  private final StudioProfilers profilers;

  public NetworkProfiler(@NotNull StudioProfilers profilers) {
    this.profilers = profilers;
  }

  @Override
  @NotNull
  public ProfilerMonitor newMonitor() {
    return new NetworkMonitor(profilers);
  }

  @Override
  public void startProfiling(@NotNull Common.Session session) {
    // TODO(b/150503095)
    NetworkStartResponse response =
      profilers.getClient().getNetworkClient().startMonitoringApp(NetworkStartRequest.newBuilder().setSession(session).build());
  }

  @Override
  public void stopProfiling(@NotNull Common.Session session) {
    // TODO(b/150503095)
    NetworkStopResponse response =
      profilers.getClient().getNetworkClient().stopMonitoringApp(NetworkStopRequest.newBuilder().setSession(session).build());
  }
}
