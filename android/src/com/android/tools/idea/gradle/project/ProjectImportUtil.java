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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.eclipse.GradleImport;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * Project import logic used by UI elements
 */
public final class ProjectImportUtil {
  @NonNls private static final String ANDROID_NATURE_NAME = "com.android.ide.eclipse.adt.AndroidNature";

  private static final Logger LOG = Logger.getInstance(ProjectImportUtil.class);

  private static final String[] GRADLE_SUPPORTED_FILES =
    new String[]{SdkConstants.FN_BUILD_GRADLE, SdkConstants.FN_BUILD_GRADLE_KTS, SdkConstants.FN_SETTINGS_GRADLE,
      SdkConstants.FN_SETTINGS_GRADLE_KTS};

  private ProjectImportUtil() {
    // Do not instantiate
  }

  @NotNull
  public static VirtualFile findImportTarget(@NotNull VirtualFile file) {
    VirtualFile gradleTarget = findGradleTarget(file);
    if (gradleTarget != null) {
      return gradleTarget;
    }

    VirtualFile eclipseTarget = findEclipseTarget(file);
    if (eclipseTarget != null) {
      return eclipseTarget;
    }

    return file;
  }

  @Nullable
  private static VirtualFile findEclipseTarget(@NotNull VirtualFile file) {
    VirtualFile target;
    VirtualFile result = null;
    if (file.isDirectory()) {
      target = findMatch(file, GradleImport.ECLIPSE_DOT_PROJECT);
      if (target != null) {
        result = findImportTarget(target);
      }
    }
    else {
      if (GradleImport.ECLIPSE_DOT_PROJECT.equals(file.getName()) && hasAndroidNature(file)) {
        result = file;
      } else if (GradleImport.ECLIPSE_DOT_CLASSPATH.equals(file.getName())) {
        result = findImportTarget(file.getParent());
      }
    }
    return result;
  }

  private static VirtualFile findGradleTarget(@NotNull VirtualFile file) {
    return findMatch(file, GRADLE_SUPPORTED_FILES);
  }

  @Nullable
  private static VirtualFile findMatch(@NotNull VirtualFile location, @NotNull String... validNames) {
    if (location.isDirectory()) {
      for (VirtualFile child : location.getChildren()) {
        for (String name : validNames) {
          if (name.equals(child.getName())) {
            return child;
          }
        }
      }
    }
    else {
      for (String name : validNames) {
        if (name.equals(location.getName())) {
          return location;
        }
      }
    }
    return null;
  }

  private static boolean hasAndroidNature(@NotNull VirtualFile projectFile) {
    File dotProjectFile = new File(projectFile.getPath());
    try {
      Element naturesElement = JDOMUtil.load(dotProjectFile).getChild("natures");
      if (naturesElement != null) {
        List<Element> naturesList = naturesElement.getChildren("nature");
        for (Element nature : naturesList) {
          String natureName = nature.getText();
          if (ANDROID_NATURE_NAME.equals(natureName)) {
            return true;
          }
        }
      }
    }
    catch (Exception e) {
      LOG.info(String.format("Unable to get natures for Eclipse project file '%1$s", projectFile.getPath()), e);
    }
    return false;
  }
}
