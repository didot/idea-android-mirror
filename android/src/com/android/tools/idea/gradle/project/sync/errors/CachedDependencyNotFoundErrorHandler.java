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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.ToggleOfflineModeHyperlink;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * Tests for {@link CachedDependencyNotFoundErrorHandler}.
 */
public class CachedDependencyNotFoundErrorHandler extends SyncErrorHandler {
  @Override
  @Nullable
  protected String findErrorMessage(@NotNull Throwable error, @NotNull NotificationData notification, @NotNull Project project) {
    Throwable cause = getRootCause(error);
    String text = cause.getMessage();
    if (isNotEmpty(text)) {
      String firstLine = getFirstLineMessage(text);
      if (firstLine.startsWith("No cached version of ") && firstLine.contains("available for offline mode.")) {
        updateUsageTracker();
        return firstLine;
      }
    }
    return null;
  }

  @Override
  @NotNull
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull NotificationData notification,
                                                              @NotNull Project project,
                                                              @NotNull String text) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    NotificationHyperlink disableOfflineMode = ToggleOfflineModeHyperlink.disableOfflineMode(project);
    if (disableOfflineMode != null) {
      hyperlinks.add(disableOfflineMode);
    }
    return hyperlinks;
  }
}