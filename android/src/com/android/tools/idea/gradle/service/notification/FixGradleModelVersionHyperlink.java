/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification;

import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.gradle.parser.BuildFileKey.PLUGIN_VERSION;
import static com.android.tools.idea.gradle.service.notification.FixGradleVersionInWrapperHyperlink.updateGradleVersion;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_LATEST_VERSION;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_PLUGIN_LATEST_VERSION;

class FixGradleModelVersionHyperlink extends NotificationHyperlink {
  FixGradleModelVersionHyperlink() {
    super("fixGradleElements", "Fix plug-in version and re-import project");
  }

  @Override
  protected void execute(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    boolean atLeastOnUpdated = false;
    for (Module module : moduleManager.getModules()) {
      VirtualFile file = GradleUtil.getGradleBuildFile(module);
      if (file != null) {
        final GradleBuildFile buildFile = new GradleBuildFile(file, project);
        Object pluginVersion = buildFile.getValue(PLUGIN_VERSION);
        if (pluginVersion != null) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              buildFile.setValue(PLUGIN_VERSION, GRADLE_PLUGIN_LATEST_VERSION);
            }
          });
          atLeastOnUpdated = true;
        }
      }
    }
    if (!atLeastOnUpdated) {
      NotificationListener notificationListener =
        new CustomNotificationListener(project, new SearchInBuildFilesHyperlink("com.android.tools.build:gradle"));
      AndroidGradleNotification notification = AndroidGradleNotification.getInstance(project);
      String msg = "Unable to find any references to the Android Gradle plug-in in build.gradle files.\n\n" +
                   "Please click the link to perform a textual search and then update the build files manually.";
      notification.showBalloon(ERROR_MSG_TITLE, msg, NotificationType.ERROR, notificationListener);
      return;
    }
    File wrapperPropertiesFile = GradleUtil.findWrapperPropertiesFile(project);
    if (wrapperPropertiesFile != null && !updateGradleVersion(project, wrapperPropertiesFile, GRADLE_LATEST_VERSION)) {
      return;
    }
    try {
      GradleProjectImporter.getInstance().reImportProject(project, null);
    }
    catch (ConfigurationException e) {
      Messages.showErrorDialog(e.getMessage(), e.getTitle());
      Logger.getInstance(FixGradleVersionInWrapperHyperlink.class).info(e);
    }
  }
}
