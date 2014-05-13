/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.tools.gradle.eclipse.GradleImport;
import com.android.tools.idea.gradle.eclipse.AdtImportBuilder;
import com.android.tools.idea.gradle.eclipse.AdtImportProvider;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Creates new project modules from existing Android Eclipse projects.
 */
public final class AdtModuleImporter extends ModuleImporter {
  private final WizardContext myContext;
  private List<ModuleWizardStep> myWizardSteps;

  public AdtModuleImporter(WizardContext context) {
    super();
    myContext = context;
  }

  public static boolean isAdtProjectLocation(VirtualFile importSource) {
    VirtualFile target = ProjectImportUtil.findImportTarget(importSource);
    if (target == null) {
      return false;
    }
    VirtualFile targetDir = target.isDirectory() ? target : target.getParent();
    File targetDirFile = VfsUtilCore.virtualToIoFile(targetDir);
    return GradleImport.isAdtProjectDir(targetDirFile) && targetDir.findChild(SdkConstants.FN_BUILD_GRADLE) == null;
  }

  @Override
  public List<? extends ModuleWizardStep> createWizardSteps() {
    AdtImportProvider provider = new AdtImportProvider(false);
    myContext.setProjectBuilder(provider.getBuilder());
    ModuleWizardStep[] adtImportSteps = provider.createSteps(myContext);
    myWizardSteps = Lists.newArrayList(adtImportSteps);
    return myWizardSteps;
  }

  @Override
  public void importProjects(Map<String, VirtualFile> projects) {
    AdtImportBuilder builder = AdtImportBuilder.getBuilder(myContext);
    Project project = myContext.getProject();
    assert builder != null && project != null;
    GradleImport importer = builder.getImporter();
    assert importer != null;
    importer.setModulesToImport(Maps.transformValues(projects, new Function<VirtualFile, File>() {
      @Override
      public File apply(VirtualFile input) {
        return VfsUtilCore.virtualToIoFile(input);
      }
    }));
    if (builder.validate(null, project)) {
      builder.commit(project, null, ModulesProvider.EMPTY_MODULES_PROVIDER, null);
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        project.save();
      }
      builder.cleanup();
    }
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean canImport(VirtualFile importSource) {
    return isAdtProjectLocation(importSource);
  }

  @Override
  public Set<ModuleToImport> findModules(VirtualFile importSource) throws IOException {
    AdtImportBuilder builder = (AdtImportBuilder)myContext.getProjectBuilder();
    assert builder != null;
    builder.setSelectedProject(VfsUtilCore.virtualToIoFile(importSource));
    GradleImport gradleImport = new GradleImport();
    gradleImport.importProjects(Collections.singletonList(VfsUtilCore.virtualToIoFile(importSource)));
    Map<String, File> adtProjects = gradleImport.getDetectedModuleLocations();
    Function<Object, Iterable<String>> resolver = Functions.<Iterable<String>>constant(Collections.<String>emptySet());
    Set<ModuleToImport> modules = Sets.newHashSet();
    for (Map.Entry<String, File> entry : adtProjects.entrySet()) {
      VirtualFile location = VfsUtil.findFileByIoFile(entry.getValue(), false);
      modules.add(new ModuleToImport(entry.getKey(), location, resolver));
    }
    return modules;
  }

  @Override
  public boolean isStepVisible(ModuleWizardStep step) {
    return myWizardSteps.contains(step);
  }

}
