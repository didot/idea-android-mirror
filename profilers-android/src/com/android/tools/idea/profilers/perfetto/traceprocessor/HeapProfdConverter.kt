/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.profilers.perfetto.traceprocessor

import com.android.tools.profiler.perfetto.proto.Memory.StackFrame
import com.android.tools.profiler.perfetto.proto.Memory.NativeAllocationContext
import com.android.tools.profiler.proto.Memory
import com.android.tools.profilers.memory.adapters.ClassDb
import com.android.tools.profilers.memory.adapters.NativeAllocationInstanceObject
import com.android.tools.profilers.memory.adapters.classifiers.NativeMemoryHeapSet
import com.android.tools.profilers.stacktrace.NativeFrameSymbolizer
import java.io.File
import java.util.HashMap

/**
 * Helper class to convert from perfetto memory proto to profiler protos.
 * The {@link NativeMemoryHeapSet} passed into the constructor is populated by calling {@link populateHeapSet}.
 */
class HeapProfdConverter(private val abi: String,
                         private val symbolizer: NativeFrameSymbolizer,
                         private val memorySet: NativeMemoryHeapSet) {

  /**
   * Given a {@link Memory.StackFrame} from the trace processor we attempt to gather symbolized data. If we cannot get symbolized data
   * we return the original name if one was found. If no name was found then we return "unknown"
   * When we have a symbolized frame we return the results in the form of
   * Symbol (File:Line) eg.. operator new (new.cpp:256)
   */
  private fun toBestAvailableName(rawFrame: StackFrame): String {
    val symbolizedFrame = symbolizer.symbolize(abi, Memory.NativeCallStack.NativeFrame.newBuilder()
      .setModuleName(rawFrame.module)
      // +1 because the common symbolizer does -1 accounting for an offset heapprofd does not have.
      // see IntellijNativeFrameSymbolizer:getOffsetOfPreviousInstruction
      .setModuleOffset(rawFrame.relPc + 1)
      .build())
    if (symbolizedFrame.symbolName.startsWith("0x")) {
      // Raw name is better than PC offset.
      return rawFrame.name.ifBlank { "unknown" }
    }
    val file = File(symbolizedFrame.fileName).name
    return "${symbolizedFrame.symbolName} (${file}:${symbolizedFrame.lineNumber})"
  }

  /**
   * Given a context all values will be enumerated and added to the {@link NativeMemoryHeapSet}. If the context has an allocation with
   * a count > 0 it will be added as an allocation. If the count is <= 0 it will be added as a free.
   */
  fun populateHeapSet(context: NativeAllocationContext) {
    val frameIdToName: MutableMap<Long, String> = HashMap()
    val stackPointerIdToParentId: MutableMap<Long, Long> = HashMap()
    val stackPointerIdToFrameName: MutableMap<Long, String> = HashMap()
    val callSites: Map<Long, NativeAllocationInstanceObject?> = HashMap()
    val classDb = ClassDb()
    context.framesList.forEach {
      frameIdToName[it.id] = toBestAvailableName(it)
    }
    context.pointersList.forEach {
      stackPointerIdToFrameName[it.id] = frameIdToName[it.frameId] ?: "unknown"
      stackPointerIdToParentId[it.id] = it.parentId
    }
    context.allocationsList.forEach {
      val fullStack = Memory.AllocationStack.StackFrameWrapper.newBuilder()
      var callSiteId = stackPointerIdToParentId[it.stackId]
      if (!callSites.containsKey(it.stackId)) {
        while (callSiteId != null && callSiteId != 0L) {
          val name = stackPointerIdToFrameName[callSiteId] ?: "unknown"
          fullStack.addFrames(Memory.AllocationStack.StackFrame.newBuilder().setMethodName(name).build())
          callSiteId = stackPointerIdToParentId[callSiteId]
        }
        val event = Memory.AllocationEvent.Allocation.newBuilder()
          .setSize(Math.abs(it.size))
          .build()
        val name = stackPointerIdToFrameName[it.stackId] ?: "unknown"
        val stack = Memory.AllocationStack.newBuilder()
          .setFullStack(fullStack)
          .build()
        val instanceObject = NativeAllocationInstanceObject(
          event, classDb.registerClass(0, 0, name), stack, it.count)
        if (it.count > 0) {
          memorySet.addDeltaInstanceObject(instanceObject)
        }
        else {
          memorySet.freeDeltaInstanceObject(instanceObject)
        }
      }
    }
  }
}