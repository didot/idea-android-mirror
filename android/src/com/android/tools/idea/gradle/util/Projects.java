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

import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.options.ExternalBuildOptionListener;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

/**
 * Utility methods for {@link Project}s.
 */
public final class Projects {
  private static final Key<BuildMode> PROJECT_BUILD_MODE_KEY = Key.create("android.gradle.project.build.mode");

  // TOOD: Remove once we support Android Gradle plug-in 0.6.0 only.
  private static final Key<Boolean> PROJECT_CAN_COMPILE_JAVA_ONLY_KEY = Key.create("android.gradle.project.can.compile.java.only");

  private static final Logger LOG = Logger.getInstance(Projects.class);

  private Projects() {
  }

  /**
   * Takes a project and compiles it, rebuilds it or simply generates source code based on the {@link BuildMode} set on the given project.
   * This method does nothing if the project does not have a {@link BuildMode}.
   *
   * @param project the given project.
   */
  public static void make(@NotNull Project project) {
    BuildMode buildMode = getBuildModeFrom(project);
    if (buildMode != null) {
      switch (buildMode) {
        case COMPILE:
          compile(project);
          break;
        case REBUILD:
          rebuild(project);
          break;
        case SOURCE_GEN:
          generateSourcesOnly(project);
          break;
        case COMPILE_JAVA:
          compileJava(project);
      }
    }
  }

  @Nullable
  public static BuildMode getBuildModeFrom(@NotNull Project project) {
    return project.getUserData(PROJECT_BUILD_MODE_KEY);
  }

  /**
   * Compiles the given project.
   *
   * @param project the given project.
   */
  public static void compile(@NotNull Project project) {
    setProjectBuildMode(project, BuildMode.COMPILE);
    doMake(project);
  }

  /**
   * Rebuilds the given project. "Rebuilding" cleans the output directories and then compiles the project.
   *
   * @param project the given project.
   */
  public static void rebuild(@NotNull Project project) {
    setProjectBuildMode(project, BuildMode.REBUILD);
    CompilerManager.getInstance(project).rebuild(null);
  }

  /**
   * Generates source code instead of a full compilation. This method does nothing if the Gradle model does not specify the name of the
   * Gradle task to invoke.
   *
   * @param project the given project.
   */
  public static void generateSourcesOnly(@NotNull Project project) {
    if (hasSourceGenTasks(project)) {
      setProjectBuildMode(project, BuildMode.SOURCE_GEN);
      doMake(project);
    } else {
      String msg = String.format("Unable to find tasks for generating source code for project '%1$s'", project.getName());
      LOG.info(msg);
      removeBuildActionFrom(project);
    }
  }

  private static boolean hasSourceGenTasks(@NotNull Project project) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      AndroidFacet androidFacet = Facets.getFirstFacetOfType(module, AndroidFacet.ID);
      if (androidFacet != null) {
        JpsAndroidModuleProperties androidFacetState = androidFacet.getProperties();
        String taskName = androidFacetState.SOURCE_GEN_TASK_NAME;
        if (taskName != null && !taskName.isEmpty() && !"TODO".equalsIgnoreCase(taskName)) {
          return true;
        }
      }
    }
    return false;
  }

  public static void setProjectCanCompileJavaOnly(@NotNull Project project, boolean canCompileJavaOnly) {
    if (canCompileJavaOnly) {
      project.putUserData(PROJECT_CAN_COMPILE_JAVA_ONLY_KEY, canCompileJavaOnly);
    }
    else {
      project.putUserData(PROJECT_CAN_COMPILE_JAVA_ONLY_KEY, null);
    }
  }

  public static void compileJava(@NotNull Project project) {
    if (canCompileJavaOnly(project)) {
      setProjectBuildMode(project, BuildMode.COMPILE_JAVA);
      doMake(project);
    } else {
      String msg = String.format("Unable to find tasks for compiling Java code for project '%1$s'", project.getName());
      LOG.info(msg);
      removeBuildActionFrom(project);
    }
  }

  public static boolean canCompileJavaOnly(@NotNull Project project) {
    return project.getUserData(PROJECT_CAN_COMPILE_JAVA_ONLY_KEY) == Boolean.TRUE;
  }

  private static void doMake(@NotNull Project project) {
    CompilerManager.getInstance(project).make(null);
  }

  /**
   * Indicates whether the given project has at least one module that has the {@link AndroidGradleFacet}.
   *
   * @param project the given project.
   * @return {@code true} if the given project has at least one module that has the Android-Gradle facet, {@code false} otherwise.
   */
  public static boolean isGradleProject(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      if (Facets.getFirstFacetOfType(module, AndroidGradleFacet.TYPE_ID) != null) {
        return true;
      }
    }
    return false;
  }

  public static boolean isIdeaAndroidProject(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      FacetManager facetManager = FacetManager.getInstance(module);
      Multimap<FacetTypeId, Facet> facetsByType = ArrayListMultimap.create();
      for (Facet facet : facetManager.getAllFacets()) {
        facetsByType.put(facet.getTypeId(), facet);
      }
      boolean hasAndroidFacet = !facetsByType.get(AndroidFacet.ID).isEmpty();
      boolean hasAndroidGradleFacet = !facetsByType.get(AndroidGradleFacet.TYPE_ID).isEmpty();
      if (hasAndroidFacet && !hasAndroidGradleFacet) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the current Gradle project. This method must be called in the event dispatch thread.
   *
   * @return the current Gradle project, or {@code null} if the current project is not a Gradle one or if there are no projects open.
   */
  @Nullable
  public static Project getCurrentGradleProject() {
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    boolean isGradleProject = project != null && isGradleProject(project);
    return isGradleProject ? project : null;
  }

  /**
   * Ensures that "External Build" is enabled for the given Gradle-based project. External build is the type of build that delegates project
   * building to Gradle.
   *
   * @param project the given project. This method does not do anything if the given project is not a Gradle-based project.
   */
  public static void ensureExternalBuildIsEnabledForGradleProject(@NotNull Project project) {
    if (isGradleProject(project)) {
      CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(project);
      boolean wasUsingExternalMake = workspaceConfiguration.USE_COMPILE_SERVER;
      if (!wasUsingExternalMake) {
        String format = "Enabled 'External Build' for Android project '%1$s'. Otherwise, the project will not be built with Gradle";
        String msg = String.format(format, project.getName());
        LOG.info(msg);
        workspaceConfiguration.USE_COMPILE_SERVER = true;
        MessageBus messageBus = project.getMessageBus();
        messageBus.syncPublisher(ExternalBuildOptionListener.TOPIC).externalBuildOptionChanged(workspaceConfiguration.USE_COMPILE_SERVER);
      }
    }
  }

  public static void removeBuildActionFrom(@NotNull Project project) {
    setProjectBuildMode(project, null);
  }

  public static void setProjectBuildMode(@NotNull Project project, @Nullable BuildMode action) {
    project.putUserData(PROJECT_BUILD_MODE_KEY, action);
  }
}
