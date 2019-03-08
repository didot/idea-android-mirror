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

import static com.android.SdkConstants.AAPT_PREFIX;
import static com.android.SdkConstants.AAPT_URI;
import static com.android.SdkConstants.ANDROID_NS_NAME;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.APP_PREFIX;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.DIST_PREFIX;
import static com.android.SdkConstants.DIST_URI;
import static com.android.SdkConstants.TOOLS_PREFIX;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.URI_PREFIX;
import static com.android.SdkConstants.XLIFF_PREFIX;
import static com.android.SdkConstants.XLIFF_URI;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.collect.ImmutableSet;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.XmlSchemaProvider;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription;
import org.jetbrains.android.dom.xml.XmlResourceDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidXmlSchemaProvider extends XmlSchemaProvider {

  private static final NotNullLazyKey<XmlFile, Project> DUMMY_XSD = NotNullLazyKey.create(
    AndroidXmlSchemaProvider.class.getName(),
    project ->
      (XmlFile)PsiFileFactory.getInstance(project)
        .createFileFromText("android.xsd", XMLLanguage.INSTANCE,
                            "<dummy />", false, false));

  @Override
  public XmlFile getSchema(@NotNull @NonNls String url, @Nullable final Module module, @NotNull PsiFile baseFile) {
    return module == null ? null : DUMMY_XSD.getValue(module.getProject());
  }

  @Override
  public boolean isAvailable(@NotNull final XmlFile file) {
    PsiFile f = file.getOriginalFile();
    if (!(f instanceof XmlFile)) {
      return false;
    }
    final XmlFile originalFile = (XmlFile)f;

    return ReadAction.compute(() -> {
      if (AndroidResourceUtil.isInResourceSubdirectory(originalFile, null)) {
        PsiDirectory parent = originalFile.getParent();
        if (parent == null) {
          return false;
        }

        ResourceFolderType resType = ResourceFolderType.getFolderType(parent.getName());

        // Don't run on custom XML files with defined namespaces. Users may want to validate them.
        return resType != ResourceFolderType.XML || XmlResourceDomFileDescription.isXmlResourceFile(originalFile);
      }

      return ManifestDomFileDescription.isManifestFile(originalFile);
    });
  }

  @NotNull
  @Override
  public Set<String> getAvailableNamespaces(@NotNull XmlFile file, @Nullable String tagName) {
    Set<String> result = new HashSet<>();
    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet != null) {
      result.add(TOOLS_URI);
      result.add(ANDROID_URI);
      if (ManifestDomFileDescription.isManifestFile(file)) {
        result.add(DIST_URI);
      }
      ResourceFolderType type = ResourceHelper.getFolderType(file.getOriginalFile());
      if (type != ResourceFolderType.MIPMAP && type != ResourceFolderType.RAW) {
        if (type == ResourceFolderType.DRAWABLE) {
          result.add(AAPT_URI);
        }
        // string and some xml files may contain xliff.
        if (type == ResourceFolderType.XML || type == ResourceFolderType.VALUES) {
          result.add(XLIFF_URI);
        }

        result.add(getLocalXmlNamespace(facet));

        result.addAll(getResourceNamespaces(facet));
      }
    }
    return result;
  }

  @Override
  public String getDefaultPrefix(@NotNull @NonNls String namespace, @NotNull XmlFile context) {
    if (ANDROID_URI.equals(namespace)) {
      return ANDROID_NS_NAME;
    }
    else if (namespace.equals(DIST_URI)) {
      return DIST_PREFIX;
    }
    else if (namespace.equals(TOOLS_URI)) {
      return TOOLS_PREFIX;
    }
    else if (namespace.equals(AUTO_URI) || namespace.startsWith(URI_PREFIX)) {
      return APP_PREFIX;
    }
    else if (namespace.equals(XLIFF_URI)) {
      return XLIFF_PREFIX;
    }
    else if (namespace.equals(AAPT_URI)) {
      return AAPT_PREFIX;
    }
    return null;
  }

  @NotNull
  private static String getLocalXmlNamespace(@NotNull AndroidFacet facet) {
    return ResourceRepositoryManager.getInstance(facet).getNamespace().getXmlNamespaceUri();
  }

  @NotNull
  private static ImmutableSet<String> getResourceNamespaces(@NotNull AndroidFacet facet) {
    return ResourceRepositoryManager.getAppResources(facet).getNamespaces()
                                    .stream()
                                    .map(ResourceNamespace::getXmlNamespaceUri)
                                    .collect(toImmutableSet());
  }
}
