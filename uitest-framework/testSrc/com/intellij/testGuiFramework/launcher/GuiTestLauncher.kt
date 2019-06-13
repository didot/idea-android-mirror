/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.launcher

import com.android.testutils.TestUtils
import com.android.testutils.TestUtils.getWorkspaceRoot
import com.android.tools.idea.tests.gui.framework.AspectsAgentLogger
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.tests.IdeaTestSuiteBase
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testGuiFramework.impl.GuiTestStarter
import org.apache.log4j.Level
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Paths
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/**
 * [GuiTestLauncher] handles the mechanics of preparing to launch the client IDE and forking the process. It can do this two ways:
 *   1) "Locally," meaning it essentially runs 'java com.intellij.Main' with a classpath built from the classes loaded by
 *   GuiTestLauncher's ClassLoader augmented with some Jps magic. It also sets various system properties and VM options.
 *
 *   2) "By path," meaning it simply executes a given command. In particular, this can be studio.sh in the bin folder of a release build.
 *   No special system properties or VM options are set in this case.
 *
 * In both cases it adds arguments 'guitest' and 'port=######'.
 *
 * By default, option (1) is used. To use option (2), set the 'idea.gui.test.remote.ide.path' system property to the path to the desired
 * executable/script.
 *
 * See [GuiTestStarter] and [GuiTestThread] for details on what happens after the new process is forked.
 *
 * @author Sergey Karashevich
 */
object GuiTestLauncher {

  private val LOG = Logger.getInstance("#com.intellij.testGuiFramework.launcher.GuiTestLauncher")

  var process: Process? = null
  private var vmOptionsFile: File? = null

  private const val MAIN_CLASS_NAME = "com.intellij.idea.Main"

  private val classpathJar = File(GuiTests.getGuiTestRootDirPath(), "classpath.jar")

  init {
    LOG.setLevel(Level.INFO)
    if (!GuiTestOptions.isRunningOnRelease()) {
      buildClasspathJar()
    }
  }

  fun runIde (port: Int) {
    val path = GuiTestOptions.getRemoteIdePath()
    if (path == "undefined") {
      startIdeProcess(createArgs(port))
    } else {
      if (vmOptionsFile == null) {
        vmOptionsFile = createAugmentedVMOptionsFile(File(GuiTestOptions.getVmOptionsFilePath()), port)
      }
      startIdeProcess(createArgsByPath(path))
    }
  }

  private fun startIdeProcess(args: List<String>) {
    val processBuilder = ProcessBuilder().inheritIO().command(args)
    vmOptionsFile?.let {
      processBuilder.environment()["STUDIO_VM_OPTIONS"] = it.canonicalPath
    }
    val aspectsAgentLogPath = AspectsAgentLogger.getAspectsAgentLog()?.absolutePath
    if (aspectsAgentLogPath != null) {
      processBuilder.environment()["ASPECTS_AGENT_LOG"] = aspectsAgentLogPath
    }
    process = processBuilder.start()
  }

  /**
   * Creates a copy of the given VM options file in the temp directory, appending the options to set the application starter and port.
   * This is necessary to run the IDE via a native launcher, which doesn't accept command-line arguments.
   */
  private fun createAugmentedVMOptionsFile(originalFile: File, port: Int) =
    FileUtil.createTempFile("studio_uitests.vmoptions", "", true).apply {
      FileUtil.writeToFile(this, """${originalFile.readText()}
-Didea.gui.test.port=$port
-Didea.application.starter.command=${GuiTestStarter.COMMAND_NAME}
-Didea.gui.test.remote.ide.path=${GuiTestOptions.getRemoteIdePath()}
-Didea.gui.test.running.on.release=true
-Didea.gui.test.from.standalone.runner=${GuiTestOptions.isStandaloneMode()}
-Didea.config.path=${GuiTests.getConfigDirPath()}
-Didea.system.path=${GuiTests.getSystemDirPath()}""" + if (GuiTestOptions.isDebug()) """
-Didea.debug.mode=true
-Xdebug
-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=${GuiTestOptions.getDebugPort()}""" else "" )
    }

  private fun createArgs(port: Int) =
    listOf<String>()
        .plus(getCurrentJavaExec())
        .plus(getVmOptions(port))
        .plus("-classpath")
        .plus(classpathJar.absolutePath)
        .plus(MAIN_CLASS_NAME)

  private fun createArgsByPath(path: String): List<String> = listOf(path)

  /**
   * Default VM options to start IntelliJ IDEA (or IDEA-based IDE). To customize options use com.intellij.testGuiFramework.launcher.GuiTestOptions
   */
  private fun getVmOptions(port: Int): List<String> {
    // TODO(b/77341383): avoid having to sync manually with studio64.vmoptions
    val options = mutableListOf(
      /* studio64.vmoptions */
      "-Xms256m",
      "-Xmx1280m",
      "-XX:ReservedCodeCacheSize=240m",
      "-XX:SoftRefLRUPolicyMSPerMB=50",
      "-Dsun.io.useCanonCaches=false",
      "-Djava.net.preferIPv4Stack=true",
      "-Djna.nosys=true",
      "-Djna.boot.library.path=",
      "-XX:MaxJavaStackTraceDepth=10000",
      "-XX:+HeapDumpOnOutOfMemoryError",
      "-XX:-OmitStackTraceInFastThrow",
      "-ea",
      "-Dawt.useSystemAAFontSettings=lcd",
      "-Dsun.java2d.renderer=sun.java2d.marlin.MarlinRenderingEngine",
      /* studio.sh options */
      "-Didea.platform.prefix=AndroidStudio",
      "-Didea.jre.check=true",
      /* testing-specific options */
      "-Didea.config.path=${GuiTests.getConfigDirPath()}",
      "-Didea.system.path=${GuiTests.getSystemDirPath()}",
      "-Dplugin.path=${GuiTestOptions.getPluginPath()}",
      "-Ddisable.android.first.run=true",
      // Ensure UI tests do not block for analytics consent dialog, this will leave analytics at the default (opted-out) state.
      "-Ddisable.android.analytics.consent.dialog.for.test=true",
      "-Ddisable.config.import=true",
      "-Didea.application.starter.command=${GuiTestStarter.COMMAND_NAME}",
      "-Didea.gui.test.port=$port"
    )
    /* aspects agent options */
    if (!SystemInfo.IS_AT_LEAST_JAVA9) {  // b/134524025
      options += "-javaagent:${GuiTestOptions.getAspectsAgentJar()}=${GuiTestOptions.getAspectsAgentRules()};${GuiTestOptions.getAspectsAgentBaseline()}"
      options += "-Daspects.baseline.export.path=${GuiTestOptions.getAspectsBaselineExportPath()}"
    }
    /* options for BLeak */
    if (System.getProperty("enable.bleak") == "true") {
      options += "-Denable.bleak=true"
      options += "-Xmx16g"
      options += "-XX:+UseG1GC"
      val instrumentationAgentJar = File(TestUtils.getWorkspaceRoot(),
                                         "bazel-bin/tools/adt/idea/uitest-framework/testSrc/com/android/tools/idea/tests/gui/framework/heapassertions/bleak/agents/ObjectSizeInstrumentationAgent_deploy.jar")
      if (instrumentationAgentJar.exists()) {
        options += "-javaagent:${instrumentationAgentJar.absolutePath}"
      } else {
        println("Object size instrumentation agent not found - leak share reports will all be 0")
      }
      val jvmtiAgent = File(TestUtils.getWorkspaceRoot(),
                            "bazel-bin/tools/adt/idea/uitest-framework/testSrc/com/android/tools/idea/tests/gui/framework/heapassertions/bleak/agents/libjnibleakhelper.so")
      if (jvmtiAgent.exists()) {
        options += "-agentpath:${jvmtiAgent.absolutePath}"
        options += "-Dbleak.jvmti.enabled=true"
        options += "-Djava.library.path=${System.getProperty("java.library.path")}:${jvmtiAgent.parent}"
      } else {
        println("BLeak JVMTI agent not found. Falling back to Java implementation: application threads will not be paused, and traversal roots will be different")
      }
    } else {
      options += "-XX:+UseConcMarkSweepGC"
    }
    /* debugging options */
    if (GuiTestOptions.isDebug()) {
      options += "-Didea.debug.mode=true"
      options += "-Xdebug"
      options += "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=${GuiTestOptions.getDebugPort()}"
    }
    if (TestUtils.runningFromBazel()) {
      options += "-Didea.home=${IdeaTestSuiteBase.createTmpDir("tools/idea")}"
      options += "-Dgradle.user.home=${IdeaTestSuiteBase.createTmpDir("home")}"
      options += "-DANDROID_SDK_HOME=${IdeaTestSuiteBase.createTmpDir(".android")}"
      options += "-Dlayoutlib.thread.timeout=60000"
      options += "-Dresolve.descriptors.in.resources=true"
      options += "-Dstudio.dev.jdk=${getJdkPathForGradle()}"
    }
    return options
  }

  private fun getJdkPathForGradle(): String? {
    val jdk = File(getWorkspaceRoot(), "prebuilts/studio/jdk")
    if (jdk.exists()) {
      return File(jdk, "BUILD").toPath().toRealPath().toFile().getParentFile().absolutePath
    }
    return null
  }

  private fun getCurrentJavaExec(): String {
    val homeDir = File(System.getProperty("java.home"))
    val binDir = File(if (SystemInfo.IS_AT_LEAST_JAVA9) homeDir else homeDir.parentFile, "bin")
    val javaName = if (SystemInfo.isWindows) "java.exe" else "java"
    return File(binDir, javaName).path
  }

  private fun getTestClasspath(): List<File> = System.getProperty("java.class.path").split(File.pathSeparator).map(::File)

  private fun buildClasspathJar() {
    val files = getTestClasspath()
    val prefix = if (SystemInfo.isWindows) "file:/" else "file:"
    val classpath = StringBuilder().apply {
      for (file in files) {
        append(prefix + file.absolutePath.replace(" ", "%20").replace("\\", "/") + if (file.isDirectory) "/ " else " ")
      }
    }

    val manifest = Manifest()
    manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
    manifest.mainAttributes[Attributes.Name.CLASS_PATH] = classpath.toString()

    JarOutputStream(FileOutputStream(classpathJar), manifest).use {}
  }

}