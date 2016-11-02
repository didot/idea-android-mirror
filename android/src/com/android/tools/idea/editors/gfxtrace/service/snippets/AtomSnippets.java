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
package com.android.tools.idea.editors.gfxtrace.service.snippets;

import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.*;
import com.android.tools.rpclib.schema.*;

import java.io.IOException;

public final class AtomSnippets implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  private String myAtomName;
  private KindredSnippets[] mySnippets;

  // Constructs a default-initialized {@link AtomSnippets}.
  public AtomSnippets() {}


  public String getAtomName() {
    return myAtomName;
  }

  public AtomSnippets setAtomName(String v) {
    myAtomName = v;
    return this;
  }

  public KindredSnippets[] getSnippets() {
    return mySnippets;
  }

  public AtomSnippets setSnippets(KindredSnippets[] v) {
    mySnippets = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("snippets", "AtomSnippets", "", "");

  static {
    ENTITY.setFields(new Field[]{
      new Field("AtomName", new Primitive("string", Method.String)),
      new Field("Snippets", new Slice("", new Interface("KindredSnippets"))),
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
    public BinaryObject create() { return new AtomSnippets(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      AtomSnippets o = (AtomSnippets)obj;
      e.string(o.myAtomName);
      e.uint32(o.mySnippets.length);
      for (int i = 0; i < o.mySnippets.length; i++) {
        e.object(o.mySnippets[i] == null ? null : o.mySnippets[i].unwrap());
      }
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      AtomSnippets o = (AtomSnippets)obj;
      o.myAtomName = d.string();
      o.mySnippets = new KindredSnippets[d.uint32()];
      for (int i = 0; i <o.mySnippets.length; i++) {
        o.mySnippets[i] = KindredSnippets.wrap(d.object());
      }
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
