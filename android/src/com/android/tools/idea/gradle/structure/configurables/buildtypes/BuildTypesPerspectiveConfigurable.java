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
package com.android.tools.idea.gradle.structure.configurables.buildtypes;

import com.android.tools.idea.gradle.structure.configurables.BasePerspectiveConfigurable;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleEditor;
import com.android.tools.idea.gradle.structure.model.PsdModuleEditor;
import com.android.tools.idea.gradle.structure.model.PsdProjectEditor;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuildTypesPerspectiveConfigurable extends BasePerspectiveConfigurable {
  public BuildTypesPerspectiveConfigurable(@NotNull PsdProjectEditor projectEditor) {
    super(projectEditor);
  }

  @Override
  @Nullable
  protected NamedConfigurable<? extends PsdModuleEditor> getConfigurable(@NotNull PsdModuleEditor moduleEditor) {
    if (moduleEditor instanceof PsdAndroidModuleEditor) {
      return new BuildTypesConfigurable((PsdAndroidModuleEditor)moduleEditor);
    }
    return null;
  }

  @Override
  public void dispose() {

  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    // TODO implement.
    return ActionCallback.DONE;
  }

  @Override
  public void queryPlace(@NotNull Place place) {

  }

  @Override
  @NotNull
  public String getId() {
    return "android.psd.buildtypes";
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Build Types";
  }
}
