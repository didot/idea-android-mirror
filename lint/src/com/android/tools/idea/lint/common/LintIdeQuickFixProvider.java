/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common;

import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension point registering quick fix providers
 */
public interface LintIdeQuickFixProvider {
  ExtensionPointName<LintIdeQuickFixProvider> EP_NAME =
    ExtensionPointName.create("com.android.tools.idea.lint.common.lintQuickFixProvider");

  @NotNull
  LintIdeQuickFix[] getQuickFixes(
    @NotNull Issue issue,
    @NotNull PsiElement startElement,
    @NotNull PsiElement endElement,
    @NotNull String message,
    @Nullable LintFix fixData);
}
