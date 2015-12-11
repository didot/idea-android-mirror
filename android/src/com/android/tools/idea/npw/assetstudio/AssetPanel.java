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
package com.android.tools.idea.npw.assetstudio;

import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionListener;

/**
 * Simple interface for UI panels that provide a reference to some {@link BaseAsset} and can notify
 * listeners when it has changed.
 */
interface AssetPanel {
  @NotNull
  BaseAsset getAsset();

  /**
   * Register a listener which will be fired anytime the underlying asset has changed.
   */
  void addActionListener(@NotNull ActionListener l);
}
