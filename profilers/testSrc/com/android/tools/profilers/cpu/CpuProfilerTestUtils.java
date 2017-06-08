/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.testutils.TestUtils;
import com.google.protobuf3jarjar.ByteString;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Common constants and methods used across CPU profiler tests.
 * Should not be instantiated.
 */
public class CpuProfilerTestUtils {

  private static final String CPU_TRACES_DIR = "tools/adt/idea/profilers/testData/cputraces/";

  private CpuProfilerTestUtils() {}

  public static ByteString traceFileToByteString(@NotNull String filename) throws IOException {
    return ByteString.copyFrom(Files.readAllBytes(getTraceFile(filename).toPath()));
  }

  public static File getTraceFile(@NotNull String filename) {
    File traceFile = TestUtils.getWorkspaceFile(CPU_TRACES_DIR + filename);
    return traceFile;
  }
}
