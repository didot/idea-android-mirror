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
package com.android.tools.idea.gradle.structure.model.android;

import com.android.builder.model.ProductFlavor;
import com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModel;
import com.android.tools.idea.gradle.structure.model.PsdChildEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsdProductFlavorEditor extends PsdChildEditor {
  @Nullable private final ProductFlavor myGradleModel;
  @Nullable private final ProductFlavorModel myParsedModel;

  private String myName = "";

  PsdProductFlavorEditor(@NotNull PsdAndroidModuleEditor parent,
                         @Nullable ProductFlavor gradleModel,
                         @Nullable ProductFlavorModel parsedModel) {
    super(parent);
    myGradleModel = gradleModel;
    myParsedModel = parsedModel;
    if (gradleModel != null) {
      myName = gradleModel.getName();
    }
    else if (parsedModel != null) {
      myName = parsedModel.name();
    }
  }

  @Override
  @NotNull
  public PsdAndroidModuleEditor getParent() {
    return (PsdAndroidModuleEditor)super.getParent();
  }

  @Override
  public boolean isEditable() {
    return myParsedModel != null;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public void setName(@NotNull String name) {
    myName = name;
    setModified(true);
  }

  @Override
  public String toString() {
    return myName;
  }
}
