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
package com.android.tools.idea.gradle.dsl.api.android;

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.GradleBlockModel;
import org.jetbrains.annotations.NotNull;

public interface LintOptionsModel extends GradleBlockModel {
  @NotNull
  ResolvedPropertyModel abortOnError();

  @NotNull
  ResolvedPropertyModel absolutePaths();

  @NotNull
  ResolvedPropertyModel baseline();

  @NotNull
  ResolvedPropertyModel check();

  @NotNull
  ResolvedPropertyModel checkAllWarnings();

  @NotNull
  ResolvedPropertyModel checkDependencies();

  @NotNull
  ResolvedPropertyModel checkGeneratedSources();

  @NotNull
  ResolvedPropertyModel checkReleaseBuilds();

  @NotNull
  ResolvedPropertyModel checkTestSources();

  @NotNull
  ResolvedPropertyModel disable();

  @NotNull
  ResolvedPropertyModel enable();

  @NotNull
  ResolvedPropertyModel error();

  @NotNull
  ResolvedPropertyModel explainIssues();

  @NotNull
  ResolvedPropertyModel fatal();

  @NotNull
  ResolvedPropertyModel htmlOutput();

  @NotNull
  ResolvedPropertyModel htmlReport();

  @NotNull
  ResolvedPropertyModel ignore();

  @NotNull
  ResolvedPropertyModel ignoreTestSources();

  @NotNull
  ResolvedPropertyModel ignoreWarnings();

  @NotNull
  ResolvedPropertyModel informational();

  @NotNull
  ResolvedPropertyModel lintConfig();

  @NotNull
  ResolvedPropertyModel noLines();

  @NotNull
  ResolvedPropertyModel quiet();

  @NotNull
  ResolvedPropertyModel sarifOutput();

  @NotNull
  ResolvedPropertyModel sarifReport();

  @NotNull
  ResolvedPropertyModel showAll();

  @NotNull
  ResolvedPropertyModel textOutput();

  @NotNull
  ResolvedPropertyModel textReport();

  @NotNull
  ResolvedPropertyModel warning();

  @NotNull
  ResolvedPropertyModel warningsAsErrors();

  @NotNull
  ResolvedPropertyModel xmlOutput();

  @NotNull
  ResolvedPropertyModel xmlReport();
}
