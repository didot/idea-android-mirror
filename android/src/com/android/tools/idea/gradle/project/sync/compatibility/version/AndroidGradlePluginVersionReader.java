/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.compatibility.version;

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.service.notification.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * Obtains the version of the Android Gradle plugin that a project is using.
 */
class AndroidGradlePluginVersionReader implements ComponentVersionReader {
  @Override
  public boolean appliesTo(@NotNull Module module) {
    return AndroidFacet.getInstance(module) != null;
  }

  @Override
  @Nullable
  public String getComponentVersion(@NotNull Module module) {
    AndroidPluginInfo pluginInfo = AndroidPluginInfo.find(module.getProject());
    if (pluginInfo != null) {
      GradleVersion pluginVersion = pluginInfo.getPluginVersion();
      return pluginVersion != null ? pluginVersion.toString() : null;
    }
    return null;
  }

  @Override
  @Nullable
  public PositionInFile getVersionSource(@NotNull Module module) {
    return null;
  }

  @Override
  @NotNull
  public List<NotificationHyperlink> getQuickFixes(@NotNull Module module,
                                                   @Nullable VersionRange expectedVersion,
                                                   @Nullable PositionInFile location) {
    AndroidPluginInfo pluginInfo = AndroidPluginInfo.find(module.getProject());
    if (pluginInfo != null) {
      String version = pluginInfo.getPluginGeneration().getRecommendedVersion();
      NotificationHyperlink quickFix = new FixAndroidGradlePluginVersionHyperlink(GradleVersion.parse(version), null);
      return singletonList(quickFix);
    }
    return emptyList();
  }

  @Override
  public boolean isProjectLevel() {
    return true;
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "Android Gradle plugin";
  }
}
