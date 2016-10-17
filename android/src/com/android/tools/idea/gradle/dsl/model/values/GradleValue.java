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
package com.android.tools.idea.gradle.dsl.model.values;

import com.android.tools.idea.gradle.dsl.parser.GradleResolvedVariable;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

import java.util.Map;

/**
 * Represents a value returned by Gradle Dsl Model.
 */
public abstract class GradleValue {
  @NotNull protected final GradleDslElement myDslElement;

  protected GradleValue(@NotNull GradleDslElement dslElement) {
    myDslElement = dslElement;
  }

  @Nullable
  public GroovyPsiElement getPsiElement() {
    return myDslElement.getPsiElement();
  }

  @NotNull
  public VirtualFile getFile() {
    return myDslElement.getDslFile().getFile();
  }

  @NotNull
  public String getPropertyName() {
    return myDslElement.getQualifiedName();
  }

  @Nullable
  public String getDslText() {
    GroovyPsiElement psiElement = getPsiElement();
    return psiElement != null ? psiElement.getText() : null;
  }

  @NotNull
  public Map<String, GradleNotNullValue<Object>> getResolvedVariables() {
    ImmutableMap.Builder<String, GradleNotNullValue<Object>> builder = ImmutableMap.builder();
    for (GradleResolvedVariable variable : myDslElement.getResolvedVariables()) {
      String variableName = variable.getVariableName();
      Object resolvedValue = variable.getValue();
      GradleDslElement element = variable.getElement();
      builder.put(variableName, new GradleNotNullValue<>(element, resolvedValue));
    }
    return builder.build();
  }
}
