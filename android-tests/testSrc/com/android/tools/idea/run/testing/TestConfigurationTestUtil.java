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
package com.android.tools.idea.run.testing;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.MapDataContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.vfs.VfsUtilCore.findRelativeFile;
import static org.junit.Assert.assertNotNull;

/**
 * Collection of test utils for testing {@link AndroidTestRunConfiguration}s, {@link JUnitConfiguration}s and their interactions
 */
public class TestConfigurationTestUtil {
  @Nullable
  public static JUnitConfiguration createJUnitConfigurationFromDirectory(Project project, @NotNull String directory) {
    RunConfiguration runConfiguration = createConfigurationFromDirectory(project, directory);
    return runConfiguration instanceof JUnitConfiguration ? (JUnitConfiguration)runConfiguration : null;
  }

  @Nullable
  public static AndroidTestRunConfiguration createAndroidConfigurationFromDirectory(Project project, @NotNull String directory) {
    RunConfiguration runConfiguration = createConfigurationFromDirectory(project, directory);
    return runConfiguration instanceof AndroidTestRunConfiguration ? (AndroidTestRunConfiguration)runConfiguration : null;
  }

  @Nullable
  private static RunConfiguration createConfigurationFromDirectory(Project project, @NotNull String directory) {
    VirtualFile virtualFile = findRelativeFile(directory, project.getBaseDir());
    assertNotNull(virtualFile);
    PsiElement element = PsiManager.getInstance(project).findDirectory(virtualFile);
    assertNotNull(element);
    return createConfigurationFromPsiElement(project, element);
  }

  @Nullable
  public static JUnitConfiguration createJUnitConfigurationFromPsiElement(Project project, @NotNull PsiElement psiElement) {
    RunConfiguration runConfiguration = createConfigurationFromPsiElement(project, psiElement);
    return runConfiguration instanceof JUnitConfiguration ? (JUnitConfiguration)runConfiguration : null;
  }

  @Nullable
  public static AndroidTestRunConfiguration createAndroidConfigurationFromPsiElement(Project project, @NotNull PsiElement psiElement) {
    RunConfiguration runConfiguration = createConfigurationFromPsiElement(project, psiElement);
    return runConfiguration instanceof AndroidTestRunConfiguration ? (AndroidTestRunConfiguration)runConfiguration : null;
  }

  @Nullable
  private static RunConfiguration createConfigurationFromPsiElement(Project project, @NotNull PsiElement psiElement) {
    ConfigurationContext context = createContext(project, psiElement);
    RunnerAndConfigurationSettings settings = context.getConfiguration();
    if (settings == null) {
      return null;
    }
    RunConfiguration configuration = settings.getConfiguration();
    if (configuration instanceof AndroidTestRunConfiguration || configuration instanceof JUnitConfiguration) {
      return configuration;
    }
    return null;
  }

  @NotNull
  public static ConfigurationContext createContext(Project project, @NotNull PsiElement psiElement) {
    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, project);
    if (LangDataKeys.MODULE.getData(dataContext) == null) {
      dataContext.put(LangDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(psiElement));
    }
    dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(psiElement));
    return ConfigurationContext.getFromContext(dataContext);
  }
}
