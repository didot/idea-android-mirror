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
package com.android.tools.idea.navigator.nodes.apk.java;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInsight.navigation.NavigationUtil.openFileWithPsiElement;

class ClassNode extends ProjectViewNode<ApkClass> {
  @NotNull private final ApkClass myClass;
  @NotNull private final ClassFinder myClassFinder;

  ClassNode(@NotNull Project project,
            @NotNull ApkClass apkClass,
            @NotNull ViewSettings viewSettings,
            @NotNull ClassFinder classFinder) {
    super(project, apkClass, viewSettings);
    myClass = apkClass;
    myClassFinder = classFinder;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    // TODO show members
    return Collections.emptyList();
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setIcon(PlatformIcons.CLASS_ICON);
    presentation.setPresentableText(getText());
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return getText();
  }

  @NotNull
  private String getText() {
    return myClass.getName();
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (canNavigate()) {
      PsiClass found = myClassFinder.findClass(myClass);
      if (found != null) {
        openFileWithPsiElement(found, requestFocus, requestFocus);
      }
    }
  }

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Override
  public boolean canRepresent(Object element) {
    // This method is invoked when a file in an editor is selected, and "Autoscroll from Source" is enabled.
    // The IDE will try to find a node in the "Android" view that corresponds to the selection (the selection is passed as the parameter
    // "element".)
    if (element instanceof VirtualFile) {
      return contains((VirtualFile)element);
    }
    if (element instanceof PsiClass) {
      return canRepresent((PsiClass)element);
    }
    if (element instanceof PsiMethod) {
      PsiClass containingClass = ((PsiMethod)element).getContainingClass();
      if (containingClass != null) {
        return canRepresent(containingClass);
      }
    }
    else if (element instanceof PsiElement) {
      VirtualFile file = getContainingFile((PsiElement)element);
      if (file != null) {
        return contains(file);
      }
    }
    return false;
  }

  private boolean canRepresent(@NotNull PsiClass psiClass) {
    return myClass.getFqn().equals(psiClass.getQualifiedName());
  }

  @Nullable
  private static VirtualFile getContainingFile(@NotNull PsiElement element) {
    PsiFile containingFile = element.getContainingFile();
    return containingFile != null ? containingFile.getVirtualFile() : null;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    List<String> classes = myClassFinder.findClasses(file);
    return classes.contains(myClass.getFqn());
  }
}
