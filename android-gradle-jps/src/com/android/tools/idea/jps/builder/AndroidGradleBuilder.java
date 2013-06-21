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
package com.android.tools.idea.jps.builder;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.jps.AndroidGradleJps;
import com.android.tools.idea.jps.model.JpsAndroidGradleModuleExtension;
import com.android.tools.idea.jps.output.parser.GradleErrorOutputParser;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.AndroidSourceGeneratingBuilder;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleExtensionImpl;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.resources.ResourcesBuilder;
import org.jetbrains.jps.incremental.resources.StandardResourceBuilderEnabler;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Builds an IDEA project using Gradle.
 */
public class AndroidGradleBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance(AndroidGradleBuilder.class);
  private static final GradleErrorOutputParser ERROR_OUTPUT_PARSER = new GradleErrorOutputParser();

  @NonNls private static final String BUILDER_NAME = "Android Gradle Builder";
  @NonNls private static final String DEFAULT_ASSEMBLE_TASK_NAME = "assemble";
  @NonNls private static final String GRADLE_SEPARATOR = ":";

  protected AndroidGradleBuilder() {
    super(BuilderCategory.TRANSLATOR);
    ResourcesBuilder.registerEnabler(new StandardResourceBuilderEnabler() {
      @Override
      public boolean isResourceProcessingEnabled(JpsModule module) {
        JpsProject project = module.getProject();
        return !AndroidGradleJps.hasAndroidGradleFacet(project);
      }
    });
  }

  /**
   * Disables IDEA's Java and Android builders for Gradle-imported projects. They are no longer needed since we build with Gradle.
   */
  @Override
  public void buildStarted(CompileContext context) {
    JpsProject project = context.getProjectDescriptor().getProject();
    if (AndroidGradleJps.hasAndroidGradleFacet(project)) {
      JavaBuilder.IS_ENABLED.set(context, false);
      AndroidSourceGeneratingBuilder.IS_ENABLED.set(context, false);
    }
  }

  /**
   * Builds a project using Gradle.
   *
   * @return {@link ExitCode#OK} if compilation with Gradle succeeds without errors.
   * @throws ProjectBuildException if something goes wrong while invoking Gradle or if there are compilation errors. Compilation errors are
   *                               displayed in IDEA's "Problems" view.
   */
  @NotNull
  @Override
  public ExitCode build(CompileContext context,
                        ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        OutputConsumer outputConsumer) throws ProjectBuildException {
    JpsAndroidGradleModuleExtension extension = AndroidGradleJps.getFirstExtension(chunk);
    if (extension == null) {
      String format = "Project '%1$s' does not have the '%2$s' facet. Nothing done.";
      LOG.info(String.format(format, getProjectName(context), AndroidGradleFacet.NAME));
      return ExitCode.NOTHING_DONE;
    }

    boolean isRebuild = JavaBuilderUtil.isForcedRecompilationAllJavaModules(context);
    boolean buildTests = AndroidJpsUtil.isInstrumentationTestContext(context);

    if (!isRebuild) {
      try {
        boolean hasChanges = dirtyFilesHolder.hasDirtyFiles() || dirtyFilesHolder.hasRemovedFiles();
        if (!hasChanges) {
          String msg = String.format("No changes found while building '%1$s'. Not calling Gradle.", chunk.getName());
          LOG.info(msg);
          return ExitCode.NOTHING_DONE;
        }
      }
      catch (IOException e) {
        String msg = String.format("Failed to determine changes in '%1$s'. Building anyway.", chunk.getName());
        LOG.error(msg, e);
      }
    }

    String[] buildTasks = getBuildTasks(chunk, isRebuild, buildTests);
    if (buildTasks.length == 0) {
      String format = "No build tasks found for project '%1$s'. Nothing done.";
      LOG.info(String.format(format, getProjectName(context)));
      return ExitCode.NOTHING_DONE;
    }

    String msg = "Gradle build using tasks: " + Arrays.toString(buildTasks);
    context.processMessage(new ProgressMessage(msg));
    LOG.info(msg);

    ensureTempDirExists();

    BuilderExecutionSettings executionSettings;
    try {
      executionSettings = new BuilderExecutionSettings();
    } catch (RuntimeException e) {
      throw new ProjectBuildException(e);
    }

    LOG.info("Using execution settings: " + executionSettings);

    String format = "About to build project '%1$s' located at %2$s";
    LOG.info(String.format(format, getProjectName(context), executionSettings.getProjectDir().getAbsolutePath()));

    return doBuild(context, buildTasks, executionSettings);
  }

  @NotNull
  private static String[] getBuildTasks(@NotNull ModuleChunk chunk, boolean isRebuild, boolean buildTests) {
    List<String> tasks = Lists.newArrayList();
    for (JpsModule module : chunk.getModules()) {
      populateBuildTasks(module, tasks, isRebuild, buildTests);
    }
    return tasks.toArray(new String[tasks.size()]);
  }

  private static void populateBuildTasks(@NotNull JpsModule module, @NotNull List<String> tasks, boolean isRebuild, boolean buildTests) {
    JpsAndroidGradleModuleExtension androidGradleFacet = AndroidGradleJps.getExtension(module);
    if (androidGradleFacet == null) {
      return;
    }
    String gradleProjectPath = androidGradleFacet.getProperties().GRADLE_PROJECT_PATH;
    if (gradleProjectPath == null) {
      // Gradle project path is never, ever null. If the path is empty, it shows as ":". We had reports of this happening. It is likely that
      // users manually added the Android-Gradle facet to a project. After all it is likely not to be a Gradle module. Better quit and not
      // build the module.
      String format = "Module '%1$s' does not have a Gradle path. It is likely that this module was manually added by the user.";
      String msg = String.format(format, module.getName());
      LOG.warn(msg);
      return;
    }
    String assembleTaskName = null;
    JpsAndroidModuleExtensionImpl androidFacet = (JpsAndroidModuleExtensionImpl)AndroidJpsUtil.getExtension(module);
    if (androidFacet != null) {
      JpsAndroidModuleProperties properties = androidFacet.getProperties();
      assembleTaskName = properties.ASSEMBLE_TASK_NAME;
    }
    if (Strings.isNullOrEmpty(assembleTaskName)) {
      if (GRADLE_SEPARATOR.equals(gradleProjectPath) && androidFacet == null) {
        // If there is no task and no Android facet, this module is in reality a root project directory that does not map to a Gradle
        // project. Do not build it with Gradle.
        return;
      }
      assembleTaskName = DEFAULT_ASSEMBLE_TASK_NAME;
    }
    if (isRebuild) {
      tasks.add(createBuildTask(gradleProjectPath, "clean"));
    }
    assert assembleTaskName != null;
    tasks.add(createBuildTask(gradleProjectPath, assembleTaskName));

    if (buildTests && androidFacet != null) {
      JpsAndroidModuleProperties properties = androidFacet.getProperties();
      if (StringUtil.isNotEmpty(properties.ASSEMBLE_TEST_TASK_NAME)) {
        tasks.add(createBuildTask(gradleProjectPath, properties.ASSEMBLE_TEST_TASK_NAME));
      }
    }
  }

  @NotNull
  private static String createBuildTask(@NotNull String gradleProjectPath, @NotNull String taskName) {
    return gradleProjectPath + GRADLE_SEPARATOR + taskName;
  }

  private static void ensureTempDirExists() {
    // Gradle checks that the dir at "java.io.tmpdir" exists, and if it doesn't it fails (on Windows.)
    String tmpDirProperty = System.getProperty("java.io.tmpdir");
    if (!Strings.isNullOrEmpty(tmpDirProperty)) {
      File tmpDir = new File(tmpDirProperty);
      try {
        FileUtil.ensureExists(tmpDir);
      }
      catch (IOException e) {
        LOG.warn("Unable to create temp directory", e);
      }
    }
  }

  @NotNull
  private static CompilerMessage createCompilerErrorMessage(@NotNull String msg) {
    return AndroidGradleJps.createCompilerMessage(BuildMessage.Kind.ERROR, msg);
  }

  @NotNull
  private static String getProjectName(@NotNull CompileContext context) {
    return context.getProjectDescriptor().getProject().getName();
  }

  @NotNull
  private static ExitCode doBuild(@NotNull CompileContext context,
                                  @NotNull String[] buildTasks,
                                  @NotNull BuilderExecutionSettings executionSettings) throws ProjectBuildException {
    GradleConnector connector = getGradleConnector(executionSettings);

    ProjectConnection connection = connector.connect();
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    try {
      BuildLauncher launcher = connection.newBuild();
      launcher.forTasks(buildTasks);

      List<String> jvmArgs = Lists.newArrayList();

      int xmx = executionSettings.getGradleDaemonMaxMemoryInMb();
      if (xmx > 0) {
        jvmArgs.add(String.format("-Xmx%dm", xmx));
      }

      if (!jvmArgs.isEmpty()) {
        LOG.info("Passing JVM args to Gradle Tooling API: " + jvmArgs);
        launcher.setJvmArguments(jvmArgs.toArray(new String[jvmArgs.size()]));
      }

      launcher.setStandardOutput(stdout);
      launcher.setStandardError(stderr);
      launcher.run();
    }
    catch (BuildException e) {
      handleBuildException(e, context, stderr.toString());
    }
    finally {
      String outText = stdout.toString();
      context.processMessage(new ProgressMessage(outText, 1.0f));
      Closeables.closeQuietly(stdout);
      Closeables.closeQuietly(stderr);
      connection.close();
    }

    return ExitCode.OK;
  }

  @NotNull
  private static GradleConnector getGradleConnector(@NotNull BuilderExecutionSettings executionSettings) {
    GradleConnector connector = GradleConnector.newConnector();
    if (connector instanceof DefaultGradleConnector) {
      DefaultGradleConnector defaultConnector = (DefaultGradleConnector)connector;

      if (executionSettings.isEmbeddedGradleDaemonEnabled()) {
        LOG.info("Using Gradle embedded mode.");
        defaultConnector.embedded(true);
      }

      defaultConnector.setVerboseLogging(executionSettings.isVerboseLoggingEnabled());

      int daemonMaxIdleTimeInMs = executionSettings.getGradleDaemonMaxIdleTimeInMs();
      if (daemonMaxIdleTimeInMs > 0) {
        defaultConnector.daemonMaxIdleTime(daemonMaxIdleTimeInMs, TimeUnit.MILLISECONDS);
      }
    }

    connector.forProjectDirectory(executionSettings.getProjectDir());

    File gradleHomeDir = executionSettings.getGradleHomeDir();
    if (gradleHomeDir != null) {
      connector.useInstallation(gradleHomeDir);
    }

    File gradleServiceDir = executionSettings.getGradleServiceDir();
    if (gradleServiceDir != null) {
      connector.useGradleUserHomeDir(gradleServiceDir);
    }

    return connector;
  }

  /**
   * Something went wrong while invoking Gradle. Since we cannot distinguish an execution error from compilation errors easily, we first try
   * to show, in the "Problems" view, compilation errors by parsing the error output. If no errors are found, we show the stack trace in the
   * "Problems" view. The idea is that we need to somehow inform the user that something went wrong.
   */
  private static void handleBuildException(BuildException e, CompileContext context, String stdErr) throws ProjectBuildException {
    Collection<CompilerMessage> compilerMessages = ERROR_OUTPUT_PARSER.parseErrorOutput(stdErr);
    if (!compilerMessages.isEmpty()) {
      for (CompilerMessage message : compilerMessages) {
        context.processMessage(message);
      }
      return;
    }
    // There are no error messages to present. Show some feedback indicating that something went wrong.
    if (!stdErr.isEmpty()) {
      // Show the contents of stderr as a compiler error.
      context.processMessage(createCompilerErrorMessage(stdErr));
    }
    else {
      // Since we have nothing else to show, just print the stack trace of the caught exception.
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try {
        //noinspection IOResourceOpenedButNotSafelyClosed
        e.printStackTrace(new PrintStream(out));
        String message = "Internal error:" + SystemProperties.getLineSeparator() + out.toString();
        context.processMessage(createCompilerErrorMessage(message));
      }
      finally {
        Closeables.closeQuietly(out);
      }
    }
    throw new ProjectBuildException(e.getMessage());
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return BUILDER_NAME;
  }

  @NotNull
  @Override
  public List<String> getCompilableFileExtensions() {
    return ImmutableList.of(SdkConstants.EXT_AIDL, SdkConstants.EXT_FS, SdkConstants.EXT_JAVA, SdkConstants.EXT_RS);
  }
}
