/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics;

import com.android.tools.idea.diagnostics.jfr.RecordingManager;
import com.android.tools.idea.diagnostics.jfr.analysis.JfrAnalyzer;
import com.intellij.openapi.application.PathManager;
import java.nio.file.Path;
import java.util.function.BiConsumer;

public class JfrReportContributor implements DiagnosticReportContributor {
  private Path jfrPath;

  @Override
  public void setup(DiagnosticReportConfiguration configuration) {
  }

  @Override
  public void startCollection(long timeElapsedSoFarMs) {
  }

  @Override
  public void stopCollection(long totalDurationMs) {
    jfrPath = RecordingManager.dumpJfrTo(Path.of(PathManager.getTempPath()));
  }

  @Override
  public String getReport() {
    if (jfrPath != null) {
      return new JfrAnalyzer().analyze(jfrPath);
    }
    return "";
  }

  @Override
  public void generateReport(BiConsumer<String, String> saveReportCallback) {
    saveReportCallback.accept("jfrReport", getReport());
  }
}
