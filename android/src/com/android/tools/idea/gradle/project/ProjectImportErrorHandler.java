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
package com.android.tools.idea.gradle.project;

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.service.notification.errors.FailedToParseSdkErrorHandler;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.LocationAwareExternalSystemException;
import com.intellij.openapi.util.Pair;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectImportErrorHandler;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.FN_LOCAL_PROPERTIES;
import static com.android.SdkConstants.GRADLE_MINIMUM_VERSION;
import static com.android.tools.idea.gradle.service.notification.errors.MissingAndroidSdkErrorHandler.FIX_SDK_DIR_PROPERTY;
import static com.intellij.openapi.util.text.StringUtil.*;

/**
 * Provides better error messages for android projects import failures.
 */
public class ProjectImportErrorHandler extends AbstractProjectImportErrorHandler {

  public static final String GRADLE_DSL_METHOD_NOT_FOUND_ERROR_PREFIX = "Gradle DSL method not found";
  public static final String CONNECTION_PERMISSION_DENIED_PREFIX = "Connection to the Internet denied.";
  public static final String INSTALL_ANDROID_SUPPORT_REPO = "Please install the Android Support Repository from the Android SDK Manager.";

  private static final Pattern SDK_NOT_FOUND_PATTERN = Pattern.compile("The SDK directory '(.*?)' does not exist.");
  private static final Pattern CLASS_NOT_FOUND_PATTERN = Pattern.compile("(.+) not found.");

  private static final String EMPTY_LINE = "\n\n";
  private static final String UNSUPPORTED_GRADLE_VERSION_ERROR = "Gradle version " + GRADLE_MINIMUM_VERSION + " is required";
  private static final String SDK_DIR_PROPERTY_MISSING = "No sdk.dir property defined in local.properties file.";

  private static final Pattern ERROR_LOCATION_PATTERN = Pattern.compile(".* file '(.*)'( line: ([\\d]+))?");

  @Override
  @Nullable
  public ExternalSystemException getUserFriendlyError(@NotNull Throwable error,
                                                      @NotNull String projectPath,
                                                      @Nullable String buildFilePath) {
    if (error instanceof ExternalSystemException) {
      // This is already a user-friendly error.
      //noinspection ThrowableResultOfMethodCallIgnored
      UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setCategory(AndroidStudioEvent.EventCategory.GRADLE_SYNC)
                                       .setKind(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
                                       .setGradleSyncFailure(GradleSyncFailure.UNKNOWN_GRADLE_FAILURE));

      return (ExternalSystemException)error;
    }

    Pair<Throwable, String> rootCauseAndLocation = getRootCauseAndLocation(error);
    Throwable rootCause = rootCauseAndLocation.getFirst();

    if (isOldGradleVersion(rootCause)) {
      UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setCategory(AndroidStudioEvent.EventCategory.GRADLE_SYNC)
                                       .setKind(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
                                       .setGradleSyncFailure(GradleSyncFailure.UNSUPPORTED_GRADLE_VERSION));

      String msg = "The project is using an unsupported version of Gradle.\n" + FIX_GRADLE_VERSION;
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    String rootCauseText = rootCause.toString();
    if (startsWith(rootCauseText, "org.gradle.api.internal.MissingMethodException")) {
      String method = parseMissingMethod(rootCauseText);
      UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setCategory(AndroidStudioEvent.EventCategory.GRADLE_SYNC)
                                       .setKind(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
                                       .setGradleSyncFailure(GradleSyncFailure.DSL_METHOD_NOT_FOUND)
                                       .setGradleMissingSignature(method));

      return createUserFriendlyError(GRADLE_DSL_METHOD_NOT_FOUND_ERROR_PREFIX + ": '" + method + "'", rootCauseAndLocation.getSecond());
    }

    if (rootCause instanceof SocketException) {
      String message = rootCause.getMessage();
      if (message != null && message.contains("Permission denied: connect")) {
        UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                         .setCategory(AndroidStudioEvent.EventCategory.GRADLE_SYNC)
                                         .setKind(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
                                         .setGradleSyncFailure(GradleSyncFailure.CONNECTION_DENIED));

        // Location of build.gradle is useless for this error. Omitting it.
        return createUserFriendlyError(CONNECTION_PERMISSION_DENIED_PREFIX, null);
      }
    }

    if (rootCause instanceof UnknownHostException) {
      UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setCategory(AndroidStudioEvent.EventCategory.GRADLE_SYNC)
                                       .setKind(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
                                       .setGradleSyncFailure(GradleSyncFailure.UNKNOWN_HOST));

      String msg = String.format("Unknown host '%1$s'. You may need to adjust the proxy settings in Gradle.", rootCause.getMessage());
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof IllegalStateException || rootCause instanceof ExternalSystemException) {
      // Missing platform in SDK now also comes as a ExternalSystemException. This may be caused by changes in IDEA's "External System
      // Import" framework.
      String msg = rootCause.getMessage();
      if (msg != null) {
        if (msg.startsWith("failed to find target android-")) {
          UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                           .setCategory(AndroidStudioEvent.EventCategory.GRADLE_SYNC)
                                           .setKind(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
                                           .setGradleSyncFailure(GradleSyncFailure.MISSING_ANDROID_PLATFORM));

          // Location of build.gradle is useless for this error. Omitting it.
          return createUserFriendlyError(msg, null);
        }
        if (msg.startsWith("failed to find Build Tools")) {
          UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                           .setCategory(AndroidStudioEvent.EventCategory.GRADLE_SYNC)
                                           .setKind(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
                                           .setGradleSyncFailure(GradleSyncFailure.MISSING_BUILD_TOOLS));

          // Location of build.gradle is useless for this error. Omitting it.
          return createUserFriendlyError(msg, null);
        }
      }
    }

    if (rootCause instanceof RuntimeException) {
      String msg = rootCause.getMessage();

      // With this condition we cover 2 similar messages about the same problem.
      if (msg != null && msg.contains("Could not find") && msg.contains("com.android.support:")) {
        UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                         .setCategory(AndroidStudioEvent.EventCategory.GRADLE_SYNC)
                                         .setKind(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
                                         .setGradleSyncFailure(GradleSyncFailure.MISSING_ANDROID_SUPPORT_REPO));

        // We keep the original error message and we append a hint about how to fix the missing dependency.
        String newMsg = msg + EMPTY_LINE + INSTALL_ANDROID_SUPPORT_REPO;
        // Location of build.gradle is useless for this error. Omitting it.
        return createUserFriendlyError(newMsg, null);
      }

      if (msg != null && msg.contains(FailedToParseSdkErrorHandler.FAILED_TO_PARSE_SDK_ERROR)) {
        UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                         .setCategory(AndroidStudioEvent.EventCategory.GRADLE_SYNC)
                                         .setKind(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
                                         .setGradleSyncFailure(GradleSyncFailure.FAILED_TO_PARSE_SDK));

        String newMsg = msg + EMPTY_LINE + "The Android SDK may be missing the directory 'add-ons'.";
        // Location of build.gradle is useless for this error. Omitting it.
        return createUserFriendlyError(newMsg, null);
      }

      if (msg != null && (msg.equals(SDK_DIR_PROPERTY_MISSING) || SDK_NOT_FOUND_PATTERN.matcher(msg).matches())) {
        UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                         .setCategory(AndroidStudioEvent.EventCategory.GRADLE_SYNC)
                                         .setKind(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
                                         .setGradleSyncFailure(GradleSyncFailure.SDK_NOT_FOUND));

        String newMsg = msg;
        File buildProperties = new File(projectPath, FN_LOCAL_PROPERTIES);
        if (buildProperties.isFile()) {
          newMsg += EMPTY_LINE + FIX_SDK_DIR_PROPERTY;
        }
        return createUserFriendlyError(newMsg, null);
      }
    }

    if (rootCause instanceof OutOfMemoryError) {
      UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setCategory(AndroidStudioEvent.EventCategory.GRADLE_SYNC)
                                       .setKind(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
                                       .setGradleSyncFailure(GradleSyncFailure.OUT_OF_MEMORY));

      // The OutOfMemoryError happens in the Gradle daemon process.
      String originalMessage = rootCause.getMessage();
      String msg = "Out of memory";
      if (originalMessage != null && !originalMessage.isEmpty()) {
        msg = msg + ": " + originalMessage;
      }
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof NoSuchMethodError) {
      String methodName = Strings.nullToEmpty(rootCause.getMessage());

      UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setCategory(AndroidStudioEvent.EventCategory.GRADLE_SYNC)
                                       .setKind(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
                                       .setGradleSyncFailure(GradleSyncFailure.METHOD_NOT_FOUND)
                                       .setGradleMissingSignature(methodName));

      String msg = String.format("Unable to find method '%1$s'.", methodName);
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof ClassNotFoundException) {
      String className = Strings.nullToEmpty(rootCause.getMessage());

      UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setCategory(AndroidStudioEvent.EventCategory.GRADLE_SYNC)
                                       .setKind(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
                                       .setGradleSyncFailure(GradleSyncFailure.CLASS_NOT_FOUND)
                                       .setGradleMissingSignature(className));

      Matcher matcher = CLASS_NOT_FOUND_PATTERN.matcher(className);
      if (matcher.matches()) {
        className = matcher.group(1);
      }

      String msg = String.format("Unable to load class '%1$s'.", className);
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    // Create ExternalSystemException or LocationAwareExternalSystemException, so that
    // it goes to SyncErrorHandlers directly.
    String location = rootCauseAndLocation.getSecond();
    String errMessage;
    if (rootCause.getMessage() == null) {
      StringWriter writer = new StringWriter();
      rootCause.printStackTrace(new PrintWriter(writer));
      errMessage = writer.toString();
    }
    else {
      errMessage = rootCause.getMessage();
    }

    if (!errMessage.isEmpty() && Character.isLowerCase(errMessage.charAt(0))) {
      // Message starts with lower case letter. Sentences should start with uppercase.
      errMessage = "Cause: " + errMessage;
    }

    ExternalSystemException exception = null;
    if (isNotEmpty(location)) {
      Pair<String, Integer> pair = getErrorLocation(location);
      if (pair != null) {
        exception = new LocationAwareExternalSystemException(errMessage, rootCause, pair.first, pair.getSecond(), -1);
      }
    }
    if (exception == null) {
      if (errMessage.equals(rootCause.getMessage())) {
        exception = new ExternalSystemException(errMessage);
      }
      else {
        exception = new ExternalSystemException(errMessage, rootCause);
      }
    }
    exception.initCause(rootCause);
    return exception;
  }

  private static boolean isOldGradleVersion(@NotNull Throwable error) {
    if (error instanceof UnsupportedVersionException) {
      return true;
    }
    if (error instanceof UnsupportedMethodException) {
      String msg = error.getMessage();
      if (msg != null && msg.contains("GradleProject.getBuildScript")) {
        return true;
      }
    }
    if (error instanceof ClassNotFoundException) {
      String msg = error.getMessage();
      if (msg != null && msg.contains(ToolingModelBuilderRegistry.class.getName())) {
        return true;
      }
    }
    if (error instanceof RuntimeException) {
      String msg = error.getMessage();
      if (msg != null && msg.startsWith(UNSUPPORTED_GRADLE_VERSION_ERROR)) {
        return true;
      }
    }
    return false;
  }

  // The default implementation in IDEA only retrieves the location in build.gradle files. This implementation also handle location in
  // settings.gradle file.
  @Override
  @Nullable
  public String getLocationFrom(@NotNull Throwable error) {
    String errorToString = error.toString();
    if (errorToString.contains("LocationAwareException")) {
      // LocationAwareException is never passed, but converted into a PlaceholderException that has the toString value of the original
      // LocationAwareException.
      String location = error.getMessage();
      if (location != null && (location.startsWith("Build file '") || location.startsWith("Settings file '"))) {
        // Only the first line contains the location of the error. Discard the rest.
        String[] lines = splitByLines(location);
        return lines.length > 0 ? lines[0] : null;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public ExternalSystemException createUserFriendlyError(@NotNull String msg, @Nullable String location, @NotNull String... quickFixes) {
    if (isNotEmpty(location)) {
      Pair<String, Integer> pair = getErrorLocation(location);
      if (pair != null) {
        return new LocationAwareExternalSystemException(msg, pair.first, pair.getSecond(), quickFixes);
      }
    }
    return new ExternalSystemException(msg, null, quickFixes);
  }

  @VisibleForTesting
  @Nullable
  static Pair<String, Integer> getErrorLocation(@NotNull String location) {
    Matcher matcher = ERROR_LOCATION_PATTERN.matcher(location);
    if (matcher.matches()) {
      String filePath = matcher.group(1);
      int line = -1;
      String lineAsText = matcher.group(3);
      if (lineAsText != null) {
        try {
          line = Integer.parseInt(lineAsText);
        }
        catch (NumberFormatException e) {
          // ignored.
        }
      }
      return Pair.create(filePath, line);
    }
    return null;
  }
}
