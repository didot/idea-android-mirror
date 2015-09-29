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

import com.android.tools.rpclib.binary.*;
import com.android.tools.rpclib.schema.ConstantSet;
import com.android.tools.rpclib.schema.Entity;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class Schema implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  private Entity[] myClasses;
  private ConstantSet[] myConstants;

  // Constructs a default-initialized {@link Schema}.
  public Schema() {}


  public Entity[] getClasses() {
    return myClasses;
  }

  public Schema setClasses(Entity[] v) {
    myClasses = v;
    return this;
  }

  public ConstantSet[] getConstants() {
    return myConstants;
  }

  public Schema setConstants(ConstantSet[] v) {
    myConstants = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }

  private static final byte[] IDBytes = {88, 28, 37, -60, -66, -89, -81, 27, 21, 67, -31, 70, -29, 120, -120, -70, 112, -36, 99, -31, };
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
    public BinaryObject create() { return new Schema(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      Schema o = (Schema)obj;
      e.uint32(o.myClasses.length);
      for (int i = 0; i < o.myClasses.length; i++) {
        e.object(o.myClasses[i]);
      }
      e.uint32(o.myConstants.length);
      for (int i = 0; i < o.myConstants.length; i++) {
        e.value(o.myConstants[i]);
      }
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      Schema o = (Schema)obj;
      o.myClasses = new Entity[d.uint32()];
      for (int i = 0; i <o.myClasses.length; i++) {
        o.myClasses[i] = (Entity)d.object();
      }
      o.myConstants = new ConstantSet[d.uint32()];
      for (int i = 0; i <o.myConstants.length; i++) {
        o.myConstants[i] = new ConstantSet();
        d.value(o.myConstants[i]);
      }
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
