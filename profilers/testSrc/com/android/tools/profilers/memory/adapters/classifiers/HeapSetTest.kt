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
package com.android.tools.profilers.memory.adapters.classifiers

import com.android.tools.profilers.memory.adapters.FakeCaptureObject
import com.android.tools.profilers.memory.adapters.FakeInstanceObject
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HeapSetTest {
  @Test
  fun `removing instance shrinks heap size`() {
    val capture = FakeCaptureObject.Builder().build()
    val inst1 = FakeInstanceObject.Builder(capture, 1, "obj").setShallowSize(4).build()
    val inst2 = FakeInstanceObject.Builder(capture, 1, "obj").setShallowSize(8).build()
    val h = HeapSet(capture, "Fake", 42)
    h.addDeltaInstanceObject(inst1)
    h.addDeltaInstanceObject(inst2)
    h.removeAddedDeltaInstanceObject(inst1)
    assertThat(h.totalRemainingSize).isEqualTo(inst2.shallowSize)
  }

  @Test
  fun `heap set makes use of classes' retained sizes if present`() {
    val capture = FakeCaptureObject.Builder().build()
    val cl = capture.registerClass(1, 0, "obj", 8)
    val inst1 = FakeInstanceObject.Builder(cl).setRetainedSize(8).build()
    val inst2 = FakeInstanceObject.Builder(cl).setRetainedSize(4).build()
    val h = HeapSet(capture, "Fake", 0)
    h.addDeltaInstanceObject(inst1)
    h.addDeltaInstanceObject(inst2)
    assertThat(h.totalRetainedSize).isEqualTo(8)
    assertThat(h.childrenClassifierSets[0].totalRetainedSize).isEqualTo(8)
  }
}