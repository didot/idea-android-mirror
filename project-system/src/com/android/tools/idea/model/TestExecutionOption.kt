/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.model

enum class TestExecutionOption {
    /** On device orchestration is not used in this case.  */
    HOST,

    /** On device orchestration is used.  */
    ANDROID_TEST_ORCHESTRATOR,

    /** On device orchestration is used, with androidx class names.  */
    ANDROIDX_TEST_ORCHESTRATOR
}

data class TestOptions(
  val executionOption: TestExecutionOption?,
  val animationsDisabled: Boolean,
  val instrumentationRunner: String?,
  val instrumentationRunnerArguments: Map<String, String>
) {
  companion object {
    @JvmField
    val DEFAULT: TestOptions = TestOptions(
      executionOption = null,
      animationsDisabled = false,
      instrumentationRunner = null,
      instrumentationRunnerArguments = emptyMap()
    )
  }
}