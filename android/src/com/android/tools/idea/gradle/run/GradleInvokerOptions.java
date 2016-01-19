/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.run;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.resources.Density;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.fd.FileChangeListener;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.fd.InstantRunUtils;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.GradleBuilds;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.DeviceFutures;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GradleInvokerOptions {
  private static final Logger LOG = Logger.getInstance(GradleInvokerOptions.class);

  @NotNull public final List<String> tasks;
  @Nullable public final BuildMode buildMode;
  @NotNull public final List<String> commandLineArguments;

  private GradleInvokerOptions(@NotNull List<String> tasks, @Nullable BuildMode buildMode, @NotNull List<String> commandLineArguments) {
    this.tasks = tasks;
    this.buildMode = buildMode;
    this.commandLineArguments = commandLineArguments;
  }

  public static GradleInvokerOptions create(@NotNull Project project,
                                            @Nullable DataContext context,
                                            @NotNull RunConfiguration configuration,
                                            @NotNull ExecutionEnvironment env,
                                            @Nullable String userGoal) {
    final Module[] modules = getModules(project, context, configuration);
    if (modules.length == 0) {
      throw new IllegalStateException("Unable to determine list of modules to build");
    }

    GradleInvoker.TestCompileType testCompileType = getTestCompileType(configuration);

    InstantRunBuildOptions instantRunBuildOptions = InstantRunBuildOptions.createAndReset(modules[0], env);

    return create(testCompileType, instantRunBuildOptions, new GradleModuleTasksProvider(modules), userGoal);
  }

  @VisibleForTesting()
  static GradleInvokerOptions create(@NotNull GradleInvoker.TestCompileType testCompileType,
                                     @Nullable InstantRunBuildOptions instantRunBuildOptions,
                                     @NotNull GradleTasksProvider gradleTasksProvider,
                                     @Nullable String userGoal) {
    if (!StringUtil.isEmpty(userGoal)) {
      return new GradleInvokerOptions(Collections.singletonList(userGoal), null, Collections.<String>emptyList());
    }

    if (testCompileType == GradleInvoker.TestCompileType.JAVA_TESTS) {
      BuildMode buildMode = BuildMode.COMPILE_JAVA;
      return new GradleInvokerOptions(gradleTasksProvider.getUnitTestTasks(buildMode), buildMode, Collections.<String>emptyList());
    }

    List<String> cmdLineArgs = Lists.newArrayList();
    List<String> tasks = Lists.newArrayList();

    // Inject instant run attributes
    // Note that these are specifically not injected for the unit or instrumentation tests
    if (testCompileType == GradleInvoker.TestCompileType.NONE && instantRunBuildOptions != null) {
      boolean incrementalBuild = !instantRunBuildOptions.cleanBuild &&      // e.g. build ids changed
                                 !instantRunBuildOptions.needsFullBuild;    // e.g. manifest changed

      cmdLineArgs.add(getInstantDevProperty(instantRunBuildOptions, incrementalBuild));
      cmdLineArgs.addAll(getDeviceSpecificArguments(instantRunBuildOptions.devices));

      if (instantRunBuildOptions.cleanBuild) {
        tasks.addAll(gradleTasksProvider.getCleanAndGenerateSourcesTasks());
      }
      else if (incrementalBuild) {
        return new GradleInvokerOptions(gradleTasksProvider.getIncrementalDexTasks(), null, cmdLineArgs);
      }
    }

    BuildMode buildMode = BuildMode.ASSEMBLE;
    tasks.addAll(gradleTasksProvider.getTasksFor(buildMode, testCompileType));
    return new GradleInvokerOptions(tasks, buildMode, cmdLineArgs);
  }

  @NotNull
  private static String getInstantDevProperty(@NotNull InstantRunBuildOptions buildOptions, boolean incrementalBuild) {
    StringBuilder sb = new StringBuilder(50);
    sb.append("-Pandroid.optional.compilation=INSTANT_DEV");

    // we need RESTART_ONLY in two scenarios: full builds, and for incremental builds when app is not running
    if (!incrementalBuild || !buildOptions.isAppRunning) {
      sb.append(",RESTART_ONLY");
    }

    FileChangeListener.Changes changes = buildOptions.fileChanges;
    if (incrementalBuild && !changes.nonSourceChanges) {
      if (changes.localResourceChanges) {
        sb.append(",LOCAL_RES_ONLY");
      }
      if (changes.localJavaChanges) {
        sb.append(",LOCAL_JAVA_ONLY");
      }
    }

    return sb.toString();
  }

  @NotNull
  private static Module[] getModules(@NotNull Project project, @Nullable DataContext context, @Nullable RunConfiguration configuration) {
    if (configuration instanceof ModuleRunProfile) {
      // ModuleBasedConfiguration includes Android and JUnit run configurations, including "JUnit: Rerun Failed Tests",
      // which is AbstractRerunFailedTestsAction.MyRunProfile.
      return  ((ModuleRunProfile)configuration).getModules();
    }
    else {
      return Projects.getModulesToBuildFromSelection(project, context);
    }
  }

  @NotNull
  private static Module[] getAffectedModules(@NotNull Project project, @NotNull Module[] modules) {
    final CompilerManager compilerManager = CompilerManager.getInstance(project);
    CompileScope scope = compilerManager.createModulesCompileScope(modules, true, true);
    return scope.getAffectedModules();
  }

  @NotNull
  private static GradleInvoker.TestCompileType getTestCompileType(@Nullable RunConfiguration runConfiguration) {
    String id = runConfiguration != null ? runConfiguration.getType().getId() : null;
    return GradleInvoker.getTestCompileType(id);
  }

  // These are defined in AndroidProject in the builder model for 1.5+; remove and reference directly
  // when Studio is updated to use the new model
  private static final String PROPERTY_BUILD_API = "android.injected.build.api";
  private static final String PROPERTY_BUILD_DENSITY = "android.injected.build.density";

  @NotNull
  private static List<String> getDeviceSpecificArguments(@NotNull List<AndroidDevice> devices) {
    if (devices.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> properties = new ArrayList<String>(2);

    // Find the minimum value of the build API level and pass it to Gradle as a property
    AndroidDevice device = devices.get(0); // Instant Run only supports launching to a single device
    AndroidVersion version = device.getVersion();
    properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_BUILD_API, Integer.toString(version.getFeatureLevel())));

    // If we are building for only one device, pass the density.
    Density density = Density.getEnum(device.getDensity());
    if (density != null) {
      properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_BUILD_DENSITY, density.getResourceValue()));
    }

    return properties;
  }

  @NotNull
  private static List<AndroidDevice> getTargetDevices(@NotNull ExecutionEnvironment env) {
    DeviceFutures deviceFutures = env.getCopyableUserData(AndroidRunConfigurationBase.DEVICE_FUTURES_KEY);
    return deviceFutures == null ? Collections.<AndroidDevice>emptyList() : deviceFutures.getDevices();
  }

  static class InstantRunBuildOptions {
    public final boolean cleanBuild;
    public final boolean needsFullBuild;
    public final boolean isAppRunning;
    @NotNull private final FileChangeListener.Changes fileChanges;
    @NotNull public final List<AndroidDevice> devices;

    InstantRunBuildOptions(boolean cleanBuild,
                           boolean needsFullBuild,
                           boolean isAppRunning,
                           @NotNull FileChangeListener.Changes changes,
                           @NotNull List<AndroidDevice> devices) {
      this.cleanBuild = cleanBuild;
      this.needsFullBuild = needsFullBuild;
      this.isAppRunning = isAppRunning;
      this.fileChanges = changes;
      this.devices = devices;
    }

    @Nullable
    static InstantRunBuildOptions createAndReset(@NotNull Module module, @NotNull ExecutionEnvironment env) {
      if (!InstantRunUtils.isInstantRunEnabled(env) ||
          !InstantRunSettings.isInstantRunEnabled(module.getProject()) ||
          !InstantRunManager.isPatchableApp(module)) {
        return null;
      }

      FileChangeListener.Changes changes = InstantRunManager.get(module.getProject()).getChangesAndReset();

      return new InstantRunBuildOptions(InstantRunUtils.needsCleanBuild(env),
                                        InstantRunUtils.needsFullBuild(env),
                                        InstantRunUtils.isAppRunning(env),
                                        changes,
                                        getTargetDevices(env));
    }
  }

  interface GradleTasksProvider {
    @NotNull
    List<String> getCleanAndGenerateSourcesTasks();

    @NotNull
    List<String> getUnitTestTasks(@NotNull BuildMode buildMode);

    @NotNull
    List<String> getIncrementalDexTasks();

    @NotNull
    List<String> getTasksFor(@NotNull BuildMode buildMode, @NotNull GradleInvoker.TestCompileType testCompileType);
  }

  static class GradleModuleTasksProvider implements GradleTasksProvider {
    private final Module[] myModules;

    GradleModuleTasksProvider(@NotNull Module[] modules) {
      myModules = modules;
      if (myModules.length == 0) {
        throw new IllegalArgumentException("No modules provided");
      }
    }

    @NotNull
    @Override
    public List<String> getCleanAndGenerateSourcesTasks() {
      List<String> tasks = Lists.newArrayList();

      tasks.add(GradleBuilds.CLEAN_TASK_NAME);
      tasks.addAll(GradleInvoker.findTasksToExecute(myModules, BuildMode.SOURCE_GEN, GradleInvoker.TestCompileType.NONE));

      return tasks;
    }

    @NotNull
    @Override
    public List<String> getUnitTestTasks(@NotNull BuildMode buildMode) {
      // Make sure all "intermediates/classes" directories are up-to-date.
      Module[] affectedModules = getAffectedModules(myModules[0].getProject(), myModules);
      return GradleInvoker.findTasksToExecute(affectedModules, buildMode, GradleInvoker.TestCompileType.JAVA_TESTS);
    }

    @NotNull
    @Override
    public List<String> getIncrementalDexTasks() {
      Module module = myModules[0];
      AndroidGradleModel model = AndroidGradleModel.get(module);
      if (model == null) {
        throw new IllegalStateException("Attempted to obtain incremental dex task for module that does not have a Gradle facet");
      }

      return Collections.singletonList(InstantRunManager.getIncrementalDexTask(model, module));
    }

    @NotNull
    @Override
    public List<String> getTasksFor(@NotNull BuildMode buildMode, @NotNull GradleInvoker.TestCompileType testCompileType) {
      return GradleInvoker.findTasksToExecute(myModules, buildMode, testCompileType);
    }
  }
}
