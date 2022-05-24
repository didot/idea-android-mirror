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
package com.android.tools.idea.gradle.dsl.api;

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.PsiElementHolder;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public interface PluginModel extends PsiElementHolder {
  @NotNull
  static List<String> extractNames(@NotNull List<PluginModel> plugins) {
    return ContainerUtil.map(plugins, plugin -> plugin.name().forceString());
  }

  @NotNull
  ResolvedPropertyModel name();

  @NotNull
  ResolvedPropertyModel version();

  @NotNull
  ResolvedPropertyModel apply();

  void remove();
}
