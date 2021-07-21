/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import com.intellij.openapi.diagnostic.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Resolution {
  private static final @NotNull Pattern PATTERN = Pattern.compile("Physical size: (\\d+)x(\\d+)");

  private final int myWidth;
  private final int myHeight;

  Resolution(int width, int height) {
    myWidth = width;
    myHeight = height;
  }

  static @Nullable Resolution newResolution(@NotNull String string) {
    Matcher matcher = PATTERN.matcher(string);

    if (!matcher.matches()) {
      Logger.getInstance(Resolution.class).warn(string);
      return null;
    }

    return new Resolution(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
  }

  int getWidth() {
    return myWidth;
  }

  int getHeight() {
    return myHeight;
  }

  @Override
  public int hashCode() {
    return 31 * myWidth + myHeight;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof Resolution)) {
      return false;
    }

    Resolution resolution = (Resolution)object;
    return myWidth == resolution.myWidth && myHeight == resolution.myHeight;
  }

  @Override
  public @NotNull String toString() {
    return myWidth + " × " + myHeight;
  }
}