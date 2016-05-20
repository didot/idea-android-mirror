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
package com.android.tools.idea.npw;

import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.google.common.collect.Lists;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Processor;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.HashSet;
import icons.AndroidIcons;
import org.jetbrains.android.AndroidTestCase;

import javax.swing.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;

/**
 * Tests for {@link NewModuleWizard}
 */
public class NewModuleWizardTest extends AndroidTestCase {

  @Override
  protected void collectAllowedRoots(final List<String> roots) throws IOException {
    JavaSdk javaSdk = JavaSdk.getInstance();
    final List<String> jdkPaths = Lists.newArrayList(javaSdk.suggestHomePaths());
    jdkPaths.add(SystemProperties.getJavaHome());
    roots.addAll(jdkPaths);

    for (final String jdkPath : jdkPaths) {
      FileUtil.processFilesRecursively(new File(jdkPath), new Processor<File>() {
        @Override
        public boolean process(File file) {
          try {
            String path = file.getCanonicalPath();
            if (!FileUtil.isAncestor(jdkPath, path, false)) {
              roots.add(path);
            }
          }
          catch (IOException ignore) { }
          return true;
        }
      });
    }
  }

  public void testBuildChooseModuleStep() throws Exception {
    File otherTemplateDir = new File(TemplateManager.getTemplateRootFolder(), Template.CATEGORY_PROJECTS);
    List<String> templateDirFiles = Arrays.asList(otherTemplateDir.list(new FilenameFilter() {
      @Override
      public boolean accept(File file, String name) {
        return !name.startsWith(".");
      }
    }));
    int expectedCount = templateDirFiles.size() - 3 + 3 /* Less "ImportExistingProject", "NewAndroidModule" and "NewAndroidProject"
                                                           added project, library, Gradle import */;
    NewModuleWizardPathFactory[] extensions = Extensions.getExtensions(NewModuleWizardPathFactory.EP_NAME);
    for (NewModuleWizardPathFactory factory : extensions) {
      Collection<WizardPath> paths = factory.createWizardPaths(new NewModuleWizardState(), new TemplateWizardStep.UpdateListener() {
        @Override
        public void update() {

        }
      }, getProject(), null, getTestRootDisposable());
      for (WizardPath path : paths) {
        expectedCount += path.getBuiltInTemplates().size();
      }
    }

    ArrayList<ModuleWizardStep> steps = Lists.newArrayList();
    TemplateWizardModuleBuilder myModuleBuilder = new TemplateWizardModuleBuilder(null, null, myModule.getProject(),
                                                                                  AndroidIcons.Wizards.NewModuleSidePanel,
                                                                                  steps, getTestRootDisposable(), false) {
      @Override
      public void update() {
        // Do nothing
      }
    };

    assertInstanceOf(steps.get(0), ChooseTemplateStep.class);
    ChooseTemplateStep chooseTemplateStep = (ChooseTemplateStep)steps.get(0);

    // Make sure we've got an actual object
    assertNotNull(chooseTemplateStep);

    DefaultListModel templateMetadatas = (DefaultListModel)chooseTemplateStep.myTemplateList.getModel();
    Set<String> templateNames = new HashSet<>(templateMetadatas.getSize());
    for (Object o : templateMetadatas.toArray()) {
      templateNames.add(o.toString());
    }

    // Make sure we have the right number of choices
    assertEquals(expectedCount, templateMetadatas.getSize());

    // Ensure we're offering NewApplication and NewLibrary
    assertContainsElements(templateNames, TemplateWizardModuleBuilder.APP_TEMPLATE_NAME, TemplateWizardModuleBuilder.LIB_TEMPLATE_NAME);

    // Ensure we're not offering duplicate elements in the list
    assertEquals(templateNames.size(), templateMetadatas.getSize());
  }
}
