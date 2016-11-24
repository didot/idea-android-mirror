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
 */
package com.android.tools.profilers.memory;

import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.AllocationEvent;
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.util.containers.HashMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class AllocationObjects implements MemoryObjects {
  @NotNull private final RootAllocationsNode myRoot;
  @NotNull private final MemoryServiceBlockingStub myClient;
  private final int myAppId;
  private long myLastStartTimeUs = Long.MAX_VALUE;
  private long myLastEndTimeUs = Long.MIN_VALUE;

  public AllocationObjects(@NotNull MemoryServiceBlockingStub client, int appId) {
    myClient = client;
    myAppId = appId;
    myRoot = new RootAllocationsNode();
  }

  @NotNull
  @Override
  public MemoryNode getRootNode() {
    return myRoot;
  }

  @Override
  public void dispose() {

  }

  private class RootAllocationsNode implements MemoryNode {
    @Override
    public String toString() {
      return "Allocations" +
             (myLastStartTimeUs != Long.MAX_VALUE ? " from " + myLastStartTimeUs : "") +
             (myLastEndTimeUs != Long.MIN_VALUE ? " to " + myLastEndTimeUs : "");
    }

    @NotNull
    @Override
    public List<MemoryNode> getSubList(long startTimeUs, long endTimeUs) {
      return Collections.singletonList(new HeapNode());
    }

    @NotNull
    @Override
    public List<Capability> getCapabilities() {
      return Collections.singletonList(Capability.LABEL);
    }
  }

  private class HeapNode implements MemoryNode {
    @Override
    public String toString() {
      return getName();
    }

    @NotNull
    @Override
    public String getName() {
      return "default";
    }

    @NotNull
    @Override
    public List<MemoryNode> getSubList(long startTimeUs, long endTimeUs) {
      myLastStartTimeUs = startTimeUs;
      myLastEndTimeUs = endTimeUs;

      // TODO add caching
      long buffer = TimeUnit.SECONDS.toNanos(1);
      long startTimeNs = TimeUnit.MICROSECONDS.toNanos(startTimeUs) - buffer;
      long endTimeNs = TimeUnit.MICROSECONDS.toNanos(endTimeUs) + buffer;

      AllocationTrackingEnvironmentResponse environmentResponse = myClient.listAllocationTrackingEnvironments(
        AllocationTrackingEnvironmentRequest.newBuilder().setAppId(myAppId).setStartTime(startTimeNs).setEndTime(endTimeNs).build());

      TIntObjectHashMap<ClassNode> classNodes = new TIntObjectHashMap<>();
      Map<ByteString, AllocationStack> callStacks = new HashMap<>();
      environmentResponse.getAllocatedClassesList().stream()
        .map(className -> classNodes.put(className.getClassId(), new ClassNode(className)));
      environmentResponse.getAllocationStacksList().stream().map(callStack -> callStacks.putIfAbsent(callStack.getStackId(), callStack));

      MemoryData response = myClient
        .getData(MemoryProfiler.MemoryRequest.newBuilder().setAppId(myAppId).setStartTime(startTimeNs).setEndTime(endTimeNs).build());
      // TODO make sure class IDs fall into a global pool
      for (AllocationEvent event : response.getAllocationEventsList()) {
        assert classNodes.contains(event.getAllocatedClassId());
        assert callStacks.containsKey(event.getAllocationStackId());
        classNodes.get(event.getAllocatedClassId()).addInstance(new InstanceNode(event, callStacks.get(event.getAllocationStackId())));
      }

      List<MemoryNode> results = new ArrayList<>(classNodes.size());
      classNodes.forEachValue(results::add);
      return results;
    }

    @NotNull
    @Override
    public List<Capability> getCapabilities() {
      return Arrays.asList(Capability.LABEL, Capability.CHILDREN_COUNT, Capability.ELEMENT_SIZE);
    }
  }

  private static class ClassNode implements MemoryNode {
    @NotNull private final AllocatedClass myAllocatedClass;
    @NotNull private final List<MemoryNode> myInstanceNodes = new ArrayList<>();

    public ClassNode(@NotNull AllocatedClass allocatedClass) {
      myAllocatedClass = allocatedClass;
    }

    @NotNull
    @Override
    public String getName() {
      return myAllocatedClass.getClassName();
    }

    public void addInstance(@NotNull InstanceNode node) {
      myInstanceNodes.add(node);
    }

    @Override
    public int getChildrenCount() {
      return myInstanceNodes.size();
    }

    @NotNull
    @Override
    public List<MemoryNode> getSubList(long startTimeUs, long endTimeUs) {
      return myInstanceNodes;
    }
  }

  private static class InstanceNode implements MemoryNode {
    @NotNull private final AllocationEvent myEvent;
    @NotNull private final AllocationStack myCallStack;

    public InstanceNode(@NotNull AllocationEvent event, @NotNull AllocationStack callStack) {
      myEvent = event;
      myCallStack = callStack;
    }

    @Override
    public int getShallowSize() {
      return myEvent.getSize();
    }

    @NotNull
    @Override
    public List<Capability> getCapabilities() {
      return Arrays.asList(Capability.LABEL, Capability.ELEMENT_SIZE, Capability.SHALLOW_SIZE);
    }
  }
}
