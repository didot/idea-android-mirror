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

import com.android.tools.rpclib.schema.*;
import com.android.tools.rpclib.binary.BinaryClass;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.android.tools.rpclib.binary.Namespace;
import com.android.tools.idea.editors.gfxtrace.service.path.BlobPath;

import java.io.IOException;

public final class ImageInfo implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  private Format myFormat;
  private int myWidth;
  private int myHeight;
  private BlobPath myData;

  // Constructs a default-initialized {@link ImageInfo}.
  public ImageInfo() {}


  public Format getFormat() {
    return myFormat;
  }

  public ImageInfo setFormat(Format v) {
    myFormat = v;
    return this;
  }

  public int getWidth() {
    return myWidth;
  }

  public ImageInfo setWidth(int v) {
    myWidth = v;
    return this;
  }

  public int getHeight() {
    return myHeight;
  }

  public ImageInfo setHeight(int v) {
    myHeight = v;
    return this;
  }

  public BlobPath getData() {
    return myData;
  }

  public ImageInfo setData(BlobPath v) {
    myData = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("image","Info","","");

  static {
    ENTITY.setFields(new Field[]{
      new Field("Format", new Interface("Format")),
      new Field("Width", new Primitive("uint32", Method.Uint32)),
      new Field("Height", new Primitive("uint32", Method.Uint32)),
      new Field("Data", new Pointer(new Struct(BlobPath.Klass.INSTANCE.entity()))),
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
    public BinaryObject create() { return new ImageInfo(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      ImageInfo o = (ImageInfo)obj;
      e.object(o.myFormat.unwrap());
      e.uint32(o.myWidth);
      e.uint32(o.myHeight);
      e.object(o.myData);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      ImageInfo o = (ImageInfo)obj;
      o.myFormat = Format.wrap(d.object());
      o.myWidth = d.uint32();
      o.myHeight = d.uint32();
      o.myData = (BlobPath)d.object();
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
