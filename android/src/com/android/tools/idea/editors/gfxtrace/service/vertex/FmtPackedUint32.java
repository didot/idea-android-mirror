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
package com.android.tools.idea.editors.gfxtrace.service.vertex;

import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.*;
import com.android.tools.rpclib.schema.*;
import com.android.tools.idea.editors.gfxtrace.service.vertex.VertexProtos.VectorElement;

import java.io.IOException;

public final class FmtPackedUint32 extends Format implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  private VectorElement[] myOrder;
  private boolean mySigned;
  private boolean myNormalized;
  private byte[] myBitCounts;

  // Constructs a default-initialized {@link FmtPackedUint32}.
  public FmtPackedUint32() {}


  public VectorElement[] getOrder() {
    return myOrder;
  }

  public FmtPackedUint32 setOrder(VectorElement[] v) {
    myOrder = v;
    return this;
  }

  public boolean getSigned() {
    return mySigned;
  }

  public FmtPackedUint32 setSigned(boolean v) {
    mySigned = v;
    return this;
  }

  public boolean getNormalized() {
    return myNormalized;
  }

  public FmtPackedUint32 setNormalized(boolean v) {
    myNormalized = v;
    return this;
  }

  public byte[] getBitCounts() {
    return myBitCounts;
  }

  public FmtPackedUint32 setBitCounts(byte[] v) {
    myBitCounts = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("vertex", "FmtPackedUint32", "", "");

  static {
    ENTITY.setFields(new Field[]{
      new Field("Order", new Slice("VectorOrder", new Primitive("VectorElement", Method.Int32))),
      new Field("Signed", new Primitive("bool", Method.Bool)),
      new Field("Normalized", new Primitive("bool", Method.Bool)),
      new Field("BitCounts", new Slice("", new Primitive("uint8", Method.Uint8))),
    });
    Namespace.register(Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public Entity entity() { return ENTITY; }

    @Override @NotNull
    public BinaryObject create() { return new FmtPackedUint32(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      FmtPackedUint32 o = (FmtPackedUint32)obj;
      e.uint32(o.myOrder.length);
      for (int i = 0; i < o.myOrder.length; i++) {
        e.int32(o.myOrder[i].getNumber());
      }
      e.bool(o.mySigned);
      e.bool(o.myNormalized);
      e.uint32(o.myBitCounts.length);
      e.write(o.myBitCounts, o.myBitCounts.length);

    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      FmtPackedUint32 o = (FmtPackedUint32)obj;
      o.myOrder = new VectorElement[d.uint32()];
      for (int i = 0; i <o.myOrder.length; i++) {
        o.myOrder[i] = VectorElement.valueOf(d.int32());
      }
      o.mySigned = d.bool();
      o.myNormalized = d.bool();
      o.myBitCounts = new byte[d.uint32()];
      d.read(o.myBitCounts, o.myBitCounts.length);

    }
    //<<<End:Java.KlassBody:2>>>
  }
}
