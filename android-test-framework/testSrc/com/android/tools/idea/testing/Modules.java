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
package com.android.tools.idea.testing;


import static org.junit.Assert.assertNotNull;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;

public class Modules {
  @NotNull private final Project myProject;
  @NotNull private final ModuleNames myModuleNames;

  public Modules(@NotNull Project project) {
    myProject = project;
    myModuleNames = new ModuleNames(myProject);
  }

  @NotNull
  public Module getAppModule() {
    String qualifiedName = myProject.getName() + ".app";
    return getModule(qualifiedName);
  }

  @NotNull
  public Module getModule(@NotNull String qualifiedName) {
    String name = myModuleNames.transformIfNeeded(qualifiedName);
    Ref<Module> moduleRef = new Ref<>();
    ApplicationManager.getApplication().runReadAction(() -> {
      Module module = ModuleManager.getInstance(myProject).findModuleByName(name);
      moduleRef.set(module);
    });
    Module module = moduleRef.get();
    assertNotNull("Unable to find module with name '" + name + "' (" + qualifiedName + ")", module);
    return module;
  }

  public boolean hasModule(@NotNull String qualifiedName) {
    String name = myModuleNames.transformIfNeeded(qualifiedName);
    return ApplicationManager.getApplication().runReadAction(
      (Computable<Boolean>)() -> ModuleManager.getInstance(myProject).findModuleByName(name) != null);
  }
}
