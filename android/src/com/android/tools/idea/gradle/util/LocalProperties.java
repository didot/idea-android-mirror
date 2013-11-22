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
import com.google.common.base.Joiner;
import com.google.common.io.Closeables;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Utility methods related to a Gradle project's local.properties file.
 */
public final class LocalProperties {
  private static final String HEADER_COMMENT = getHeaderComment();

  @NotNull private final File myFilePath;
  @NotNull private final Properties myProperties;

  @NotNull
  private static String getHeaderComment() {
    String[] lines = {
      "# This file is automatically generated by Android Studio.",
      "# Do not modify this file -- YOUR CHANGES WILL BE ERASED!",
      "#",
      "# This file must *NOT* be checked into Version Control Systems,",
      "# as it contains information specific to your local configuration.",
      "",
      "# Location of the SDK. This is only used by Gradle.",
      "# For customization when using a Version Control System, please read the",
      "# header note."
    };
    return Joiner.on(SystemProperties.getLineSeparator()).join(lines);
  }

  /**
   * Creates a new {@link LocalProperties}. This constructor creates a new file at the given path if a local.properties file does not exist.
   *
   * @param project the Android project.
   * @throws IOException if an I/O error occurs while reading the file.
   * @throws IllegalArgumentException if there is already a directory called "local.properties" in the given project.
   */
  public LocalProperties(@NotNull Project project) throws IOException {
    this(new File(project.getBasePath()));
  }

  /**
   * Creates a new {@link LocalProperties}. This constructor creates a new file at the given path if a local.properties file does not exist.
   *
   * @param projectDirPath the path of the Android project's root directory.
   * @throws IOException if an I/O error occurs while reading the file.
   * @throws IllegalArgumentException if there is already a directory called "local.properties" at the given path.
   */
  public LocalProperties(@NotNull File projectDirPath) throws IOException {
    myFilePath = new File(projectDirPath, SdkConstants.FN_LOCAL_PROPERTIES);
    myProperties = readFile(myFilePath);
  }

  @NotNull
  private static Properties readFile(@NotNull File filePath) throws IOException {
    if (filePath.isDirectory()) {
      // There is a directory named "local.properties". Unlikely to happen, but worth checking.
      throw new IllegalArgumentException(String.format("The path '%1$s' belongs to a directory!", filePath.getPath()));
    }
    if (!filePath.exists()) {
      return new Properties();
    }
    Properties properties = new Properties();
    FileInputStream fileInputStream = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      fileInputStream = new FileInputStream(filePath);
      properties.load(fileInputStream);
    } finally {
      Closeables.closeQuietly(fileInputStream);
    }
    return properties;
  }

  /**
   * @return the path of the Android SDK specified in this local.properties file; or {@code null} if such property is not specified.
   */
  @Nullable
  public String getAndroidSdkPath() {
    String path = myProperties.getProperty(SdkConstants.SDK_DIR_PROPERTY);
    if (path != null) {
      path = FileUtil.toSystemDependentName(path);
    }
    return path;
  }

  public void setAndroidSdkPath(@NotNull Sdk androidSdk) {
    String androidSdkPath = androidSdk.getHomePath();
    assert androidSdkPath != null;
    setAndroidSdkPath(androidSdkPath);
  }

  public void setAndroidSdkPath(@NotNull String androidSdkPath) {
    String path = FileUtil.toSystemIndependentName(androidSdkPath);
    myProperties.setProperty(SdkConstants.SDK_DIR_PROPERTY, path);
  }

  public void save() throws IOException {
    FileUtilRt.createParentDirs(myFilePath);
    FileOutputStream out = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      out = new FileOutputStream(myFilePath);
      myProperties.store(out, HEADER_COMMENT);
    } finally {
      Closeables.closeQuietly(out);
    }
  }

  @NotNull
  public File getFilePath() {
    return myFilePath;
  }
}
