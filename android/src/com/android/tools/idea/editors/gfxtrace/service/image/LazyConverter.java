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
package com.android.tools.idea.editors.gfxtrace.service.image;

import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.BinaryClass;
import com.android.tools.rpclib.binary.BinaryID;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.android.tools.rpclib.binary.Namespace;

import java.io.IOException;

public final class LazyConverter implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  private BinaryID myData;
  private int myWidth;
  private int myHeight;
  private Format myFormatFrom;
  private Format myFormatTo;

  // Constructs a default-initialized {@link LazyConverter}.
  public LazyConverter() {}


  public BinaryID getData() {
    return myData;
  }

  public LazyConverter setData(BinaryID v) {
    myData = v;
    return this;
  }

  public int getWidth() {
    return myWidth;
  }

  public LazyConverter setWidth(int v) {
    myWidth = v;
    return this;
  }

  public int getHeight() {
    return myHeight;
  }

  public LazyConverter setHeight(int v) {
    myHeight = v;
    return this;
  }

  public Format getFormatFrom() {
    return myFormatFrom;
  }

  public LazyConverter setFormatFrom(Format v) {
    myFormatFrom = v;
    return this;
  }

  public Format getFormatTo() {
    return myFormatTo;
  }

  public LazyConverter setFormatTo(Format v) {
    myFormatTo = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }

  private static final byte[] IDBytes = {70, 101, 101, 62, -21, 24, 110, -60, -111, -24, 74, 74, 49, -28, -38, -69, 116, -46, -52, 74, };
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
    public BinaryObject create() { return new LazyConverter(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      LazyConverter o = (LazyConverter)obj;
      e.id(o.myData);
      e.uint32(o.myWidth);
      e.uint32(o.myHeight);
      e.object(o.myFormatFrom.unwrap());
      e.object(o.myFormatTo.unwrap());
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      LazyConverter o = (LazyConverter)obj;
      o.myData = d.id();
      o.myWidth = d.uint32();
      o.myHeight = d.uint32();
      o.myFormatFrom = Format.wrap(d.object());
      o.myFormatTo = Format.wrap(d.object());
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
