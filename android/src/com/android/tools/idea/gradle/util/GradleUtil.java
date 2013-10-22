/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * Utilities related to Gradle.
 */
public final class GradleUtil {
  @NonNls public static final String GRADLE_MINIMUM_VERSION = "1.8";
  @NonNls public static final String GRADLE_LATEST_VERSION = GRADLE_MINIMUM_VERSION;

  @NonNls public static final String GRADLE_PLUGIN_MINIMUM_VERSION = "0.6.1";
  @NonNls public static final String GRADLE_PLUGIN_LATEST_VERSION = "0.6.+";

  @NonNls private static final String GRADLEW_PROPERTIES_PATH =
    "gradle" + File.separator + "wrapper" + File.separator + "gradle-wrapper.properties";
  @NonNls private static final String GRADLEW_DISTRIBUTION_URL_PROPERTY_NAME = "distributionUrl";

  private GradleUtil() {
  }

  @Nullable
  public static Module findModuleByGradlePath(@NotNull Project project, @NotNull String gradlePath) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
      if (gradleFacet != null) {
        if (gradlePath.equals(gradleFacet.getConfiguration().GRADLE_PROJECT_PATH)) {
          return module;
        }
      }
    }
    return null;
  }

  @NotNull
  public static List<String> getPathSegments(@NotNull String gradlePath) {
    return Lists.newArrayList(Splitter.on(SdkConstants.GRADLE_PATH_SEPARATOR).omitEmptyStrings().split(gradlePath));
  }

  @Nullable
  public static VirtualFile getGradleBuildFile(@NotNull Module module) {
    AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
    if (gradleFacet != null) {
      return gradleFacet.getGradleProject().getBuildFile();
    }
    // At the time we're called, module.getModuleFile() may be null, but getModuleFilePath returns the path where it will be created.
    File moduleFilePath = new File(module.getModuleFilePath());
    return getGradleBuildFile(moduleFilePath.getParentFile());
  }

  @Nullable
  public static VirtualFile getGradleBuildFile(@NotNull File rootDir) {
    File gradleBuildFilePath = getGradleBuildFilePath(rootDir);
    return VfsUtil.findFileByIoFile(gradleBuildFilePath, true);
  }

  @NotNull
  public static File getGradleBuildFilePath(@NotNull File rootDir) {
    return new File(rootDir, SdkConstants.FN_BUILD_GRADLE);
  }

  @NotNull
  public static File getGradleWrapperPropertiesFilePath(@NotNull File projectRootDir) {
    return new File(projectRootDir, GRADLEW_PROPERTIES_PATH);
  }

  public static void updateGradleDistributionUrl(@NotNull String gradleVersion, @NotNull File propertiesFile) throws IOException {
    Properties properties = loadGradleWrapperProperties(propertiesFile);
    String gradleDistributionUrl = getGradleDistributionUrl(gradleVersion);
    if (gradleDistributionUrl.equals(properties.getProperty(GRADLEW_DISTRIBUTION_URL_PROPERTY_NAME))) {
      return;
    }
    properties.setProperty(GRADLEW_DISTRIBUTION_URL_PROPERTY_NAME, gradleDistributionUrl);
    FileOutputStream out = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      out = new FileOutputStream(propertiesFile);
      properties.store(out, null);
    }
    finally {
      Closeables.closeQuietly(out);
    }
  }

  @NotNull
  private static Properties loadGradleWrapperProperties(@NotNull File propertiesFile) throws IOException {
    Properties properties = new Properties();
    FileInputStream fileInputStream = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      fileInputStream = new FileInputStream(propertiesFile);
      properties.load(fileInputStream);
      return properties;
    }
    finally {
      Closeables.closeQuietly(fileInputStream);
    }
  }

  @NotNull
  private static String getGradleDistributionUrl(@NotNull String gradleVersion) {
    return String.format("http://services.gradle.org/distributions/gradle-%1$s-bin.zip", gradleVersion);
  }
}
