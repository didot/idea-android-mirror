/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.icon;

import org.jetbrains.annotations.NotNull;

/**
 * Categories of Android icon purposes - each type has its own rules for generating a final look
 * and feel.
 */
public enum AndroidAdaptiveIconType {
  /**
   * Launcher icon to be shown in the application list
   */
  ADAPTIVE("Adaptive Icons", "ic_launcher"),

  /**
   * Launcher icon to be shown in the application list
   */
  LAUNCHER_LEGACY("Launcher Icons (Legacy)", "ic_launcher"),

  /**
   * Icons shown in the action bar
   */
  ACTIONBAR("Action Bar and Tab Icons", "ic_action_%s"),

  /**
   * Icons shown in a notification message
   */
  NOTIFICATION("Notification Icons", "ic_stat_%s");

  @NotNull private final String myDisplayName;

  /**
   * Default asset name format, for use in generating a name for the final asset.
   */
  @NotNull private final String myDefaultNameFormat;

  AndroidAdaptiveIconType(@NotNull String displayName, @NotNull String defaultNameFormat) {
    myDisplayName = displayName;
    myDefaultNameFormat = defaultNameFormat;
  }

  @NotNull
  public static AndroidIconGenerator createIconGenerator(@NotNull AndroidAdaptiveIconType iconType, int minSdkVersion) {
    switch (iconType) {
      case ADAPTIVE:
        return new AndroidAdaptiveIconGenerator(minSdkVersion);
      case LAUNCHER_LEGACY:
        return new AndroidLauncherIconGenerator(minSdkVersion);
      case ACTIONBAR:
        return new AndroidActionBarIconGenerator(minSdkVersion);
      case NOTIFICATION:
        return new AndroidNotificationIconGenerator(minSdkVersion);
    }

    throw new IllegalArgumentException("Can't create generator for unexpected icon type: " + iconType);
  }

  @NotNull
  public String getDisplayName() {
    return myDisplayName;
  }

  /**
   * Convert a value like 'name' to the icon appropriate version, e.g. 'icon_stat_name' for
   * notification icons.
   */
  @NotNull
  public String toOutputName(@NotNull String baseName) {
    return String.format(myDefaultNameFormat, baseName);
  }

  @NotNull
  @Override
  public String toString() {
    return getDisplayName();
  }
}

