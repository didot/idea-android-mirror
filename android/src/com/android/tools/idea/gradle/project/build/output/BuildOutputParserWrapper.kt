/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.output

import com.android.SdkConstants.DOT_GRADLE
import com.android.SdkConstants.FD_MERGED
import com.android.SdkConstants.FD_RES
import com.android.ide.common.blame.parser.aapt.AbstractAaptOutputParser.AAPT_TOOL_NAME
import com.android.ide.common.resources.MergingException.RESOURCE_ASSET_MERGER_TOOL_NAME
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.project.build.output.AndroidGradlePluginOutputParser.ANDROID_GRADLE_PLUGIN_MESSAGES_GROUP
import com.android.tools.idea.gradle.project.build.output.CmakeOutputParser.CMAKE
import com.android.tools.idea.gradle.project.build.output.XmlErrorOutputParser.Companion.XML_PARSING_GROUP
import com.android.tools.idea.stats.withProjectId
import com.android.utils.FileUtils
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.google.wireless.android.sdk.stats.BuildOutputWindowStats
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File
import java.util.function.Consumer

private val toolNameToEnumMap = mapOf("Java compiler" to BuildErrorMessage.ErrorType.JAVA_COMPILER,
                                      "Kotlin compiler" to BuildErrorMessage.ErrorType.KOTLIN_COMPILER,
                                      CLANG_COMPILER_MESSAGES_GROUP_PREFIX to BuildErrorMessage.ErrorType.CLANG,
                                      CMAKE to BuildErrorMessage.ErrorType.CMAKE,
                                      DATABINDING_GROUP to BuildErrorMessage.ErrorType.DATA_BINDING,
                                      XML_PARSING_GROUP to BuildErrorMessage.ErrorType.XML_PARSER,
                                      AAPT_TOOL_NAME to BuildErrorMessage.ErrorType.AAPT,
                                      "D8" to BuildErrorMessage.ErrorType.D8,
                                      "R8" to BuildErrorMessage.ErrorType.R8,
                                      RESOURCE_ASSET_MERGER_TOOL_NAME to BuildErrorMessage.ErrorType.RESOURCE_AND_ASSET_MERGER,
                                      ANDROID_GRADLE_PLUGIN_MESSAGES_GROUP to BuildErrorMessage.ErrorType.GENERAL_ANDROID_GRADLE_PLUGIN)

private fun findErrorType(messageGroup: String): BuildErrorMessage.ErrorType? = toolNameToEnumMap.filterKeys {
  messageGroup.startsWith(it)
}.values.firstOrNull()

fun sendBuildFailureMetrics(buildOutputParsers: List<BuildOutputParserWrapper>, project: Project) {
  try {
    val buildErrorMessages = mutableListOf<BuildErrorMessage>()
    buildOutputParsers.forEach {
      buildErrorMessages.addAll(it.buildErrorMessages)
    }

    UsageTracker.log(
      AndroidStudioEvent.newBuilder().withProjectId(project).setKind(
        AndroidStudioEvent.EventKind.BUILD_OUTPUT_WINDOW_STATS).setBuildOutputWindowStats(
        BuildOutputWindowStats.newBuilder().addAllBuildErrorMessages(buildErrorMessages).build()))
  }
  catch (e: Exception) {
    Logger.getInstance("BuildFailureMetricsReporting").error("Failed to send metrics", e)
  }
}

/**
 * A wrapper class for all the build output parsers so we can collect metrics on error output parsing.
 */
class BuildOutputParserWrapper(val parser: BuildOutputParser) : BuildOutputParser {

  val buildErrorMessages = ArrayList<BuildErrorMessage>()

  override fun parse(line: String?, reader: BuildOutputInstantReader?, messageConsumer: Consumer<in BuildEvent>?): Boolean {
    return parser.parse(line, reader) {
      messageConsumer?.accept(it)
      reportErrorEvent(it)
    }
  }

  fun reset() = buildErrorMessages.clear()

  private fun reportErrorEvent(buildEvent: BuildEvent) {
    if (buildEvent !is MessageEvent || buildEvent.kind != MessageEvent.Kind.ERROR) {
      return
    }

    val buildErrorMessageBuilder = BuildErrorMessage.newBuilder()

    findErrorType(buildEvent.group)?.let {
      buildErrorMessageBuilder.errorShownType = it
    }

    if (buildEvent is FileMessageEvent) {
      buildErrorMessageBuilder.fileLocationIncluded = true
      buildErrorMessageBuilder.fileIncludedType = getFileType(buildEvent.filePosition.file)
      if (buildEvent.filePosition.startLine >= 0) {
        buildErrorMessageBuilder.lineLocationIncluded = true
      }
    }

    buildErrorMessages.add(buildErrorMessageBuilder.build())
  }

  /**
   * Returns whether the file is build generated or user added.
   */
  private fun getFileType(file: File): BuildErrorMessage.FileType {
    val filePath = if (file.isAbsolute) file.absolutePath else file.path
    if (filePath.contains(File.separatorChar + DOT_GRADLE + File.separatorChar) ||
        filePath.contains(FileUtils.join(FD_RES, FD_MERGED))) {
      return BuildErrorMessage.FileType.BUILD_GENERATED_FILE
    }
    return BuildErrorMessage.FileType.PROJECT_FILE
  }
}