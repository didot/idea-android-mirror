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

import com.android.tools.idea.uibuilder.api.ViewHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Handler for the {@code <DatePicker>} widget.
 */
@SuppressWarnings("unused") // Loaded by reflection
public class DatePickerHandler extends ViewHandler {
  // Note: This handler is derived from ViewHandler to avoid being treated as a {@code FrameLayout}.

  @Override
  public double getPreviewScale(@NotNull String tagName) {
    return 0.4;
  }
}
