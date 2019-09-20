/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module.android;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.testFramework.JavaProjectTestCase;
import org.jetbrains.android.facet.AndroidFacet;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;

/**
 * Tests for {@link AndroidModuleCleanupStep}.
 */
public class AndroidModuleCleanupStepTest extends JavaProjectTestCase {
  private AndroidModuleCleanupStep myCleanupStep;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myCleanupStep = new AndroidModuleCleanupStep();
  }

  public void testCleanUpModule() {
    createAndAddAndroidFacet(myModule);
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    myCleanupStep.cleanUpModule(myModule, modelsProvider);

    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    assertNull(AndroidFacet.getInstance(myModule));
  }
}