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
package com.android.tools.idea.gradle.dsl.model.crashlytics;

import com.android.tools.idea.gradle.dsl.api.crashlytics.CrashlyticsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.crashlytics.CrashlyticsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class CrashlyticsModelImpl extends GradleDslBlockModel implements CrashlyticsModel {
  @NonNls public static final String ENABLE_NDK = "mEnableNdk";

  @Override
  @NotNull
  public ResolvedPropertyModel enableNdk() {
    return getModelForProperty(ENABLE_NDK);
  }

  public CrashlyticsModelImpl(@NotNull CrashlyticsDslElement element) {
    super(element);
  }
}
