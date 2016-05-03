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
package com.android.tools.idea.gradle.structure.model.android;

import com.android.builder.model.Library;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependencyModel;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.android.tools.idea.gradle.structure.model.PsDependency.TextType.PLAIN_TEXT;
import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.util.PlatformIcons.LIBRARY_ICON;

public class PsAndroidLibraryDependency extends PsAndroidDependency implements PsLibraryDependency {
  @NotNull private final List<PsArtifactDependencySpec> myPomDependencies = Lists.newArrayList();
  @NotNull private final Set<String> myTransitiveDependencies = Sets.newHashSet();
  @NotNull private PsArtifactDependencySpec myResolvedSpec;

  @Nullable private final Library myResolvedModel;
  @Nullable private PsArtifactDependencySpec myDeclaredSpec;

  PsAndroidLibraryDependency(@NotNull PsAndroidModule parent,
                             @NotNull PsArtifactDependencySpec resolvedSpec,
                             @NotNull PsAndroidArtifact container,
                             @Nullable Library resolvedModel,
                             @Nullable ArtifactDependencyModel parsedModel) {
    super(parent, container, parsedModel);
    myResolvedSpec = resolvedSpec;
    myResolvedModel = resolvedModel;
    setDeclaredSpec(parsedModel);
  }

  @Override
  @Nullable
  public Library getResolvedModel() {
    return myResolvedModel;
  }

  void addTransitiveDependency(@NotNull String dependency) {
    myTransitiveDependencies.add(dependency);
  }

  @Override
  public void setDependenciesFromPomFile(@NotNull List<PsArtifactDependencySpec> pomDependencies) {
    myPomDependencies.clear();
    myPomDependencies.addAll(pomDependencies);
  }

  @Nullable
  private static PsArtifactDependencySpec createSpec(@Nullable ArtifactDependencyModel parsedModel) {
    if (parsedModel != null) {
      String compactNotation = parsedModel.compactNotation().value();
      return PsArtifactDependencySpec.create(compactNotation);
    }
    return null;
  }

  @NotNull
  public ImmutableCollection<PsAndroidDependency> getTransitiveDependencies() {
    PsAndroidModule module = getParent();

    ImmutableSet.Builder<PsAndroidDependency> transitive = ImmutableSet.builder();
    for (String dependency : myTransitiveDependencies) {
      PsAndroidDependency found = module.findLibraryDependency(dependency);
      if (found != null) {
        transitive.add(found);
      }
    }
    for (PsArtifactDependencySpec dependency : myPomDependencies) {
      PsAndroidLibraryDependency found = module.findLibraryDependency(dependency);
      if (found != null) {
        transitive.add(found);
      }
    }

    return transitive.build();
  }

  @NotNull
  public List<String> findRequestingModuleDependencies() {
    Set<String> moduleNames = Sets.newHashSet();
    findRequestingModuleDependencies(getParent(), moduleNames);
    if (moduleNames.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> sorted = Lists.newArrayList(moduleNames);
    Collections.sort(sorted);
    return sorted;
  }

  private void findRequestingModuleDependencies(@NotNull PsAndroidModule module, @NotNull Collection<String> found) {
    PsProject project = module.getParent();
    module.forEachModuleDependency(moduleDependency -> {
      String gradlePath = moduleDependency.getGradlePath();
      PsModule foundModule = project.findModuleByGradlePath(gradlePath);
      if (foundModule instanceof PsAndroidModule) {
        PsAndroidModule androidModule = (PsAndroidModule)foundModule;

        PsAndroidLibraryDependency libraryDependency = androidModule.findLibraryDependency(myResolvedSpec);
        if (libraryDependency != null && libraryDependency.isDeclared()) {
          found.add(androidModule.getName());
        }

        findRequestingModuleDependencies(androidModule, found);
      }
    });
  }

  @Override
  public void addParsedModel(@NotNull DependencyModel parsedModel) {
    assert parsedModel instanceof ArtifactDependencyModel;
    if (getParsedModels().isEmpty()) {
      myDeclaredSpec = PsArtifactDependencySpec.create((ArtifactDependencyModel)parsedModel);
    }
    super.addParsedModel(parsedModel);
  }

  @Override
  @Nullable
  public PsArtifactDependencySpec getDeclaredSpec() {
    return myDeclaredSpec;
  }

  @Override
  @NotNull
  public PsArtifactDependencySpec getResolvedSpec() {
    return myResolvedSpec;
  }

  @Override
  @NotNull
  public String getName() {
    return myResolvedSpec.name;
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return LIBRARY_ICON;
  }

  @Override
  @NotNull
  public String toText(@NotNull TextType type) {
    switch (type) {
      case PLAIN_TEXT:
        return myResolvedSpec.toString();
      case FOR_NAVIGATION:
        PsArtifactDependencySpec spec = myDeclaredSpec;
        if (spec == null) {
          spec = myResolvedSpec;
        }
        return spec.toString();
      default:
        return "";
    }
  }

  @Override
  public boolean hasPromotedVersion() {
    if (myResolvedSpec.version != null && myDeclaredSpec != null && myDeclaredSpec.version != null) {
      GradleVersion declaredVersion = GradleVersion.tryParse(myDeclaredSpec.version);
      return declaredVersion != null && declaredVersion.compareTo(myResolvedSpec.version) < 0;
    }
    return false;
  }

  @Override
  public void setVersion(@NotNull String version) {
    boolean modified = false;
    ArtifactDependencyModel reference = null;
    for (DependencyModel parsedDependency : getParsedModels()) {
      if (parsedDependency instanceof ArtifactDependencyModel) {
        ArtifactDependencyModel dependency = (ArtifactDependencyModel)parsedDependency;
        dependency.setVersion(version);
        if (reference == null) {
          reference = dependency;
        }
        modified = true;
      }
    }
    if (modified) {
      GradleVersion parsedVersion = GradleVersion.parse(version);
      String resolvedVersion = nullToEmpty(myResolvedSpec.version);
      if (parsedVersion.compareTo(resolvedVersion) != 0) {
        // Update the "resolved" spec with the new version
        myResolvedSpec = new PsArtifactDependencySpec(myResolvedSpec.name, myResolvedSpec.group, version);
      }
      setDeclaredSpec(reference);
      setModified(true);
      getParent().fireDependencyModifiedEvent(this);
    }
  }

  private void setDeclaredSpec(@Nullable ArtifactDependencyModel parsedModel) {
    myDeclaredSpec = createSpec(parsedModel);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PsAndroidLibraryDependency that = (PsAndroidLibraryDependency)o;
    return Objects.equals(myResolvedSpec, that.myResolvedSpec);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myDeclaredSpec);
  }

  @Override
  public String toString() {
    return toText(PLAIN_TEXT);
  }
}
