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
package com.android.tools.idea.editors.gfxtrace.service.path;

import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class MemoryKind {
  public static final MemoryKind Unknown = new MemoryKind(0, "Unknown");
  public static final int UnknownValue = 0;
  public static final MemoryKind Integer = new MemoryKind(1, "Integer");
  public static final int IntegerValue = 1;
  public static final MemoryKind Address = new MemoryKind(2, "Address");
  public static final int AddressValue = 2;
  public static final MemoryKind Float = new MemoryKind(3, "Float");
  public static final int FloatValue = 3;
  public static final MemoryKind Char = new MemoryKind(4, "Char");
  public static final int CharValue = 4;
  public static final MemoryKind Void = new MemoryKind(5, "Void");
  public static final int VoidValue = 5;

  private static final ImmutableMap<Integer, MemoryKind> VALUES = ImmutableMap.<Integer, MemoryKind>builder()
    .put(0, Unknown)
    .put(1, Integer)
    .put(2, Address)
    .put(3, Float)
    .put(4, Char)
    .put(5, Void)
    .build();

  private final int myValue;
  private final String myName;

  private MemoryKind(int v, String n) {
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

  public static MemoryKind decode(@NotNull Decoder d) throws IOException {
    return findOrCreate(d.int32());
  }

  public static MemoryKind find(int value) {
    return VALUES.get(value);
  }

  public static MemoryKind findOrCreate(int value) {
    MemoryKind result = VALUES.get(value);
    return (result == null) ? new MemoryKind(value, null) : result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof MemoryKind)) return false;
    return myValue == ((MemoryKind)o).myValue;
  }

  @Override
  public int hashCode() {
    return myValue;
  }

  @Override
  public String toString() {
    return (myName == null) ? "MemoryKind(" + myValue + ")" : myName;
  }
}
