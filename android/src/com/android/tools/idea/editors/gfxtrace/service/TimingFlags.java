/*
 * Copyright (C) 2015 The Android Open Source Project
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
 *
 * THIS FILE WAS GENERATED BY codergen. EDIT WITH CARE.
 */
package com.android.tools.idea.editors.gfxtrace.service;

import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class TimingFlags {
  public static final TimingFlags TimingCPU = new TimingFlags(0, "TimingCPU");
  public static final int TimingCPUValue = 0;
  public static final TimingFlags TimingGPU = new TimingFlags(1, "TimingGPU");
  public static final int TimingGPUValue = 1;
  public static final TimingFlags TimingPerCommand = new TimingFlags(2, "TimingPerCommand");
  public static final int TimingPerCommandValue = 2;
  public static final TimingFlags TimingPerDrawCall = new TimingFlags(4, "TimingPerDrawCall");
  public static final int TimingPerDrawCallValue = 4;
  public static final TimingFlags TimingPerFrame = new TimingFlags(8, "TimingPerFrame");
  public static final int TimingPerFrameValue = 8;

  private static final ImmutableMap<Integer, TimingFlags> VALUES = ImmutableMap.<Integer, TimingFlags>builder()
    .put(0, TimingCPU)
    .put(1, TimingGPU)
    .put(2, TimingPerCommand)
    .put(4, TimingPerDrawCall)
    .put(8, TimingPerFrame)
    .build();

  private final int myValue;
  private final String myName;

  private TimingFlags(int v, String n) {
    myValue = v;
    myName = n;
  }

  public int getValue() {
    return myValue;
  }

  public String getName() {
    return myName;
  }

  public void encode(@NotNull Encoder e) throws IOException {
    e.int32(myValue);
  }

  public static TimingFlags decode(@NotNull Decoder d) throws IOException {
    return findOrCreate(d.int32());
  }

  public static TimingFlags find(int value) {
    return VALUES.get(value);
  }

  public static TimingFlags findOrCreate(int value) {
    TimingFlags result = VALUES.get(value);
    return (result == null) ? new TimingFlags(value, null) : result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof TimingFlags)) return false;
    return myValue == ((TimingFlags)o).myValue;
  }

  @Override
  public int hashCode() {
    return myValue;
  }

  @Override
  public String toString() {
    return (myName == null) ? "TimingFlags(" + myValue + ")" : myName;
  }
}
