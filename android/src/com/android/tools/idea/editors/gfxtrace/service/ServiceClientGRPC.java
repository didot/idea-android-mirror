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
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.android.tools.rpclib.rpccore.RpcException;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf3jarjar.ByteString;
import io.grpc.ManagedChannel;
import java.io.IOException;

public final class ServiceClientGRPC extends ServiceClient {
  private final GapidGrpc.GapidFutureClient myClient;
  private final String myAuthToken;

  public ServiceClientGRPC(ManagedChannel channel, String authToken) {
    myClient = GapidGrpc.newFutureStub(channel);
    myAuthToken = authToken;
  }

  @Override
  public ListenableFuture<Path> follow(Path p) {
    return new FollowInvoker(p).invoke();
  }

  @Override
  public ListenableFuture<Object> get(Path p) {
    return new GetInvoker(p).invoke();
  }

  @Override
  public ListenableFuture<Info[]> getAvailableStringTables() {
    return new GetAvailableStringTablesInvoker().invoke();
  }

  @Override
  public ListenableFuture<CapturePath[]> getCaptures() {
    return new GetCapturesInvoker().invoke();
  }

  @Override
  public ListenableFuture<DevicePath[]> getDevices() {
    return new GetDevicesInvoker().invoke();
  }

  @Override
  public ListenableFuture<String[]> getFeatures() {
    return new GetFeaturesInvoker().invoke();
  }

  @Override
  public ListenableFuture<ImageInfoPath> getFramebufferColor(DevicePath device, AtomPath after, RenderSettings settings) {
    return new GetFramebufferColorInvoker(device, after, settings).invoke();
  }

  @Override
  public ListenableFuture<ImageInfoPath> getFramebufferDepth(DevicePath device, AtomPath after) {
    return new GetFramebufferDepthInvoker(device, after).invoke();
  }

  @Override
  public ListenableFuture<Message> getSchema() {
    return new GetSchemaInvoker().invoke();
  }

  @Override
  public ListenableFuture<StringTable> getStringTable(Info info) {
    return new GetStringTableInvoker(info).invoke();
  }

  @Override
  public ListenableFuture<TimingInfoPath> getTimingInfo(DevicePath device, CapturePath capture, TimingFlags flags) {
    return new GetTimingInfoInvoker(device, capture, flags).invoke();
  }

  @Override
  public ListenableFuture<CapturePath> importCapture(String name, byte[] Data) {
    return new ImportCaptureInvoker(name, Data).invoke();
  }

  @Override
  public ListenableFuture<CapturePath> loadCapture(String path) {
    return new LoadCaptureInvoker(path).invoke();
  }

  @Override
  public ListenableFuture<Path> set(Path p, Object v) {
    return new SetInvoker(p, v).invoke();
  }

  private interface Invoker<T> extends AsyncFunction<ServiceProtos.Response, T> {
    ListenableFuture<T> invoke();
  }

  private class FollowInvoker implements Invoker<Path> {
    private final CallFollow myCall;
    private final Exception myStack = new StackException();

    private FollowInvoker(Path p) {
      myCall = new CallFollow();
      myCall.setP(p);
    }

    @Override
    public ListenableFuture<Path> invoke() {
      try {
        return Futures.transform(myClient.follow(asRequest(myCall)), this);
      } catch (IOException e) {
        return Futures.immediateFailedFuture(e);
      }
    }

    @Override
    public ListenableFuture<Path> apply(ServiceProtos.Response input) throws Exception {
      try {
        ResultFollow result = (ResultFollow)fromResponse(input);
        return Futures.immediateFuture(result.getValue());
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetInvoker implements Invoker<Object> {
    private final CallGet myCall;
    private final Exception myStack = new StackException();

    private GetInvoker(Path p) {
      myCall = new CallGet();
      myCall.setP(p);
    }

    @Override
    public ListenableFuture<Object> invoke() {
      try {
        return Futures.transform(myClient.get(asRequest(myCall)), this);
      } catch (IOException e) {
        return Futures.immediateFailedFuture(e);
      }
    }

    @Override
    public ListenableFuture<Object> apply(ServiceProtos.Response input) throws Exception {
      try {
        ResultGet result = (ResultGet)fromResponse(input);
        return Futures.immediateFuture(result.getValue());
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetAvailableStringTablesInvoker implements Invoker<Info[]> {
    private final CallGetAvailableStringTables myCall;
    private final Exception myStack = new StackException();

    private GetAvailableStringTablesInvoker() {
      myCall = new CallGetAvailableStringTables();
    }

    @Override
    public ListenableFuture<Info[]> invoke() {
      try {
        return Futures.transform(myClient.getAvailableStringTables(asRequest(myCall)), this);
      } catch (IOException e) {
        return Futures.immediateFailedFuture(e);
      }
    }

    @Override
    public ListenableFuture<Info[]> apply(ServiceProtos.Response input) throws Exception {
      try {
        ResultGetAvailableStringTables result = (ResultGetAvailableStringTables)fromResponse(input);
        return Futures.immediateFuture(result.getValue());
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetCapturesInvoker implements Invoker<CapturePath[]> {
    private final CallGetCaptures myCall;
    private final Exception myStack = new StackException();

    private GetCapturesInvoker() {
      myCall = new CallGetCaptures();
    }

    @Override
    public ListenableFuture<CapturePath[]> invoke() {
      try {
        return Futures.transform(myClient.getCaptures(asRequest(myCall)), this);
      } catch (IOException e) {
        return Futures.immediateFailedFuture(e);
      }
    }

    @Override
    public ListenableFuture<CapturePath[]> apply(ServiceProtos.Response input) throws Exception {
      try {
        ResultGetCaptures result = (ResultGetCaptures)fromResponse(input);
        return Futures.immediateFuture(result.getValue());
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetDevicesInvoker implements Invoker<DevicePath[]> {
    private final CallGetDevices myCall;
    private final Exception myStack = new StackException();

    private GetDevicesInvoker() {
      myCall = new CallGetDevices();
    }

    @Override
    public ListenableFuture<DevicePath[]> invoke() {
      try {
        return Futures.transform(myClient.getDevices(asRequest(myCall)), this);
      } catch (IOException e) {
        return Futures.immediateFailedFuture(e);
      }
    }

    @Override
    public ListenableFuture<DevicePath[]> apply(ServiceProtos.Response input) throws Exception {
      try {
        ResultGetDevices result = (ResultGetDevices)fromResponse(input);
        return Futures.immediateFuture(result.getValue());
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetFeaturesInvoker implements Invoker<String[]> {
    private final CallGetFeatures myCall;
    private final Exception myStack = new StackException();

    private GetFeaturesInvoker() {
      myCall = new CallGetFeatures();
    }

    @Override
    public ListenableFuture<String[]> invoke() {
      try {
        return Futures.transform(myClient.getFeatures(asRequest(myCall)), this);
      } catch (IOException e) {
        return Futures.immediateFailedFuture(e);
      }
    }

    @Override
    public ListenableFuture<String[]> apply(ServiceProtos.Response input) throws Exception {
      try {
        ResultGetFeatures result = (ResultGetFeatures)fromResponse(input);
        return Futures.immediateFuture(result.getValue());
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetFramebufferColorInvoker implements Invoker<ImageInfoPath> {
    private final CallGetFramebufferColor myCall;
    private final Exception myStack = new StackException();

    private GetFramebufferColorInvoker(DevicePath device, AtomPath after, RenderSettings settings) {
      myCall = new CallGetFramebufferColor();
      myCall.setDevice(device);
      myCall.setAfter(after);
      myCall.setSettings(settings);
    }

    @Override
    public ListenableFuture<ImageInfoPath> invoke() {
      try {
        return Futures.transform(myClient.getFramebufferColor(asRequest(myCall)), this);
      } catch (IOException e) {
        return Futures.immediateFailedFuture(e);
      }
    }

    @Override
    public ListenableFuture<ImageInfoPath> apply(ServiceProtos.Response input) throws Exception {
      try {
        ResultGetFramebufferColor result = (ResultGetFramebufferColor)fromResponse(input);
        return Futures.immediateFuture(result.getValue());
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetFramebufferDepthInvoker implements Invoker<ImageInfoPath> {
    private final CallGetFramebufferDepth myCall;
    private final Exception myStack = new StackException();

    private GetFramebufferDepthInvoker(DevicePath device, AtomPath after) {
      myCall = new CallGetFramebufferDepth();
      myCall.setDevice(device);
      myCall.setAfter(after);
    }

    @Override
    public ListenableFuture<ImageInfoPath> invoke() {
      try {
        return Futures.transform(myClient.getFramebufferDepth(asRequest(myCall)), this);
      } catch (IOException e) {
        return Futures.immediateFailedFuture(e);
      }
    }

    @Override
    public ListenableFuture<ImageInfoPath> apply(ServiceProtos.Response input) throws Exception {
      try {
        ResultGetFramebufferDepth result = (ResultGetFramebufferDepth)fromResponse(input);
        return Futures.immediateFuture(result.getValue());
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetSchemaInvoker implements Invoker<Message> {
    private final CallGetSchema myCall;
    private final Exception myStack = new StackException();

    private GetSchemaInvoker() {
      myCall = new CallGetSchema();
    }

    @Override
    public ListenableFuture<Message> invoke() {
      try {
        return Futures.transform(myClient.getSchema(asRequest(myCall)), this);
      } catch (IOException e) {
        return Futures.immediateFailedFuture(e);
      }
    }

    @Override
    public ListenableFuture<Message> apply(ServiceProtos.Response input) throws Exception {
      try {
        ResultGetSchema result = (ResultGetSchema)fromResponse(input);
        return Futures.immediateFuture(result.getValue());
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetStringTableInvoker implements Invoker<StringTable> {
    private final CallGetStringTable myCall;
    private final Exception myStack = new StackException();

    private GetStringTableInvoker(Info info) {
      myCall = new CallGetStringTable();
      myCall.setInfo(info);
    }

    @Override
    public ListenableFuture<StringTable> invoke() {
      try {
        return Futures.transform(myClient.getStringTable(asRequest(myCall)), this);
      } catch (IOException e) {
        return Futures.immediateFailedFuture(e);
      }
    }

    @Override
    public ListenableFuture<StringTable> apply(ServiceProtos.Response input) throws Exception {
      try {
        ResultGetStringTable result = (ResultGetStringTable)fromResponse(input);
        return Futures.immediateFuture(result.getValue());
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetTimingInfoInvoker implements Invoker<TimingInfoPath> {
    private final CallGetTimingInfo myCall;
    private final Exception myStack = new StackException();

    private GetTimingInfoInvoker(DevicePath device, CapturePath capture, TimingFlags flags) {
      myCall = new CallGetTimingInfo();
      myCall.setDevice(device);
      myCall.setCapture(capture);
      myCall.setFlags(flags);
    }

    @Override
    public ListenableFuture<TimingInfoPath> invoke() {
      try {
        return Futures.transform(myClient.getTimingInfo(asRequest(myCall)), this);
      } catch (IOException e) {
        return Futures.immediateFailedFuture(e);
      }
    }

    @Override
    public ListenableFuture<TimingInfoPath> apply(ServiceProtos.Response input) throws Exception {
      try {
        ResultGetTimingInfo result = (ResultGetTimingInfo)fromResponse(input);
        return Futures.immediateFuture(result.getValue());
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class ImportCaptureInvoker implements Invoker<CapturePath> {
    private final CallImportCapture myCall;
    private final Exception myStack = new StackException();

    private ImportCaptureInvoker(String name, byte[] Data) {
      myCall = new CallImportCapture();
      myCall.setName(name);
      myCall.setData(Data);
    }

    @Override
    public ListenableFuture<CapturePath> invoke() {
      try {
        return Futures.transform(myClient.importCapture(asRequest(myCall)), this);
      } catch (IOException e) {
        return Futures.immediateFailedFuture(e);
      }
    }

    @Override
    public ListenableFuture<CapturePath> apply(ServiceProtos.Response input) throws Exception {
      try {
        ResultImportCapture result = (ResultImportCapture)fromResponse(input);
        return Futures.immediateFuture(result.getValue());
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class LoadCaptureInvoker implements Invoker<CapturePath> {
    private final CallLoadCapture myCall;
    private final Exception myStack = new StackException();

    private LoadCaptureInvoker(String path) {
      myCall = new CallLoadCapture();
      myCall.setPath(path);
    }

    @Override
    public ListenableFuture<CapturePath> invoke() {
      try {
        return Futures.transform(myClient.loadCapture(asRequest(myCall)), this);
      } catch (IOException e) {
        return Futures.immediateFailedFuture(e);
      }
    }

    @Override
    public ListenableFuture<CapturePath> apply(ServiceProtos.Response input) throws Exception {
      try {
        ResultLoadCapture result = (ResultLoadCapture)fromResponse(input);
        return Futures.immediateFuture(result.getValue());
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class SetInvoker implements Invoker<Path> {
    private final CallSet myCall;
    private final Exception myStack = new StackException();

    private SetInvoker(Path p, Object v) {
      myCall = new CallSet();
      myCall.setP(p);
      myCall.setV(v);
    }

    @Override
    public ListenableFuture<Path> invoke() {
      try {
        return Futures.transform(myClient.set(asRequest(myCall)), this);
      } catch (IOException e) {
        return Futures.immediateFailedFuture(e);
      }
    }

    @Override
    public ListenableFuture<Path> apply(ServiceProtos.Response input) throws Exception {
      try {
        ResultSet result = (ResultSet)fromResponse(input);
        return Futures.immediateFuture(result.getValue());
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }

  private ServiceProtos.Request asRequest(BinaryObject call) throws IOException {
    ByteString.Output out = ByteString.newOutput();
    Encoder e = new Encoder(out);
    e.object(call);
    return ServiceProtos.Request.newBuilder()
      .setRequest(out.toByteString())
      .setToken(myAuthToken)
      .build();
  }

  private static BinaryObject fromResponse(ServiceProtos.Response resp) throws IOException, RpcException {
    Decoder d = new Decoder(resp.getResponse().newInput());
    BinaryObject res = d.object();
    if (res instanceof RpcException) {
      throw (RpcException)res;
    }
    return res;
  }

  private static class StackException extends Exception {
    @Override
    public String toString() {
      return String.valueOf(getCause());
    }
  }
}
