/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SyncActionOptions implements Serializable {
  @Nullable private SelectedVariants mySelectedVariants;
  @Nullable private Collection<String> myCachedLibraries;
  @Nullable private String myModuleIdWithVariantSwitched;
  private boolean mySingleVariantSyncEnabled;

  public boolean isSingleVariantSyncEnabled() {
    return mySingleVariantSyncEnabled;
  }

  public void setSingleVariantSyncEnabled(boolean singleVariantSyncEnabled) {
    mySingleVariantSyncEnabled = singleVariantSyncEnabled;
  }

  @Nullable
  public SelectedVariants getSelectedVariants() {
    return mySelectedVariants;
  }

  public void setSelectedVariants(@Nullable SelectedVariants selectedVariants) {
    mySelectedVariants = selectedVariants;
  }

  @NotNull
  public Collection<String> getCachedLibraries() {
    return myCachedLibraries == null ? Collections.emptySet() : myCachedLibraries;
  }

  public void setCachedLibraries(@Nullable Collection<String> cached) {
    myCachedLibraries = cached;
  }

  @Nullable
  public String getModuleIdWithVariantSwitched() {
    return myModuleIdWithVariantSwitched;
  }

  public void setModuleIdWithVariantSwitched(@Nullable String moduleId) {
    myModuleIdWithVariantSwitched = moduleId;
  }
}