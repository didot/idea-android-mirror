/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.dom;

import com.android.tools.idea.model.AndroidModuleInfo;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.LayoutViewClassUtils;
import org.jetbrains.annotations.NotNull;

public class AndroidClassTagNameReference extends TagNameReference {
  public AndroidClassTagNameReference(ASTNode nameElement, boolean startTagFlag) {
    super(nameElement, startTagFlag);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    assert element instanceof PsiClass;
    XmlTag tagElement = getTagElement();
    assert tagElement != null;

    AndroidFacet facet = AndroidFacet.getInstance(element);
    int minimumApi = facet == null ? -1 : AndroidModuleInfo.getInstance(facet).getModuleMinApi();
    String tagName = ArrayUtil.getFirstElement(LayoutViewClassUtils.getTagNamesByClass((PsiClass)element, minimumApi));

    return tagElement.setName(tagName != null ? tagName : "");
  }

  @Override
  public boolean isSoft() {
    return true;
  }
}
