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

import com.android.tools.idea.gradle.dsl.api.android.packagingOptions.DexModel;
import com.android.tools.idea.gradle.dsl.api.android.packagingOptions.JniLibsModel;
import com.android.tools.idea.gradle.dsl.api.android.packagingOptions.ResourcesModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel;
import org.jetbrains.annotations.NotNull;

public interface PackagingOptionsModel extends GradleDslModel {
  @NotNull
  ResolvedPropertyModel doNotStrip();

  @NotNull
  ResolvedPropertyModel excludes();

  @NotNull
  ResolvedPropertyModel merges();

  @NotNull
  ResolvedPropertyModel pickFirsts();

  @NotNull
  DexModel dex();

  @NotNull
  JniLibsModel jniLibs();

  @NotNull
  ResourcesModel resources();
}
