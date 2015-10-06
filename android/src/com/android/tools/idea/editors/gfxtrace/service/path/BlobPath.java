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

import com.android.tools.rpclib.schema.*;
import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class BlobPath extends Path {
  @Override
  public StringBuilder stringPath(StringBuilder builder) {
    return builder.append("Blob(").append(myID).append(")");
  }

  @Override
  public Path getParent() {
    return null;
  }

  //<<<Start:Java.ClassBody:1>>>
  private BinaryID myID;

  // Constructs a default-initialized {@link BlobPath}.
  public BlobPath() {}


  public BinaryID getID() {
    return myID;
  }

  public BlobPath setID(BinaryID v) {
    myID = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("path","Blob","","");

  static {
    Namespace.register(Klass.INSTANCE);
    ENTITY.setFields(new Field[]{
      new Field("ID", new Array("binary.ID", new Primitive("byte", Method.Uint8), 20)),
    });
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public Entity entity() { return ENTITY; }

    @Override @NotNull
    public BinaryObject create() { return new BlobPath(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      BlobPath o = (BlobPath)obj;
      o.myID.write(e);

    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      BlobPath o = (BlobPath)obj;
      o.myID = new BinaryID(d);

    }
    //<<<End:Java.KlassBody:2>>>
  }
}
