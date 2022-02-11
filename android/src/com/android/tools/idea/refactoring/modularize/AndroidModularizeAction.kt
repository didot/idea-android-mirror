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
package com.android.tools.idea.refactoring.modularize

import com.android.tools.idea.projectsystem.getSyncManager
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.actions.BaseJavaRefactoringAction
import org.jetbrains.android.util.AndroidUtils

// open for testing
open class AndroidModularizeAction : BaseJavaRefactoringAction() {

  override fun isAvailableInEditorOnly() = false

  override fun isAvailableForFile(file: PsiFile?): Boolean {
    return file != null && file.fileType == JavaFileType.INSTANCE && AndroidUtils.hasAndroidFacets(file.project)
  }

  private val Project.isLastSyncSuccessful get() = getSyncManager().getLastSyncResult().isSuccessful

  override fun isEnabledOnDataContext(dataContext: DataContext): Boolean {
    // Hide action if last Gradle sync was unsuccessful.
    val project = CommonDataKeys.PROJECT.getData(dataContext)
    if (project != null && !project.isLastSyncSuccessful) {
      return false
    }

    for (element in getPsiElementArray(dataContext)) {
      if (!isAvailableForFile(element.containingFile)) {
        return false
      }
    }

    val file = CommonDataKeys.PSI_FILE.getData(dataContext)
    return file == null || isAvailableForFile(file)
  }

  override fun isAvailableOnElementInEditorAndFile(element: PsiElement, editor: Editor, file: PsiFile, context: DataContext): Boolean {
    val project = file.project
    return project.isLastSyncSuccessful
  }

  override fun isEnabledOnElements(elements: Array<out PsiElement>) = false

  override fun getHandler(dataContext: DataContext) = AndroidModularizeHandler()
}
