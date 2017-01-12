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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker.TestCompileType;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;

import java.util.ArrayList;
import java.util.List;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class BuildApkAction extends DumbAwareAction {
  private static final String ACTION_TEXT = "Build APK(s)";

  public BuildApkAction() {
    super(ACTION_TEXT);
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabledAndVisible(project != null && GradleProjectInfo.getInstance(project).isBuildWithGradle());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null && GradleProjectInfo.getInstance(project).isBuildWithGradle()) {
      GoToApkLocationTask task = null;

      List<Module> appModules = new ArrayList<>();

      for (Module module : ModuleManager.getInstance(project).getModules()) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null) {
          AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
          if (androidModel != null && androidModel.getProjectType() == PROJECT_TYPE_APP) {
            String assembleTaskName = facet.getProperties().ASSEMBLE_TASK_NAME;
            if (isNotEmpty(assembleTaskName)) {
              if (task == null) {
                task = new GoToApkLocationTask(ACTION_TEXT, module, null);
              }
              appModules.add(module);
            }
          }
        }
      }

      if (task != null && !appModules.isEmpty()) {
        GradleBuildInvoker gradleBuildInvoker = GradleBuildInvoker.getInstance(project);
        gradleBuildInvoker.add(task);
        gradleBuildInvoker.assemble(appModules.toArray(new Module[appModules.size()]), TestCompileType.NONE);
      }
    }
  }
}
