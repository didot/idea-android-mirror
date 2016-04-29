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

import com.android.tools.rpclib.schema.*;
import com.android.tools.idea.editors.gfxtrace.service.path.AtomPath;
import com.android.tools.idea.editors.gfxtrace.service.path.DevicePath;
import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

final class CallGetFramebufferColor implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  private DevicePath myDevice;
  private AtomPath myAfter;
  private RenderSettings mySettings;

  // Constructs a default-initialized {@link CallGetFramebufferColor}.
  public CallGetFramebufferColor() {}


  public DevicePath getDevice() {
    return myDevice;
  }

  public CallGetFramebufferColor setDevice(DevicePath v) {
    myDevice = v;
    return this;
  }

  public AtomPath getAfter() {
    return myAfter;
  }

  public CallGetFramebufferColor setAfter(AtomPath v) {
    myAfter = v;
    return this;
  }

  public RenderSettings getSettings() {
    return mySettings;
  }

  public CallGetFramebufferColor setSettings(RenderSettings v) {
    mySettings = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("service", "callGetFramebufferColor", "", "");

  static {
    ENTITY.setFields(new Field[]{
      new Field("device", new Pointer(new Struct(DevicePath.Klass.INSTANCE.entity()))),
      new Field("after", new Pointer(new Struct(AtomPath.Klass.INSTANCE.entity()))),
      new Field("settings", new Struct(RenderSettings.Klass.INSTANCE.entity())),
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
    public BinaryObject create() { return new CallGetFramebufferColor(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      CallGetFramebufferColor o = (CallGetFramebufferColor)obj;
      e.object(o.myDevice);
      e.object(o.myAfter);
      e.value(o.mySettings);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      CallGetFramebufferColor o = (CallGetFramebufferColor)obj;
      o.myDevice = (DevicePath)d.object();
      o.myAfter = (AtomPath)d.object();
      o.mySettings = new RenderSettings();
      d.value(o.mySettings);
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
