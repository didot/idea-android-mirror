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
package com.android.tools.idea.gradle.structure.configurables.java.dependencies;

import com.android.tools.idea.gradle.structure.configurables.AbstractDependenciesConfigurable;
import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.dependencies.module.MainPanel;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JavaModuleDependenciesConfigurable extends AbstractDependenciesConfigurable<PsJavaModule> {
  private MainPanel myMainPanel;

  public JavaModuleDependenciesConfigurable(@NotNull PsJavaModule module,
                                            @NotNull PsContext context,
                                            @NotNull List<PsModule> extraModules) {
    super(module, context, extraModules);
  }

  @Override
  @NotNull
  public String getId() {
    return "module.dependencies." + getDisplayName();
  }

  @Override
  public MainPanel createOptionsPanel() {
    if (myMainPanel == null) {
      myMainPanel = new MainPanel(getModule(), getContext(), getExtraModules());
      myMainPanel.setHistory(getHistory());
    }
    return myMainPanel;
  }

  @Override
  public void disposeUIResources() {
    if (myMainPanel != null) {
      Disposer.dispose(myMainPanel);
    }
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    return createOptionsPanel().navigateTo(place, requestFocus);
  }

  @Override
  public void queryPlace(@NotNull Place place) {
    createOptionsPanel().queryPlace(place);
  }
}
