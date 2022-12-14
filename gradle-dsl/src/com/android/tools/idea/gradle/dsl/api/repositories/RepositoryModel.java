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
package com.android.tools.idea.gradle.dsl.api.repositories;

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.PsiElementHolder;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import org.jetbrains.annotations.NotNull;

public interface RepositoryModel extends PsiElementHolder {
  enum RepositoryType {
    JCENTER_DEFAULT,
    MAVEN_CENTRAL,
    GOOGLE_DEFAULT,
    FLAT_DIR,
    MAVEN
  }

  @NotNull
  ResolvedPropertyModel name();

  @NotNull
  RepositoryType getType();

  @NotNull
  GradleDslElement getDslElement();
}
