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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.RenderSession;
import org.jetbrains.annotations.NotNull;

/**
 * Resulting information of executing {@link RenderTask#triggerTouchEvent(RenderSession.TouchEventType, int, int, long)}.
 */
public class TouchEventResult {
  private final long myDurationMs;

  protected TouchEventResult(long durationMs) {
    myDurationMs = durationMs;
  }

  @NotNull
  public static TouchEventResult create(long durationMs) {
    return new TouchEventResult(durationMs);
  }

  public long getDurationMs() {
    return myDurationMs;
  }
}
