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
package com.android.tools.idea.gradle.facet;

import com.intellij.ProjectTopics;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.impl.FacetUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Android-Gradle facet.
 *
 * </p>This facet is set to IDEA modules that have been imported from an Android-Gradle project. The purpose of this facet is to identify
 * these modules and build them with Gradle exclusively.
 */
public class AndroidGradleFacet extends Facet<AndroidGradleFacetConfiguration> {
  private static final Logger LOG = Logger.getInstance(AndroidGradleFacet.class);

  @NotNull public static final FacetTypeId<AndroidGradleFacet> ID = new FacetTypeId<AndroidGradleFacet>("android-gradle");

  @NonNls public static final String NAME = "Android-Gradle";

  @SuppressWarnings("ConstantConditions")
  public AndroidGradleFacet(@NotNull Module module,
                            @NotNull String name,
                            @NotNull AndroidGradleFacetConfiguration configuration) {
    super(getFacetType(), module, name, configuration, null);
  }

  @NotNull
  public static AndroidGradleFacetType getFacetType() {
    return (AndroidGradleFacetType)FacetTypeRegistry.getInstance().findFacetType(ID);
  }

  @Override
  public void initFacet() {
    MessageBusConnection connection = getModule().getMessageBus().connect(this);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (!isDisposed()) {
              PsiDocumentManager.getInstance(getModule().getProject()).commitAllDocuments();
              storeProjectPath();
            }
          }
        });
      }
    });
    storeProjectPath();
  }

  private void storeProjectPath() {
    AndroidGradleFacetConfiguration config = getConfiguration();
    // Store the project path. JPS Builder needs this information to invoke Gradle.
    config.PROJECT_ABSOLUTE_PATH = getModule().getProject().getBasePath();
    try {
      FacetUtil.saveFacetConfiguration(config);
    }
    catch (WriteExternalException e) {
      LOG.error("Unable to save contents of 'Android-Gradle' facet", e);
    }
  }
}
