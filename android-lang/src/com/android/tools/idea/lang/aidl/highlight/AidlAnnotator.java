/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.lang.aidl.highlight;

import com.android.tools.idea.lang.aidl.psi.AidlAnnotationElement;
import com.android.tools.idea.lang.aidl.psi.AidlConstantDeclaration;
import com.android.tools.idea.lang.aidl.psi.AidlDottedName;
import com.android.tools.idea.lang.aidl.psi.AidlEnumDeclaration;
import com.android.tools.idea.lang.aidl.psi.AidlEnumeratorDeclaration;
import com.android.tools.idea.lang.aidl.psi.AidlInterfaceDeclaration;
import com.android.tools.idea.lang.aidl.psi.AidlMethodDeclaration;
import com.android.tools.idea.lang.aidl.psi.AidlNamedElement;
import com.android.tools.idea.lang.aidl.psi.AidlParcelableDeclaration;
import com.android.tools.idea.lang.aidl.psi.AidlUnionDeclaration;
import com.android.tools.idea.lang.aidl.psi.AidlVariableDeclaration;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Provide fine granularity syntax highlighting based on the PSI element.
 */
public class AidlAnnotator implements Annotator {

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof AidlAnnotationElement) {
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        .textAttributes(DefaultLanguageHighlighterColors.METADATA)
        .create();
    }
    else if (element instanceof AidlNamedElement) {
      PsiElement component = element.getParent();
      if (component instanceof AidlDottedName) {
        component = component.getParent();
      }
      if (component instanceof AidlInterfaceDeclaration ||
          component instanceof AidlParcelableDeclaration ||
          component instanceof AidlEnumDeclaration ||
          component instanceof AidlUnionDeclaration) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
          .textAttributes(DefaultLanguageHighlighterColors.CLASS_NAME)
          .create();
      }
      else if (component instanceof AidlMethodDeclaration) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
          .textAttributes(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
          .create();
      } else if (component instanceof AidlVariableDeclaration || component instanceof AidlEnumeratorDeclaration) {
          holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .textAttributes(DefaultLanguageHighlighterColors.INSTANCE_FIELD)
            .create();
      } else if (component instanceof AidlConstantDeclaration) {
          holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .textAttributes(DefaultLanguageHighlighterColors.CONSTANT)
            .create();
      }
    }
  }
}
