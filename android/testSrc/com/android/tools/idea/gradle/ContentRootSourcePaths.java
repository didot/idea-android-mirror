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
package com.android.tools.idea.gradle;

import com.android.build.gradle.model.ProductFlavorContainer;
import com.android.build.gradle.model.Variant;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.model.android.AndroidProjectStub;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Verifies that the source paths of a {@link ContentRootData} are correct.
 */
public class ContentRootSourcePaths {
  @NotNull private final Map<ExternalSystemSourceType, List<String>> myDirectoryPathsBySourceType = Maps.newHashMap();

  public ContentRootSourcePaths() {
    add(ExternalSystemSourceType.SOURCE, ExternalSystemSourceType.TEST, ExternalSystemSourceType.EXCLUDED);
  }

  private void add(@NotNull ExternalSystemSourceType...sourceTypes) {
    for (ExternalSystemSourceType sourceType : sourceTypes) {
      myDirectoryPathsBySourceType.put(sourceType, new ArrayList<String>());
    }
  }

  /**
   * Stores the expected paths of all the source and test directories in the given {@code AndroidProject}.
   *
   * @param androidProject the given {@code AndroidProject}.
   */
  public void storeExpectedSourcePaths(@NotNull AndroidProjectStub androidProject) {
    addSourceDirectoryPaths(androidProject.getFirstVariant());
    addSourceDirectoryPaths(androidProject.getDefaultConfig());
    for (ProductFlavorContainer flavor : androidProject.getProductFlavors().values()) {
      addSourceDirectoryPaths(flavor);
    }
  }

  private void addSourceDirectoryPaths(@NotNull Variant variant) {
    addSourceDirectoryPaths(ExternalSystemSourceType.SOURCE, variant.getGeneratedResourceFolders());
    addSourceDirectoryPaths(ExternalSystemSourceType.SOURCE, variant.getGeneratedSourceFolders());
    addSourceDirectoryPaths(ExternalSystemSourceType.TEST, variant.getGeneratedTestResourceFolders());
    addSourceDirectoryPaths(ExternalSystemSourceType.TEST, variant.getGeneratedTestSourceFolders());
  }

  private void addSourceDirectoryPaths(@NotNull ProductFlavorContainer productFlavor) {
    addSourceDirectoryPaths(ExternalSystemSourceType.SOURCE, productFlavor.getSourceProvider());
    addSourceDirectoryPaths(ExternalSystemSourceType.TEST, productFlavor.getTestSourceProvider());
  }

  private void addSourceDirectoryPaths(@NotNull ExternalSystemSourceType sourceType, @NotNull SourceProvider sourceProvider) {
    addSourceDirectoryPaths(sourceType, sourceProvider.getAidlDirectories());
    addSourceDirectoryPaths(sourceType, sourceProvider.getAssetsDirectories());
    addSourceDirectoryPaths(sourceType, sourceProvider.getJavaDirectories());
    addSourceDirectoryPaths(sourceType, sourceProvider.getJniDirectories());
    addSourceDirectoryPaths(sourceType, sourceProvider.getRenderscriptDirectories());
    addSourceDirectoryPaths(sourceType, sourceProvider.getResDirectories());
    addSourceDirectoryPaths(sourceType, sourceProvider.getResourcesDirectories());
  }

  private void addSourceDirectoryPaths(@NotNull ExternalSystemSourceType sourceType, @Nullable Iterable<File> sourceDirectories) {
    if (sourceDirectories == null) {
      return;
    }
    List<String> paths = myDirectoryPathsBySourceType.get(sourceType);
    for (File directory : sourceDirectories) {
      paths.add(directory.getAbsolutePath());
    }
    Collections.sort(paths);
  }

  public void assertCorrectSourceDirectoryPaths(@NotNull ContentRootData contentRootData) {
    assertCorrectSourceDirectoryPaths(contentRootData, ExternalSystemSourceType.SOURCE);
    assertCorrectSourceDirectoryPaths(contentRootData, ExternalSystemSourceType.TEST);
  }

  private void assertCorrectSourceDirectoryPaths(@NotNull ContentRootData contentRootData, @NotNull ExternalSystemSourceType sourceType) {
    List<String> expectedPaths = myDirectoryPathsBySourceType.get(sourceType);
    String msg = String.format("Source paths (%s)", sourceType.toString().toLowerCase());
    Assert.assertEquals(msg, expectedPaths, getSourcePaths(contentRootData, sourceType));
  }

  @NotNull
  private static List<String> getSourcePaths(@NotNull ContentRootData contentRootData, @NotNull ExternalSystemSourceType sourceType) {
    List<String> sourcePaths = Lists.newArrayList(contentRootData.getPaths(sourceType));
    Collections.sort(sourcePaths);
    return sourcePaths;
  }
}
