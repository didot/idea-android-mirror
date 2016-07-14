/*
 * Copyright (C) 2016 The Android Open Source Project
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

import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.*;
import com.android.tools.rpclib.schema.*;

import java.io.IOException;

public final class ReportItemPath extends Path {
  @Override
  public String getSegmentString() {
    return "Items[" + String.valueOf(myIndex) + ']';
  }

  @Override
  public ReportPath getParent() {
    return myReport;
  }

  //<<<Start:Java.ClassBody:1>>>
  private ReportPath myReport;
  private long myIndex;

  // Constructs a default-initialized {@link ReportItemPath}.
  public ReportItemPath() {}


  public ReportPath getReport() {
    return myReport;
  }

  public ReportItemPath setReport(ReportPath v) {
    myReport = v;
    return this;
  }

  public long getIndex() {
    return myIndex;
  }

  public ReportItemPath setIndex(long v) {
    myIndex = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("path", "ReportItem", "", "");

  static {
    ENTITY.setFields(new Field[]{
      new Field("Report", new Pointer(new Struct(ReportPath.Klass.INSTANCE.entity()))),
      new Field("Index", new Primitive("uint64", Method.Uint64)),
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
    public BinaryObject create() { return new ReportItemPath(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      ReportItemPath o = (ReportItemPath)obj;
      e.object(o.myReport);
      e.uint64(o.myIndex);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      ReportItemPath o = (ReportItemPath)obj;
      o.myReport = (ReportPath)d.object();
      o.myIndex = d.uint64();
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
