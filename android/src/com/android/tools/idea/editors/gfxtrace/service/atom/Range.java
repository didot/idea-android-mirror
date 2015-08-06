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
package com.android.tools.idea.editors.gfxtrace.service.atom;

import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.BinaryClass;
import com.android.tools.rpclib.binary.BinaryID;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.android.tools.rpclib.binary.Namespace;

import java.io.IOException;

public final class Range implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  long myStart;
  long myEnd;

  // Constructs a default-initialized {@link Range}.
  public Range() {}


  public long getStart() {
    return myStart;
  }

  public Range setStart(long v) {
    myStart = v;
    return this;
  }

  public long getEnd() {
    return myEnd;
  }

  public Range setEnd(long v) {
    myEnd = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }

  private static final byte[] IDBytes = {23, -99, -5, 1, 126, 121, 28, 91, 124, -56, -58, -54, -97, -119, 96, 27, -21, -24, -79, 20, };
  public static final BinaryID ID = new BinaryID(IDBytes);

  static {
    Namespace.register(ID, Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public BinaryID id() { return ID; }

    @Override @NotNull
    public BinaryObject create() { return new Range(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      Range o = (Range)obj;
      e.uint64(o.myStart);
      e.uint64(o.myEnd);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      Range o = (Range)obj;
      o.myStart = d.uint64();
      o.myEnd = d.uint64();
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
