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
package org.jetbrains.android.refactoring;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class MigrateToAppCompatUsageViewDescriptor implements UsageViewDescriptor {
  private final PsiElement[] myElements;

  MigrateToAppCompatUsageViewDescriptor(PsiElement[] elements) {
    myElements = elements;
  }

  @NotNull
  @Override
  public PsiElement[] getElements() {
    return myElements;
  }

  @Override
  public String getProcessedElementsHeader() {
    return "Items to be migrated";
  }

  @Override
  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return JavaRefactoringBundle.message("occurrences.to.be.migrated", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }
}
