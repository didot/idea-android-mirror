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
package com.android.tools.idea.wizard;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.templates.TemplateUtils;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.android.SdkConstants.FN_BUILD_GRADLE;

/**
 * {@linkplain NewModuleWizard} guides the user through adding a new module to an existing project. It has a template-based flow and as the
 * first step of the wizard allows the user to choose a template which will guide the rest of the wizard flow.
 */
public class NewModuleWizard extends TemplateWizard {
  protected TemplateWizardModuleBuilder myModuleBuilder;

  public NewModuleWizard(@Nullable Project project) {
    super("New Module", project);
    Window window = getWindow();
    // Allow creation in headless mode for tests
    if (window != null) {
      getWindow().setMinimumSize(new Dimension(1000, 640));
    } else {
      // We should always have a window unless we're in test mode
      ApplicationManager.getApplication().isUnitTestMode();
    }
    init();
  }

  @Override
  protected void init() {
    boolean haveGlobalRepository = false;
    VirtualFile buildGradle = myProject.getBaseDir().findChild(FN_BUILD_GRADLE);
    if (buildGradle != null) {
      String contents = TemplateUtils.readTextFile(myProject, buildGradle);
      if (contents != null) {
        haveGlobalRepository = contents.contains("repositories") && contents.contains(SdkConstants.GRADLE_PLUGIN_NAME);
      }
    }

    myModuleBuilder = new TemplateWizardModuleBuilder(null, null, myProject, AndroidIcons.Wizards.NewModuleSidePanel,
                                                      mySteps, getDisposable(), false) {
      @Override
      public void update() {
        super.update();
        NewModuleWizard.this.update();
      }
    };
    myModuleBuilder.setupModuleBuilder(haveGlobalRepository);
    super.init();
  }

  @Override
  protected boolean isStepVisible(ModuleWizardStep page) {
    return myModuleBuilder.isStepVisible(page);
  }

  @Override
  public void update() {
    if (myModuleBuilder != null && myModuleBuilder.updateWizardSteps()) {
      super.update();
    }
  }

  public void createModule(final boolean performGradleSyncAfter) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myModuleBuilder.createModule(performGradleSyncAfter);
      }
    });
  }

  @Override
  @VisibleForTesting
  protected void doNextAction() {
    super.doNextAction();
  }

  @Override
  @VisibleForTesting
  protected void doPreviousAction() {
    super.doPreviousAction();
  }
}
