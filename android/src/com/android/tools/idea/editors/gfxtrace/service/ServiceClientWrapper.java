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

import com.android.tools.rpclib.binary.BinaryID;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.rpclib.any.Box;
import com.android.tools.idea.editors.gfxtrace.service.stringtable.Info;
import com.android.tools.idea.editors.gfxtrace.service.path.CapturePath;
import com.android.tools.idea.editors.gfxtrace.service.path.DevicePath;
import com.android.tools.idea.editors.gfxtrace.service.path.ImageInfoPath;
import com.android.tools.idea.editors.gfxtrace.service.path.AtomPath;
import com.android.tools.rpclib.schema.Message;
import com.android.tools.idea.editors.gfxtrace.service.stringtable.StringTable;
import com.android.tools.idea.editors.gfxtrace.service.path.TimingInfoPath;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.GfxAPIProtos.FramebufferAttachment;
import com.google.common.util.concurrent.ListenableFuture;

public class ServiceClientWrapper extends ServiceClient {
  protected final ServiceClient myClient;
  public ServiceClientWrapper(ServiceClient client) {
    myClient = client;
  }
  @Override
  public ListenableFuture<Path> follow(Path p) {
    return myClient.follow(p);
  }
  @Override
  public ListenableFuture<Object> get(Path p) {
    return myClient.get(p);
  }
  @Override
  public ListenableFuture<Info[]> getAvailableStringTables() {
    return myClient.getAvailableStringTables();
  }
  @Override
  public ListenableFuture<CapturePath[]> getCaptures() {
    return myClient.getCaptures();
  }
  @Override
  public ListenableFuture<DevicePath[]> getDevices() {
    return myClient.getDevices();
  }
  @Override
  public ListenableFuture<String[]> getFeatures() {
    return myClient.getFeatures();
  }
  @Override
  public ListenableFuture<ImageInfoPath> getFramebufferAttachment(DevicePath device, AtomPath after, FramebufferAttachment attachment, RenderSettings settings) {
    return myClient.getFramebufferAttachment(device, after, attachment, settings);
  }
  @Override
  public ListenableFuture<ImageInfoPath> getFramebufferColor(DevicePath device, AtomPath after, RenderSettings settings) {
    return myClient.getFramebufferColor(device, after, settings);
  }
  @Override
  public ListenableFuture<ImageInfoPath> getFramebufferDepth(DevicePath device, AtomPath after) {
    return myClient.getFramebufferDepth(device, after);
  }
  @Override
  public ListenableFuture<Message> getSchema() {
    return myClient.getSchema();
  }
  @Override
  public ListenableFuture<StringTable> getStringTable(Info info) {
    return myClient.getStringTable(info);
  }
  @Override
  public ListenableFuture<TimingInfoPath> getTimingInfo(DevicePath device, CapturePath capture, TimingFlags flags) {
    return myClient.getTimingInfo(device, capture, flags);
  }
  @Override
  public ListenableFuture<CapturePath> importCapture(String name, byte[] Data) {
    return myClient.importCapture(name, Data);
  }
  @Override
  public ListenableFuture<CapturePath> loadCapture(String path) {
    return myClient.loadCapture(path);
  }
  @Override
  public ListenableFuture<DevicePath> registerAndroidDevice(String serial) {
    return myClient.registerAndroidDevice(serial);
  }
  @Override
  public ListenableFuture<Path> set(Path p, Object v) {
    return myClient.set(p, v);
  }
}
