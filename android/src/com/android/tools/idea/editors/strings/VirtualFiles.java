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
package com.android.tools.idea.editors.strings;

import com.google.common.base.Strings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public final class VirtualFiles {
  private VirtualFiles() {
  }

  /**
   * Returns a short string representation of a virtual file's path
   */
  @NotNull
  public static String toString(@NotNull VirtualFile file, @NotNull Project project) {
    return Strings.nullToEmpty(FileUtil.getRelativePath(project.getBasePath(), file.getPath(), '/'));
  }
}
