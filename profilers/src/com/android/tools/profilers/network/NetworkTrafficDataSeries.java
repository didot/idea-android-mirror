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

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class is a data series representing bytes in / bytes out network traffic (based on the
 * {@link Type} passed into the constructor).
 *
 * It is responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 *
 * TODO: This class needs tests.
 */
public class NetworkTrafficDataSeries implements DataSeries<Long> {
  public enum Type {
    BYTES_RECIEVED("Received"),
    BYTES_SENT("Sent");

    private final String myLabel;

    Type(String label) {
      myLabel = label;
    }

    @NotNull
    public String getLabel() {
      return myLabel;
    }
  }

  @NotNull
  private NetworkServiceGrpc.NetworkServiceBlockingStub myClient;
  private final int myProcessId;
  private final Type myType;

  public NetworkTrafficDataSeries(@NotNull NetworkServiceGrpc.NetworkServiceBlockingStub client, int id, Type type) {
    myClient = client;
    myProcessId = id;
    myType = type;
  }

  @Override
  public ImmutableList<SeriesData<Long>> getDataForXRange(@NotNull Range timeCurrentRangeUs) {
    List<SeriesData<Long>> seriesData = new ArrayList<>();

    NetworkProfiler.NetworkDataRequest.Builder dataRequestBuilder = NetworkProfiler.NetworkDataRequest.newBuilder()
      .setAppId(myProcessId)
      .setType(NetworkProfiler.NetworkDataRequest.Type.SPEED)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin()))
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax()));
    NetworkProfiler.NetworkDataResponse response = myClient.getData(dataRequestBuilder.build());
    for (NetworkProfiler.NetworkProfilerData data : response.getDataList()) {
      long xTimestamp = TimeUnit.NANOSECONDS.toMicros(data.getBasicInfo().getEndTimestamp());
      NetworkProfiler.SpeedData speedData = data.getSpeedData();
      switch (myType) {
        case BYTES_RECIEVED:
          seriesData.add(new SeriesData<>(xTimestamp, speedData.getReceived()));
          break;
        case BYTES_SENT:
          seriesData.add(new SeriesData<>(xTimestamp, speedData.getSent()));
          break;
        default:
          throw new IllegalStateException("Unexpected network traffic data series type: " + myType);
      }
    }
    return ContainerUtil.immutableList(seriesData);
  }
}
