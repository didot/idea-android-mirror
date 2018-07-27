/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu.simpleperf

import com.android.testutils.TestUtils
import com.android.tools.profiler.protobuf3jarjar.ByteString
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Paths


class SimpleperfSampleReporterTest {

  @get:Rule
  val expected = ExpectedException.none()!!

  private lateinit var sampleReporter : SimpleperfSampleReporter

  @Before
  fun setUp() {
    val ideaHome = Paths.get(TestUtils.getWorkspaceRoot().absolutePath, "tools", "idea")
    sampleReporter = SimpleperfSampleReporter(ideaHome.toString())
  }

  @Test
  fun simpleperfBinaryShouldExist() {
    assertThat(File(sampleReporter.simpleperfBinaryPath).exists()).isTrue()
  }

  @Test
  fun preProcessingInvalidTraceReturnsFailure() {
    val processedTrace = sampleReporter.preProcessTrace(ByteString.copyFromUtf8("bad trace"))
    assertThat(processedTrace).isEqualTo(SimpleperfSampleReporter.FAILURE)

    val trace = FileUtil.createTempFile("cpu_trace", ".trace", true)
    FileOutputStream(trace).use { out -> out.write(processedTrace.toByteArray()) }

    // Trace can't be parsed by SimpleperfTraceParser. Expect an exception
    expected.expect(Exception::class.java)
    SimpleperfTraceParser().parse(trace, 0)
  }

  @Test
  fun preProcessingRawTraceReturnsValidTrace() {
    val processedTrace = sampleReporter.preProcessTrace(CpuProfilerTestUtils.traceFileToByteString("simpleperf_raw_trace.trace"))
    assertThat(processedTrace).isNotEqualTo(SimpleperfSampleReporter.FAILURE)

    val trace = FileUtil.createTempFile("cpu_trace", ".trace", true)
    FileOutputStream(trace).use { out -> out.write(processedTrace.toByteArray()) }

    // Trace can be parsed by SimpleperfTraceParser
    val parsedTrace = SimpleperfTraceParser().parse(trace, 0)
    assertThat(parsedTrace).isNotNull()
  }
}