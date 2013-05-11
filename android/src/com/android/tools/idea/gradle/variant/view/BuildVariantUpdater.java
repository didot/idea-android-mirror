/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.variant.view;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.ContentRootModuleCustomizer;
import com.android.tools.idea.gradle.customizer.DependenciesModuleCustomizer;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.android.tools.idea.gradle.util.Facets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * Updates the contents/settings of a module when a build variant changes.
 */
class BuildVariantUpdater {
  private static final Logger LOG = Logger.getInstance(BuildVariantUpdater.class);

  private final ModuleCustomizer[] myModuleCustomizers = {new ContentRootModuleCustomizer(), new DependenciesModuleCustomizer()};

  void updateModule(@NotNull final Project project, @NotNull final String moduleName, @NotNull final String buildVariantName) {
    ExternalSystemApiUtil.executeProjectChangeAction(project, GradleConstants.SYSTEM_ID, project, true, new Runnable() {
      @Override
      public void run() {
        doUpdate(project, moduleName, buildVariantName);
      }
    });
  }

  private void doUpdate(@NotNull Project project, @NotNull String moduleName, @NotNull String buildVariantName) {
    Module moduleToUpdate = findModule(project, moduleName);
    if (moduleToUpdate == null) {
      LOG.warn(String.format("Unable to find module with name '%1$s' in project '%2$s", moduleName, project.getName()));
      return;
    }
    AndroidFacet facet = Facets.getFirstFacet(moduleToUpdate, AndroidFacet.ID);
    if (facet == null) {
      LOG.warn(String.format("Unable to find 'Android' facet in module '%1$s', project '%2$s'", moduleName, project.getName()));
      return;
    }
    final IdeaAndroidProject androidProject = facet.getIdeaAndroidProject();
    if (androidProject == null) {
      LOG.warn(String.format("Unable to find AndroidProject for module '%1$s', project '%2$s'", moduleName, project.getName()));
      return;
    }
    androidProject.setSelectedVariantName(buildVariantName);
    facet.syncSelectedVariant();

    for (ModuleCustomizer customizer : myModuleCustomizers) {
      customizer.customizeModule(moduleToUpdate, project, androidProject);
    }

    // We changed the way we build projects: now we build only the selected variant. If user changes variant, we need to rebuild the project
    // since the generated sources may not be there.
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      CompilerManager.getInstance(project).make(new CompileStatusNotification() {
        @Override
        public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
          VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath(androidProject.getRootDirPath());
          if (rootDir != null && rootDir.isDirectory()) {
            rootDir.refresh(true, true);
          }
        }
      });
    }
  }

  @Nullable
  private static Module findModule(@NotNull Project project, @NotNull String moduleName) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      if (moduleName.equals(module.getName())) {
        return module;
      }
    }
    return null;
  }
}
