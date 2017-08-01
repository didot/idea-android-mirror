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

import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.npw.template.TemplateValueInjector;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.wizard.WizardConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;
import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.WizardConstants.MODULE_TEMPLATE_NAME;

/**
 * Value object which holds the current state of the wizard pages for the NewProjectWizard
 * Deprecated by {@link TemplateValueInjector}
 */
@Deprecated
public class NewProjectWizardState extends NewModuleWizardState {
  private static final String APPLICATION_NAME = "My Application";

  private final Template myProjectTemplate;

  public NewProjectWizardState() {
    this(Template.createFromName(CATEGORY_PROJECTS, MODULE_TEMPLATE_NAME));
  }

  public NewProjectWizardState(@NotNull Template moduleTemplate) {
    myHidden.remove(ATTR_PROJECT_LOCATION);
    myHidden.remove(ATTR_IS_LIBRARY_MODULE);
    myHidden.remove(ATTR_APP_TITLE);

    put(ATTR_IS_LIBRARY_MODULE, false);
    put(ATTR_IS_LAUNCHER, true);
    put(ATTR_PROJECT_LOCATION, WizardUtils.getProjectLocationParent().getPath());
    put(ATTR_APP_TITLE, APPLICATION_NAME);
    final int DEFAULT_MIN = SdkVersionInfo.LOWEST_ACTIVE_API;
    put(ATTR_MIN_API_LEVEL, DEFAULT_MIN);
    put(ATTR_MIN_API, Integer.toString(DEFAULT_MIN));
    myProjectTemplate = Template.createFromName(CATEGORY_PROJECTS, WizardConstants.PROJECT_TEMPLATE_NAME);
    myTemplate = moduleTemplate;
    setParameterDefaults();

    updateParameters();
  }

  @TestOnly
  public Template getProjectTemplate() {
    return myProjectTemplate;
  }
}
