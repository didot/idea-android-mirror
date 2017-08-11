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
package com.android.tools.idea.sdk;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * Utility methods for SDK paths.
 */
public class SdkPaths {
  private SdkPaths() {
  }

  /**
   * Indicates whether the given path belongs to a valid Android SDK.
   *
   * @param sdkPath              the given path.
   * @param includePathInMessage indicates whether the given path should be included in the result message.
   * @return the validation result.
   */
  @NotNull
  public static Validator.Result validateAndroidSdk(@Nullable File sdkPath, boolean includePathInMessage) {
    return validatedSdkPath(sdkPath, "SDK", false, includePathInMessage);
  }

  @VisibleForTesting
  static Validator.Result validateAndroidNdk(@Nullable File ndkPath, boolean includePathInMessage, @NotNull Validator<File> validator) {
    if (ndkPath != null) {
      Validator.Result wizardValidationResult = validator.validate(ndkPath);
      if (!wizardValidationResult.isOk()) {
        return wizardValidationResult;
      }
    }
    Validator.Result validationResult = validatedSdkPath(ndkPath, "NDK", false, includePathInMessage);
    if (validationResult.isOk() && ndkPath != null) {
      File toolchainsDirPath = new File(ndkPath, "toolchains");
      if (!toolchainsDirPath.isDirectory()) {
        String message;
        if (includePathInMessage) {
          message = String.format("The NDK at\n'%1$s'\ndoes not contain any toolchains.", ndkPath.getPath());
        }
        else {
          message = "NDK does not contain any toolchains.";
        }
        return new Validator.Result(Validator.Severity.ERROR, message);
      }
    }
    return validationResult;
  }

  /**
   * Indicates whether the given path belongs to a valid Android NDK.
   *
   * @param ndkPath              the given path.
   * @param includePathInMessage indicates whether the given path should be included in the result message.
   * @return the validation result.
   */
  @NotNull
  public static Validator.Result validateAndroidNdk(@Nullable File ndkPath, boolean includePathInMessage) {
    return validateAndroidNdk(ndkPath, includePathInMessage, new PathValidator.Builder().withCommonRules().build("Android NDK location"));
  }

  @NotNull
  private static Validator.Result validatedSdkPath(@Nullable File sdkPath,
                                                   @NotNull String sdkName,
                                                   boolean checkForWritable,
                                                   boolean includePathInMessage) {
    if (sdkPath == null) {
      return new Validator.Result(Validator.Severity.ERROR, "");
    }

    String cause = null;
    if (!sdkPath.isDirectory()) {
      cause = "does not belong to a directory.";
    }
    else if (!sdkPath.canRead()) {
      cause = "is not readable.";
    }
    else if (checkForWritable && !sdkPath.canWrite()) {
      cause = "is not writable.";
    }
    if (isNotEmpty(cause)) {
      String message;
      if (includePathInMessage) {
        message = String.format("The path\n'%1$s'\n%2$s", sdkPath.getPath(), cause);
      }
      else {
        message = String.format("The path %1$s", cause);
      }
      return new Validator.Result(Validator.Severity.ERROR, message);
    }

    File platformsDirPath = new File(sdkPath, SdkConstants.FD_PLATFORMS);
    if (!platformsDirPath.isDirectory()) {
      String message;
      if (includePathInMessage) {
        message = String.format("The %1$s at\n'%2$s'\ndoes not contain any platforms.", sdkName, sdkPath.getPath());
      }
      else {
        message = String.format("%1$s does not contain any platforms.", sdkName);
      }
      return new Validator.Result(Validator.Severity.ERROR, message);
    }

    return Validator.Result.OK;
  }
}
