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
import com.android.tools.idea.gradle.project.ChooseGradleHomeDialog;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.KeyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.net.HttpConfigurable;
import org.gradle.StartParameter;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.wrapper.PathAssembler;
import org.gradle.wrapper.WrapperConfiguration;
import org.gradle.wrapper.WrapperExecutor;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_URL_PROPERTY;
import static org.jetbrains.plugins.gradle.util.GradleUtil.getLastUsedGradleHome;

/**
 * Utilities related to Gradle.
 */
public final class GradleUtil {
  @NonNls public static final String GRADLE_MINIMUM_VERSION = SdkConstants.GRADLE_MINIMUM_VERSION;
  @NonNls public static final String GRADLE_LATEST_VERSION = SdkConstants.GRADLE_LATEST_VERSION;

  @NonNls public static final String GRADLE_PLUGIN_MINIMUM_VERSION = SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION;
  @NonNls public static final String GRADLE_PLUGIN_LATEST_VERSION = SdkConstants.GRADLE_PLUGIN_LATEST_VERSION;

  @NonNls private static final String GRADLEW_PROPERTIES_PATH = FileUtil.join("gradle", "wrapper", "gradle-wrapper.properties");

  private static final Logger LOG = Logger.getInstance(GradleUtil.class);
  private static final ProjectSystemId SYSTEM_ID = GradleConstants.SYSTEM_ID;

  private static final String GRADLE_EXECUTABLE_NAME = SystemInfo.isWindows ? "gradle.bat" : "gradle";

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
    if (gradleFacet != null && gradleFacet.getGradleProject() != null) {
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
  public static File getGradleSettingsFilePath(@NotNull File rootDir) {
    return new File(rootDir, SdkConstants.FN_SETTINGS_GRADLE);
  }

  @NotNull
  public static File getGradleWrapperPropertiesFilePath(@NotNull File projectRootDir) {
    return new File(projectRootDir, GRADLEW_PROPERTIES_PATH);
  }

  /**
   * Updates the 'distributionUrl' in the given Gradle wrapper properties file.
   *
   * @param gradleVersion  the Gradle version to update the property to.
   * @param propertiesFile the given Gradle wrapper properties file.
   * @return {@code true} if the property was updated, or {@code false} if no update was necessary because the property already had the
   *         correct value.
   * @throws IOException if something goes wrong when saving the file.
   */
  public static boolean updateGradleDistributionUrl(@NotNull String gradleVersion, @NotNull File propertiesFile) throws IOException {
    Properties properties = loadGradleWrapperProperties(propertiesFile);
    String gradleDistributionUrl = getGradleDistributionUrl(gradleVersion, false);
    String property = properties.getProperty(DISTRIBUTION_URL_PROPERTY);
    if (property != null && (property.equals(gradleDistributionUrl) || property.equals(getGradleDistributionUrl(gradleVersion, true)))) {
      return false;
    }
    properties.setProperty(DISTRIBUTION_URL_PROPERTY, gradleDistributionUrl);
    FileOutputStream out = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      out = new FileOutputStream(propertiesFile);
      properties.store(out, null);
      return true;
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
  private static String getGradleDistributionUrl(@NotNull String gradleVersion, boolean binOnly) {
    String suffix = binOnly ? "bin" : "all";
    return String.format("http://services.gradle.org/distributions/gradle-%1$s-" + suffix + ".zip", gradleVersion);
  }

  @Nullable
  public static GradleExecutionSettings getGradleExecutionSettings(@NotNull Project project) {
    GradleProjectSettings projectSettings = getGradleProjectSettings(project);
    if (projectSettings == null) {
      String format = "Unable to obtain Gradle project settings for project '%1$s', located at '%2$s'";
      String msg = String.format(format, project.getName(), FileUtil.toSystemDependentName(project.getBasePath()));
      LOG.info(msg);
      return null;
    }
    return ExternalSystemApiUtil.getExecutionSettings(project, projectSettings.getExternalProjectPath(), SYSTEM_ID);
  }

  @Nullable
  public static File findWrapperPropertiesFile(@NotNull Project project) {
    File baseDir = new File(project.getBasePath());
    File wrapperPropertiesFile = getGradleWrapperPropertiesFilePath(baseDir);
    return wrapperPropertiesFile.isFile() ? wrapperPropertiesFile : null;
  }

  @Nullable
  public static GradleProjectSettings getGradleProjectSettings(@NotNull Project project) {
    GradleSettings settings = (GradleSettings)ExternalSystemApiUtil.getSettings(project, SYSTEM_ID);

    GradleSettings.MyState state = settings.getState();
    assert state != null;
    Set<GradleProjectSettings> allProjectsSettings = state.getLinkedExternalProjectsSettings();

    return getFirstNotNull(allProjectsSettings);
  }

  @Nullable
  private static GradleProjectSettings getFirstNotNull(@Nullable Set<GradleProjectSettings> allProjectSettings) {
    if (allProjectSettings != null) {
      for (GradleProjectSettings settings : allProjectSettings) {
        if (settings != null) {
          return settings;
        }
      }
    }
    return null;
  }

  @NotNull
  public static List<String> getGradleInvocationJvmArgs(@NotNull File projectDir) {
    if (ExternalSystemApiUtil.isInProcessMode(SYSTEM_ID)) {
      List<String> args = Lists.newArrayList();
      if (!AndroidGradleSettings.isAndroidSdkDirInLocalPropertiesFile(projectDir)) {
        AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
        if (sdkData != null) {
          String arg = AndroidGradleSettings.createAndroidHomeJvmArg(sdkData.getPath());
          args.add(arg);
        }
      }
      List<KeyValue<String, String>> proxyProperties = HttpConfigurable.getJvmPropertiesList(false, null);
      for (KeyValue<String, String> proxyProperty : proxyProperties) {
        String arg = AndroidGradleSettings.createJvmArg(proxyProperty.getKey(), proxyProperty.getValue());
        args.add(arg);
      }
      return args;
    }
    return Collections.emptyList();
  }

  public static void stopAllGradleDaemons(boolean interactive) throws IOException {
    File gradleHome = findAnyGradleHome(interactive);
    if (gradleHome == null) {
      throw new FileNotFoundException("Unable to find path to Gradle home directory");
    }
    File gradleExecutable = new File(gradleHome, "bin" + File.separatorChar + GRADLE_EXECUTABLE_NAME);
    if (!gradleExecutable.isFile()) {
      throw new FileNotFoundException("Unable to find Gradle executable: " + gradleExecutable.getPath());
    }
    new ProcessBuilder(gradleExecutable.getPath(), "--stop").start();
  }

  @Nullable
  public static File findAnyGradleHome(boolean interactive) {
    // Try cheapest option first:
    String lastUsedGradleHome = getLastUsedGradleHome();
    if (!lastUsedGradleHome.isEmpty()) {
      File path = new File(lastUsedGradleHome);
      if (isValidGradleHome(path)) {
        return path;
      }
    }

    ProjectManager projectManager = ProjectManager.getInstance();
    for (Project project : projectManager.getOpenProjects()) {
      File gradleHome = findGradleHome(project);
      if (gradleHome != null) {
        return gradleHome;
      }
    }

    if (interactive) {
      ChooseGradleHomeDialog chooseGradleHomeDialog = new ChooseGradleHomeDialog();
      chooseGradleHomeDialog.setTitle("Choose Gradle Installation");
      String description = "A Gradle installation is necessary to stop all daemons.\n" +
                           "Please select the home directory of a Gradle installation, otherwise the project won't be closed.";
      chooseGradleHomeDialog.setDescription(description);
      if (!chooseGradleHomeDialog.showAndGet()) {
        return null;
      }
      String enteredPath = chooseGradleHomeDialog.getEnteredGradleHomePath();
      File gradleHomePath = new File(enteredPath);
      if (isValidGradleHome(gradleHomePath)) {
        chooseGradleHomeDialog.storeLastUsedGradleHome();
        return gradleHomePath;
      }
    }

    return null;
  }

  @Nullable
  private static File findGradleHome(@NotNull Project project) {
    GradleExecutionSettings settings = getGradleExecutionSettings(project);
    if (settings != null) {
      String gradleHome = settings.getGradleHome();
      if (!Strings.isNullOrEmpty(gradleHome)) {
        File path = new File(gradleHome);
        if (isValidGradleHome(path)) {
          return path;
        }
      }
    }
    else {
      File wrapperPropertiesFile = findWrapperPropertiesFile(project);
      if (wrapperPropertiesFile != null) {
        WrapperExecutor wrapperExecutor = WrapperExecutor.forWrapperPropertiesFile(wrapperPropertiesFile, new StringBuilder());
        WrapperConfiguration configuration = wrapperExecutor.getConfiguration();
        File gradleHome = getGradleHome(project, configuration);
        if (gradleHome != null) {
          return gradleHome;
        }
      }
    }
    return null;
  }

  @Nullable
  private static File getGradleHome(@NotNull Project project, @NotNull WrapperConfiguration configuration) {
    File systemHomePath = StartParameter.DEFAULT_GRADLE_USER_HOME;
    if (Wrapper.PathBase.PROJECT.name().equals(configuration.getDistributionBase())) {
      systemHomePath = new File(project.getBasePath(), SdkConstants.DOT_GRADLE);
    }
    if (!systemHomePath.isDirectory()) {
      return null;
    }
    PathAssembler.LocalDistribution localDistribution = new PathAssembler(systemHomePath).getDistribution(configuration);
    File distributionPath = localDistribution.getDistributionDir();
    if (distributionPath != null) {
      File[] children = FileUtil.notNullize(distributionPath.listFiles());
      for (File child : children) {
        if (child.isDirectory() && child.getName().startsWith("gradle-") && isValidGradleHome(child)) {
          return child;
        }
      }
    }
    return null;
  }

  private static boolean isValidGradleHome(@NotNull File path) {
    return path.isDirectory() && ServiceManager.getService(GradleInstallationManager.class).isGradleSdkHome(path);
  }
}
