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

package org.jetbrains.android;

import com.android.resources.ResourceFolderType;
import com.google.common.collect.ObjectArrays;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.ResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ValueResourceInfo;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.ATTR_NAME;

public class AndroidFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    if (element instanceof LazyValueResourceElementWrapper) {
      return true;
    }
    if (element instanceof XmlAttributeValue) {
      XmlAttributeValue value = (XmlAttributeValue)element;
      if (AndroidResourceUtil.findIdFields(value).length > 0) {
        return true;
      }
    }
    element = correctResourceElement(element);
    if (element instanceof PsiField) {
      return AndroidResourceUtil.isResourceField((PsiField)element);
    }
    else if (element instanceof PsiFile || element instanceof XmlTag) {
      final AndroidFacet facet = AndroidFacet.getInstance(element);

      if (facet != null) {
        LocalResourceManager resourceManager = ModuleResourceManagers.getInstance(facet).getLocalResourceManager();
        if (element instanceof PsiFile) {
          return resourceManager.getFileResourceFolderType((PsiFile)element) != null;
        }
        else {
          ResourceFolderType fileResType = resourceManager.getFileResourceFolderType(element.getContainingFile());
          if (ResourceFolderType.VALUES == fileResType) {
            return AndroidResourceUtil.getResourceTypeForResourceTag((XmlTag)element) != null;
          }
        }
      }
    }
    return false;
  }

  private static class MyFindUsagesHandler extends FindUsagesHandler {
    private final PsiElement[] myAdditionalElements;

    protected MyFindUsagesHandler(@NotNull PsiElement element, PsiElement... additionalElements) {
      super(element);
      myAdditionalElements = additionalElements;
    }

    @NotNull
    @Override
    public PsiElement[] getSecondaryElements() {
      return myAdditionalElements;
    }
  }

  @Nullable
  private static PsiElement correctResourceElement(PsiElement element) {
    if (element instanceof XmlElement && !(element instanceof XmlFile)) {
      XmlTag tag = element instanceof XmlTag ? (XmlTag)element : PsiTreeUtil.getParentOfType(element, XmlTag.class);
      DomElement domElement = DomManager.getDomManager(element.getProject()).getDomElement(tag);
      if (domElement instanceof ResourceElement) {
        return tag;
      }
      return null;
    }
    return element;
  }

  private static XmlAttributeValue wrapIfNecessary(XmlAttributeValue value) {
    if (value instanceof ResourceElementWrapper) {
      return value;
    }
    return new ValueResourceElementWrapper(value);
  }

  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    if (element instanceof LazyValueResourceElementWrapper) {
      ValueResourceInfo resourceInfo = ((LazyValueResourceElementWrapper)element).getResourceInfo();
      AndroidFacet facet = AndroidFacet.getInstance(element.getContainingFile());
      if (facet == null) {
        return null;
      }
      PsiField[] resourceFields = AndroidResourceUtil.findResourceFields(facet, resourceInfo.getType().getName(),
                                                                         resourceInfo.getName(), true);
      if (resourceFields.length == 0) {
        return null;
      }
      return new MyFindUsagesHandler(element, resourceFields);
    }
    AndroidFacet facet = AndroidFacet.getInstance(element);
    if (facet == null) {
      return null;
    }
    if (element instanceof XmlAttributeValue) {
      XmlAttributeValue value = (XmlAttributeValue)element;
      PsiField[] fields = AndroidResourceUtil.findIdFields(value);
      if (fields.length > 0) {
        element = wrapIfNecessary(value);
        return new MyFindUsagesHandler(element, fields);
      }
    }
    element = correctResourceElement(element);
    if (element instanceof PsiFile) {
      // resource file
      PsiField[] fields = AndroidResourceUtil.findResourceFieldsForFileResource((PsiFile)element, true);
      if (fields.length == 0) {
        return null;
      }
      return new MyFindUsagesHandler(element, fields);
    }
    else if (element instanceof XmlTag) {
      // value resource
      XmlTag tag = (XmlTag)element;
      PsiField[] fields = AndroidResourceUtil.findResourceFieldsForValueResource(tag, true);
      if (fields.length == 0) {
        return null;
      }

      PsiField[] styleableFields = AndroidResourceUtil.findStyleableAttributeFields(tag, true);
      if (styleableFields.length > 0) {
        fields = ObjectArrays.concat(fields, styleableFields, PsiField.class);
      }
      final XmlAttribute nameAttr = tag.getAttribute(ATTR_NAME);
      final XmlAttributeValue nameValue = nameAttr != null ? nameAttr.getValueElement() : null;
      assert nameValue != null;
      return new MyFindUsagesHandler(nameValue, fields);
    }
    else if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      List<PsiElement> resources = AndroidResourceUtil.findResourcesByField(field);
      if (resources.isEmpty()) {
        return new MyFindUsagesHandler(element);
      }

      // ignore alternative resources because their usages are the same
      PsiElement resource = resources.get(0);
      return createFindUsagesHandler(resource, forHighlightUsages);
    }
    return null;
  }

}
