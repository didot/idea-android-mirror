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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.repositories.JCenterDefaultRepositoryModel;
import com.android.tools.idea.gradle.dsl.model.repositories.MavenCentralRepositoryModel;
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoryModel;
import com.android.tools.idea.gradle.structure.configurables.ui.ArtifactRepositorySearchForm;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.repositories.search.AndroidSdkRepository;
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepository;
import com.android.tools.idea.gradle.structure.model.repositories.search.JCenterRepository;
import com.android.tools.idea.gradle.structure.model.repositories.search.MavenCentralRepository;
import com.google.common.collect.Lists;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class AddArtifactDependencyDialog extends DialogWrapper {
  @Nullable private final PsModule myModule;

  private JPanel myPanel;
  private ArtifactRepositorySearchForm mySearchForm;

  public AddArtifactDependencyDialog(@NotNull PsModule module) {
    super(module.getParent().getResolvedModel());
    myModule = module;
    setUp();
  }

  public AddArtifactDependencyDialog(@NotNull PsProject project) {
    super(project.getResolvedModel());
    myModule = null;
    setUp();
  }

  private void setUp() {
    setTitle("Add Artifact Dependency");
    init();
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    if (myPanel == null) {
      myPanel = new JPanel(new BorderLayout());
      setUpUI();
    }
    return myPanel;
  }

  @Override
  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return mySearchForm != null ? mySearchForm.getPreferredFocusedComponent() : super.getPreferredFocusedComponent();
  }

  private void setUpUI() {
    List<ArtifactRepository> repositories = Lists.newArrayList();
    if (myModule != null) {
      GradleBuildModel parsedModel = myModule.getParsedModel();
      if (parsedModel != null) {
        for (RepositoryModel repositoryModel : parsedModel.repositories().repositories()) {
          if (repositoryModel instanceof JCenterDefaultRepositoryModel) {
            repositories.add(new JCenterRepository());
            continue;
          }
          if (repositoryModel instanceof MavenCentralRepositoryModel) {
            repositories.add(new MavenCentralRepository());
          }
        }
      }
    }
    else {
      // Fall back to jcenter
      repositories.add(new JCenterRepository());
    }

    AndroidProject androidProject = null;
    if (myModule instanceof PsAndroidModule) {
      AndroidGradleModel gradleModel = ((PsAndroidModule)myModule).getGradleModel();
      androidProject = gradleModel.getAndroidProject();
    }
    repositories.add(new AndroidSdkRepository(androidProject));

    mySearchForm = new ArtifactRepositorySearchForm(repositories);
    myPanel.add(mySearchForm.getPanel(), BorderLayout.CENTER);
  }
}
