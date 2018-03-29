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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsProject;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PsAllModulesFakeModule extends PsModule {
  public PsAllModulesFakeModule(@NotNull PsProject parent) {
    super(parent, "<All Modules>");
  }

  @Override
  public boolean isModified() {
    return getParent().isModified();
  }

  @Override
  public void applyChanges() {
    getProject().applyChanges();
  }

  private PsProject getProject() {
    return getParent();
  }

  @NotNull
  @Override
  public List<String> getConfigurations() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addLibraryDependency(@NotNull String library, @NotNull List<String> scopesNames) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addModuleDependency(@NotNull String modulePath, @NotNull List<String> scopesNames) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setLibraryDependencyVersion(@NotNull PsArtifactDependencySpec spec,
                                          @NotNull String configurationName,
                                          @NotNull String newVersion) {
    throw new UnsupportedOperationException();
  }
}
