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
package com.android.tools.idea.gradle.project.model.ide.android.stubs;

import com.android.builder.model.AndroidLibrary;
import com.android.tools.idea.gradle.project.model.ide.android.UnusedModelMethodException;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

/**
 * Creates a deep copy of an {@link AndroidLibrary}.
 */
public final class AndroidLibraryStub extends AndroidBundleStub implements AndroidLibrary {
  @NotNull private final Collection<File> myLocalJars;
  @NotNull private final File myProguardRules;
  @NotNull private final File myLintJar;
  @NotNull private final File myPublicResources;

  public AndroidLibraryStub() {
    this(Lists.newArrayList(new File("jar")), new File("proguardRules"), new File("lintJar"), new File("publicResources"));
  }

  public AndroidLibraryStub(@NotNull Collection<File> jars,
                            @NotNull File proguardRules,
                            @NotNull File lintJar,
                            @NotNull File publicResources) {
    myLocalJars = jars;
    myProguardRules = proguardRules;
    myLintJar = lintJar;
    myPublicResources = publicResources;
  }

  @Override
  @NotNull
  public Collection<File> getLocalJars() {
    return myLocalJars;
  }

  @Override
  @NotNull
  public File getJniFolder() {
    throw new UnusedModelMethodException("getJniFolder");
  }

  @Override
  @NotNull
  public File getAidlFolder() {
    throw new UnusedModelMethodException("getRenderscriptFolder");
  }

  @Override
  @NotNull
  public File getRenderscriptFolder() {
    throw new UnusedModelMethodException("getRenderscriptFolder");
  }

  @Override
  @NotNull
  public File getProguardRules() {
    return myProguardRules;
  }

  @Override
  @NotNull
  public File getLintJar() {
    return myLintJar;
  }

  @Override
  @NotNull
  public File getExternalAnnotations() {
    throw new UnusedModelMethodException("getExternalAnnotations");
  }

  @Override
  @NotNull
  public File getPublicResources() {
    return myPublicResources;
  }

  @Override
  @NotNull
  public File getSymbolFile() {
    throw new UnusedModelMethodException("getSymbolFile");
  }

  @Override
  @Deprecated
  public boolean isOptional() {
    throw new UnusedModelMethodException("isOptional");
  }
}
