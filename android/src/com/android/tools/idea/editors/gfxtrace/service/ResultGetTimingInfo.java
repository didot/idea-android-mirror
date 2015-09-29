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

import com.android.tools.idea.editors.gfxtrace.service.path.TimingInfoPath;
import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

final class ResultGetTimingInfo implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  private TimingInfoPath myValue;

  // Constructs a default-initialized {@link ResultGetTimingInfo}.
  public ResultGetTimingInfo() {}


  public TimingInfoPath getValue() {
    return myValue;
  }

  public ResultGetTimingInfo setValue(TimingInfoPath v) {
    myValue = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }

  private static final byte[] IDBytes = {32, 11, -67, 8, 78, 113, 13, -124, -17, 109, -116, 46, 6, -106, -79, -24, -72, 33, -60, -25, };
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
    public BinaryObject create() { return new ResultGetTimingInfo(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      ResultGetTimingInfo o = (ResultGetTimingInfo)obj;
      e.object(o.myValue);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      ResultGetTimingInfo o = (ResultGetTimingInfo)obj;
      o.myValue = (TimingInfoPath)d.object();
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
