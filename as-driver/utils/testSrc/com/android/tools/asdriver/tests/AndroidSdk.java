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
package com.android.tools.asdriver.tests;

import java.nio.file.Path;
import java.util.Map;

public class AndroidSdk {
  private final Path sourceDir;

  public AndroidSdk(Path sourceDir) {
    this.sourceDir = sourceDir;
  }

  public void install(Map<String, String> env) {
    env.put("ANDROID_HOME", sourceDir.toString());
  }

  public Path getSourceDir() {
    return sourceDir;
  }
}
