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
package com.android.tools.idea.gradle.structure.model.pom;

import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.findPomForLibrary;
import static com.intellij.openapi.util.JDOMUtil.loadDocument;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.util.text.StringUtil.nullize;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class MavenPoms {
  private static final Logger LOG = Logger.getInstance(MavenPoms.class);

  private MavenPoms() {
  }

  @NotNull
  public static List<ArtifactDependencySpec> findDependenciesInPomFile(@NotNull File libraryPath) {
    VirtualFile pomFile = findPomForLibrary(libraryPath);
    if (pomFile == null) {
      return Collections.emptyList();
    }
    List<ArtifactDependencySpec> dependencies = Lists.newArrayList();
    try {
      Document document = loadDocument(virtualToIoFile(pomFile));
      Element rootElement = document.getRootElement();
      if (rootElement != null) {
        Element dependenciesElement = null;
        for (Element childElement : rootElement.getChildren()) {
          if ("dependencies".equals(childElement.getName())) {
            dependenciesElement = childElement;
            break;
          }
        }

        if (dependenciesElement != null) {
          for (Element childElement : dependenciesElement.getChildren()) {
            if ("dependency".equals(childElement.getName())) {
              ArtifactDependencySpec spec = createSpec(childElement);
              if (spec != null) {
                dependencies.add(spec);
              }
            }
          }
        }
      }
    }
    catch (Exception e) {
      String msg = String.format("Failed to obtain dependencies in POM file for library '%1$s", libraryPath.getName());
      LOG.warn(msg, e);
    }
    return dependencies;
  }

  @Nullable
  private static ArtifactDependencySpec createSpec(@NotNull Element dependencyElement) {
    String artifactId = null;
    String groupId = null;
    String version = null;
    for (Element childElement : dependencyElement.getChildren()) {
      if ("artifactId".equals(childElement.getName())) {
        artifactId = textOf(childElement);
      }
      else if ("groupId".equals(childElement.getName())) {
        groupId = textOf(childElement);
      }
      else if ("version".equals(childElement.getName())) {
        version = textOf(childElement);
      }
    }
    if (isNotEmpty(artifactId)) {
      return new ArtifactDependencySpec(artifactId, groupId, version);
    }

    return null;
  }

  @Nullable
  private static String textOf(@NotNull Element e) {
    return nullize(e.getText(), true);
  }
}
