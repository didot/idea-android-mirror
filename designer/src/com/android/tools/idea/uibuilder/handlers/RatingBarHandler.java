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
package com.android.tools.idea.uibuilder.handlers;

import static com.android.SdkConstants.ATTR_IS_INDICATOR;
import static com.android.SdkConstants.ATTR_NUM_STARS;
import static com.android.SdkConstants.ATTR_RATING;
import static com.android.SdkConstants.ATTR_STEP_SIZE;
import static com.android.SdkConstants.ATTR_STYLE;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class RatingBarHandler extends ProgressBarHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_STYLE,
      ATTR_NUM_STARS,
      ATTR_RATING,
      ATTR_STEP_SIZE,
      ATTR_IS_INDICATOR);
  }
}
