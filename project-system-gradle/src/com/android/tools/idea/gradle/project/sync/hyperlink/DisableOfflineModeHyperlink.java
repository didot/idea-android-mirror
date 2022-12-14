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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_OFFLINE_MODE_DISABLED;

import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

public class DisableOfflineModeHyperlink extends SyncIssueNotificationHyperlink {
  public DisableOfflineModeHyperlink() {
    super("disable.gradle.offline.mode",
          "Disable offline mode and sync project",
          AndroidStudioEvent.GradleSyncQuickFix.DISABLE_OFFLINE_MODE_HYPERLINK);
  }

  @Override
  protected void execute(@NotNull Project project) {
    GradleSettings.getInstance(project).setOfflineWork(false);
    HyperlinkUtil.requestProjectSync(project, TRIGGER_QF_OFFLINE_MODE_DISABLED);
  }
}
