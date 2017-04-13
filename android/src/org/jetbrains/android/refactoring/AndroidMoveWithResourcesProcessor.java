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
package org.jetbrains.android.refactoring;

import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceFolderRegistry;
import com.android.tools.idea.res.ResourceFolderRepository;
import com.android.tools.idea.res.ResourceHelper;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.android.AndroidFileTemplateProvider;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.*;

// TODO: Should we eventually plug into MoveClassHandler.EP_NAME extensions? Offer a QuickFix at any point?
public class AndroidMoveWithResourcesProcessor extends BaseRefactoringProcessor {

  private static final Logger LOGGER = Logger.getInstance(AndroidMoveWithResourcesProcessor.class);

  private final PsiElement[] myRoots;
  private final Set<PsiClass> myClasses;
  private final Set<ResourceItem> myResources;
  private final Set<PsiElement> myManifestEntries;
  private Module myTargetModule;

  protected AndroidMoveWithResourcesProcessor(@NotNull Project project,
                                              @NotNull PsiElement[] roots,
                                              @NotNull Set<PsiClass> classes,
                                              @NotNull Set<ResourceItem> resources,
                                              @NotNull Set<PsiElement> manifestEntries) {
    super(project);
    myRoots = roots;
    myClasses = classes;
    myResources = resources;
    myManifestEntries = manifestEntries;
  }

  public void setTargetModule(@NotNull Module module) {
    myTargetModule = module;
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new UsageViewDescriptor() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        PsiElement[] result = new PsiElement[usages.length];
        for (int i = 0; i < usages.length; i++) {
          result[i] = usages[i].getElement();
        }
        return result;
      }

      @Override
      public String getProcessedElementsHeader() {
        return "Items to be moved";
      }

      @Override
      public String getCodeReferencesText(int usagesCount, int filesCount) {
        return String.format("%1$d resources in %2$d files", usagesCount, filesCount);
      }

      @Nullable
      @Override
      public String getCommentReferencesText(int usagesCount, int filesCount) {
        return null;
      }
    };
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    List<UsageInfo> result = new ArrayList<>();

    for (PsiElement clazz : myClasses) {
      result.add(new UsageInfo(clazz));
    }

    for (PsiElement tag : myManifestEntries) {
      result.add(new UsageInfo(tag));
    }

    for (ResourceItem resource : myResources) {
      PsiFile psiFile = LocalResourceRepository.getItemPsiFile(myProject, resource);
      if (ResourceHelper.getFolderType(psiFile) == ResourceFolderType.VALUES) {
        // This is just a value, so we won't move the entire file, just its corresponding XmlTag
        XmlTag xmlTag = LocalResourceRepository.getItemTag(myProject, resource);
        if (xmlTag != null) {
          result.add(new ResourceXmlUsageInfo(xmlTag, resource));
        }
      }
      else if (psiFile instanceof PsiBinaryFile) {
        // The usage view doesn't handle binaries at all. Work around this (for example,
        // the UsageInfo class asserts in the constructor if the element doesn't have
        // a text range.)
        SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);
        SmartPsiElementPointer<PsiElement> smartPointer = smartPointerManager.createSmartPsiElementPointer(psiFile);
        SmartPsiFileRange smartFileRange =
          smartPointerManager.createSmartPsiFileRangePointer(psiFile, TextRange.EMPTY_RANGE);
        result.add(new ResourceXmlUsageInfo(smartPointer, smartFileRange, resource) {
          @Override
          public boolean isValid() {
            return true;
          }

          @Override
          @Nullable
          public Segment getSegment() {
            return null;
          }
        });
      }
      else if (psiFile != null) {
        result.add(new ResourceXmlUsageInfo(psiFile, resource));
      }
      // TODO: What about references from build.gradle files?
    }

    return UsageViewUtil.removeDuplicatedUsages(result.toArray(new UsageInfo[result.size()]));
  }

  @Override
  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    AndroidFacet facet = AndroidFacet.getInstance(myTargetModule);
    assert facet != null; // We know this has to be an Android module

    List<VirtualFile> javaSourceFolders = Lists.newArrayList();
    for (IdeaSourceProvider provider : IdeaSourceProvider.getCurrentSourceProviders(facet)) {
      javaSourceFolders.addAll(provider.getJavaDirectories());
    }
    VirtualFile javaTargetDir = javaSourceFolders.get(0);

    VirtualFile resDir = facet.getAllResourceDirectories().get(0);
    ResourceFolderRepository repo = ResourceFolderRegistry.get(facet, resDir);

    Set<XmlFile> touchedXmlFiles = new HashSet<>();

    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();

      if (usage instanceof ResourceXmlUsageInfo) {
        ResourceItem resource = ((ResourceXmlUsageInfo)usage).getResourceItem();

        if (element instanceof PsiFile) {
          PsiDirectory targetDir = getOrCreateTargetDirectory(repo, resource);
          if (targetDir != null && targetDir.findFile(((PsiFile)element).getName()) == null) {
            MoveFilesOrDirectoriesUtil.doMoveFile((PsiFile)element, targetDir);
          }
          // TODO: What if a file with that name exists?
        }
        else if (element instanceof XmlTag) {
          XmlFile resourceFile = (XmlFile)getOrCreateTargetValueFile(repo, resource);
          if (resourceFile != null) {
            XmlTag rootTag = resourceFile.getRootTag();
            if (rootTag != null && TAG_RESOURCES.equals(rootTag.getName())) {
              rootTag.addSubTag((XmlTag)element.copy(), false);
              element.delete();
              touchedXmlFiles.add(resourceFile);
            }
          }
          else {
            // We don't move stuff if we can't find the destination resource file
          }
        }
      }
      else if (element instanceof XmlTag) { // This has to be a manifest entry
        XmlFile manifest = (XmlFile)getOrCreateTargetManifestFile(facet);
        if (manifest != null) {
          // TODO: More generally we should recreate the parent chain of tags. For now, we assume the destination is always an
          // <application> tag inside a <manifest>.
          manifest.acceptChildren(new XmlRecursiveElementWalkingVisitor() {
            @Override
            public void visitXmlTag(XmlTag tag) {
              if (TAG_MANIFEST.equals(tag.getName())) {
                XmlTag applicationTag = null;
                for (PsiElement child : tag.getChildren()) {
                  if (child instanceof XmlTag && TAG_APPLICATION.equals(((XmlTag)child).getName())) {
                    applicationTag = (XmlTag)child;
                    applicationTag.addSubTag((XmlTag)element.copy(), false);
                    element.delete();
                    break;
                  }
                }
                if (applicationTag == null) { // We need to create one; this happens with manifests created by the new module wizard.
                  applicationTag = XmlElementFactory.getInstance(myProject).createTagFromText("<" + TAG_APPLICATION + "/>");
                  applicationTag.addSubTag((XmlTag)element.copy(), false);
                  element.delete();
                  tag.addSubTag(applicationTag, true);
                }
                touchedXmlFiles.add(manifest);
              }
              else {
                super.visitXmlTag(tag);
              }
            }
          });
        }
      }
      else if (element instanceof PsiClass) {
        String packageName = ((PsiJavaFile)(element).getContainingFile()).getPackageName();

        MoveClassesOrPackagesUtil.doMoveClass(
          (PsiClass)element,
          RefactoringUtil
            .createPackageDirectoryInSourceRoot(new PackageWrapper(PsiManager.getInstance(myProject), packageName), javaTargetDir),
          true);
      }
    }

    // Reformat the XML files we edited via PSI operations.
    for (XmlFile touchedFile : touchedXmlFiles) {
      CodeStyleManager.getInstance(myProject).reformat(touchedFile);
    }
  }

  @Nullable
  private PsiDirectory getOrCreateTargetDirectory(ResourceFolderRepository base, ResourceItem resourceItem) {
    PsiManager manager = PsiManager.getInstance(myProject);
    if (resourceItem.getSource() != null) {
      ResourceFolderType folderType = ResourceHelper.getFolderType(resourceItem.getSource());
      if (folderType != null) {
        try {
          return manager.findDirectory(
            VfsUtil.createDirectoryIfMissing(base.getResourceDir(), resourceItem.getConfiguration().getFolderName(folderType)));
        }
        catch (Exception ex) {
          LOGGER.debug(ex);
        }
      }
    }
    LOGGER.warn("Couldn't determine target folder for resource " + resourceItem);
    return null;
  }

  @Nullable
  private PsiFile getOrCreateTargetValueFile(ResourceFolderRepository base, ResourceItem resourceItem) {
    if (resourceItem.getSource() != null) {
      try {
        String name = resourceItem.getSource().getFile().getName();
        PsiDirectory dir = getOrCreateTargetDirectory(base, resourceItem);
        if (dir != null) {
          PsiFile result = dir.findFile(name);
          if (result != null) {
            return result;
          }

          // TODO: How do we make sure the custom templates are applied for new files (license, author, etc) ?
          return (PsiFile)AndroidFileTemplateProvider
            .createFromTemplate(AndroidFileTemplateProvider.VALUE_RESOURCE_FILE_TEMPLATE, name, dir);
        }
      }
      catch (Exception ex) {
        LOGGER.debug(ex);
      }
    }
    LOGGER.warn("Couldn't determine target file for resource " + resourceItem);
    return null;
  }

  @Nullable
  private PsiFile getOrCreateTargetManifestFile(AndroidFacet facet) {
    PsiManager manager = PsiManager.getInstance(myProject);

    VirtualFile manifestFile = VfsUtil.findFileByIoFile(facet.getMainSourceProvider().getManifestFile(), false);

    if (manifestFile != null) {
      return manager.findFile(manifestFile);
    }
    else {
      VirtualFile directory = VfsUtil.findFileByIoFile(facet.getMainSourceProvider().getManifestFile().getParentFile(), false);
      if (directory != null) {
        PsiDirectory targetDirectory = manager.findDirectory(directory);
        if (targetDirectory != null) {
          try {
            return (PsiFile)AndroidFileTemplateProvider
              .createFromTemplate(AndroidFileTemplateProvider.ANDROID_MANIFEST_TEMPLATE, FN_ANDROID_MANIFEST_XML, targetDirectory);
          }
          catch (Exception ex) {
            LOGGER.debug(ex);
          }
        }
      }
    }
    LOGGER.warn("Couldn't determine manifest file for module " + myTargetModule);
    return null;
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return "Moving " + RefactoringUIUtil.calculatePsiElementDescriptionList(myRoots);
  }


  static class ResourceXmlUsageInfo extends UsageInfo {

    private final ResourceItem myResourceItem;

    public ResourceXmlUsageInfo(@NotNull PsiElement element, @NotNull ResourceItem resourceItem) {
      super(element);
      myResourceItem = resourceItem;
    }

    public ResourceXmlUsageInfo(@NotNull SmartPsiElementPointer<?> smartPointer,
                                @Nullable SmartPsiFileRange psiFileRange,
                                @NotNull ResourceItem resourceItem) {
      super(smartPointer, psiFileRange, false, false);
      myResourceItem = resourceItem;
    }

    @NotNull
    public ResourceItem getResourceItem() {
      return myResourceItem;
    }
  }
}
