/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.jetbrains.android.refactoring

import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.URI_PREFIX
import com.android.builder.model.AaptOptions
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.SingleNamespaceResourceRepository
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.res.ResourceRepositoryManager
import com.google.common.collect.Maps
import com.google.common.collect.Table
import com.google.common.collect.Tables
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.impl.migration.PsiMigrationManager
import com.intellij.psi.impl.source.xml.SchemaPrefixReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlDocument
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BaseRefactoringAction
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.Usage
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView
import com.intellij.usages.rules.SingleParentUsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRuleProvider
import com.intellij.util.text.nullize
import com.intellij.util.xml.DomManager
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.GenericDomValue
import com.intellij.util.xml.WrappingConverter
import org.jetbrains.android.dom.converters.AndroidResourceReference
import org.jetbrains.android.dom.converters.ResourceReferenceConverter
import org.jetbrains.android.dom.converters.StyleItemNameConverter
import org.jetbrains.android.dom.resources.ResourceValue
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.IdeaSourceProvider
import org.jetbrains.android.util.AndroidResourceUtil
import org.jetbrains.android.util.AndroidUtils
import javax.swing.Icon

private val DataContext.module: Module? get() = LangDataKeys.MODULE.getData(this)

/**
 * Action to perform the refactoring.
 *
 * Decides if the refactoring is available and constructs the right [MigrateToResourceNamespacesHandler] object if it is.
 */
class MigrateToResourceNamespacesAction : BaseRefactoringAction() {
  override fun getHandler(dataContext: DataContext) = MigrateToResourceNamespacesHandler()
  override fun isHidden() = StudioFlags.MIGRATE_TO_RESOURCE_NAMESPACES_REFACTORING_ENABLED.get().not()
  override fun isAvailableInEditorOnly() = false
  override fun isAvailableForLanguage(language: Language?) = true

  override fun isEnabledOnDataContext(dataContext: DataContext) = isEnabledOnModule(dataContext.module)
  override fun isEnabledOnElements(elements: Array<PsiElement>) = isEnabledOnModule(ModuleUtil.findModuleForPsiElement(elements.first()))

  private fun isEnabledOnModule(module: Module?): Boolean {
    return ResourceRepositoryManager.getOrCreateInstance(module ?: return false)?.namespacing == AaptOptions.Namespacing.DISABLED
  }
}

/**
 * [RefactoringActionHandler] for [MigrateToResourceNamespacesAction].
 *
 * Since there's no user input required to start the refactoring, it just runs a fresh [MigrateToResourceNamespacesProcessor].
 */
class MigrateToResourceNamespacesHandler : RefactoringActionHandler {
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
    dataContext?.module?.let(this::invoke)
  }

  override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext?) {
    dataContext?.module?.let(this::invoke)
  }

  private fun invoke(module: Module) {
    val processor = MigrateToResourceNamespacesProcessor(AndroidFacet.getInstance(module)!!)
    processor.setPreviewUsages(true)

    offerToCreateBackupAndRun(module.project, processor.commandName) {
      processor.run()
    }
  }
}

private sealed class ResourceUsageInfo(element: PsiElement, startOffset: Int, endOffset: Int) : UsageInfo(element, startOffset, endOffset) {
  constructor(element: PsiElement) : this(element, 0, element.textLength)

  abstract val resourceType: ResourceType
  abstract val name: String

  var inferredPackage: String? = null
}

private class DomValueUsageInfo(
  /** DOM value that needs to be rewritten. It cannot be stored because it may become invalid during the refactoring. */
  val resourceValue: ResourceValue,

  /** Converter used by the DOM value being rewritten, used to compute the new string value. */
  val converter: ResourceReferenceConverter,

  /** Reference used to highlight the right part of [xmlElement] in the preview window. */
  ref: PsiReference,

  /** [XmlElement] whose [GenericDomValue] needs to be changed. */
  val xmlElement: XmlElement
) : ResourceUsageInfo( // We don't use the UsageInfo(PsiReference) constructor to avoid resolving the reference.
  ref.element,
  ref.rangeInElement.startOffset,
  ref.rangeInElement.endOffset
) {

  override val resourceType: ResourceType
    get() = resourceValue.type!!
  override val name: String
    get() = resourceValue.resourceName!!
}

private class CodeUsageInfo(
  /** The whole field reference, used in "find usages" view. */
  fieldReferenceExpression: PsiElement,
  /** The "R" reference itself, will be rebound to the right class. */
  val classReference: PsiReference,
  override val resourceType: ResourceType,
  override val name: String
) : ResourceUsageInfo(fieldReferenceExpression)

private class XmlAttributeUsageInfo(attribute: XmlAttribute) : ResourceUsageInfo(attribute) {
  override val resourceType: ResourceType get() = ResourceType.ATTR
  override val name: String = attribute.localName
}

private class StyleItemUsageInfo(val xmlAttribute: XmlAttribute, url: ResourceUrl) : ResourceUsageInfo(xmlAttribute) {
  override val resourceType: ResourceType = url.type
  override val name: String = url.name
}

/**
 * Implements the "migrate to resource namespaces" refactoring by finding all references to resources and rewriting them.
 */
class MigrateToResourceNamespacesProcessor(
  private val invokingFacet: AndroidFacet
) : BaseRefactoringProcessor(invokingFacet.module.project) {

  init {
    require(StudioFlags.IN_MEMORY_R_CLASSES.get()) { "IN_MEMORY_R_CLASSES has to be enabled for the refactoring to work." }
  }

  public override fun getCommandName() = "Migrate to resource namespaces"

  private val allFacets = AndroidUtils.getAllAndroidDependencies(invokingFacet.module, true) + invokingFacet

  private val elementFactory = XmlElementFactory.getInstance(myProject)

  override fun findUsages(): Array<out UsageInfo> {
    val progressIndicator = ProgressManager.getInstance().progressIndicator

    progressIndicator.text = "Analyzing XML resource files..."
    val result = mutableListOf<ResourceUsageInfo>()
    result += findResUsages()

    progressIndicator.text = "Analyzing manifest files..."
    result += findManifestUsages()

    progressIndicator.text = "Analyzing code files..."
    result += findCodeUsages()

    progressIndicator.text = "Inferring namespaces..."
    progressIndicator.text2 = null

    val leafRepos = mutableListOf<SingleNamespaceResourceRepository>()
    ResourceRepositoryManager.getAppResources(invokingFacet).getLeafResourceRepositories(leafRepos)

    val inferredNamespaces: Table<ResourceType, String, String> =
      Tables.newCustomTable(Maps.newEnumMap(ResourceType::class.java)) { mutableMapOf<String, String>() }

    val total = result.size.toDouble()

    // TODO(b/78765120): try doing this in parallel using a thread pool.
    result.forEachIndexed { index, resourceUsageInfo ->
      ProgressManager.checkCanceled()

      resourceUsageInfo.inferredPackage = inferredNamespaces.row(resourceUsageInfo.resourceType).computeIfAbsent(resourceUsageInfo.name) {
        for (repo in leafRepos) {
          if (repo.hasResources(ResourceNamespace.RES_AUTO, resourceUsageInfo.resourceType, resourceUsageInfo.name)) {
            // TODO(b/78765120): check other repos and build a list of unresolved or conflicting references, to display in a UI later.
            return@computeIfAbsent (repo as SingleNamespaceResourceRepository).packageName
          }
        }

        null
      }

      progressIndicator.fraction = (index + 1) / total
    }

    return result.toTypedArray()
  }

  private fun findResUsages(): Collection<ResourceUsageInfo> {
    val result = mutableListOf<ResourceUsageInfo>()
    val psiManager = PsiManager.getInstance(myProject)

    for (facet in allFacets) {
      val repositoryManager = ResourceRepositoryManager.getOrCreateInstance(facet)
      if (repositoryManager.namespacing != AaptOptions.Namespacing.DISABLED) continue

      for (resourceDir in repositoryManager.getModuleResources(true)!!.resourceDirs) {
        // TODO(b/78765120): process the files in parallel?
        VfsUtil.processFilesRecursively(resourceDir) { vf ->
          if (vf.fileType == StdFileTypes.XML) {
            val psiFile = psiManager.findFile(vf)
            if (psiFile is XmlFile) {
              result += findXmlUsages(psiFile, facet)
            }
          }

          true // continue processing
        }
      }
    }
    return result
  }

  private fun findManifestUsages(): Collection<ResourceUsageInfo> {
    val psiManager = PsiManager.getInstance(myProject)
    val result = mutableListOf<ResourceUsageInfo>()

    for (facet in allFacets) {
      result += IdeaSourceProvider.getCurrentSourceProviders(facet)
        .asSequence()
        .mapNotNull { it.manifestFile }
        .mapNotNull { psiManager.findFile(it) as? XmlFile }
        .flatMap { findXmlUsages(it, facet).asSequence() }
    }

    return result
  }

  private fun findXmlUsages(xmlFile: XmlFile, currentFacet: AndroidFacet): Collection<ResourceUsageInfo> {
    ProgressManager.checkCanceled()
    val progressIndicator = ProgressManager.getInstance().progressIndicator
    progressIndicator.text2 = xmlFile.virtualFile.path

    val result = mutableListOf<ResourceUsageInfo>()
    val domManager = DomManager.getDomManager(myProject)
    val moduleRepo = ResourceRepositoryManager.getModuleResources(currentFacet)

    fun referenceNeedsRewriting(resourceType: ResourceType, name: String): Boolean {
      return !moduleRepo.hasResources(ResourceNamespace.RES_AUTO, resourceType, name)
    }

    xmlFile.accept(object : XmlRecursiveElementVisitor() {
      override fun visitXmlTag(tag: XmlTag) {
        val domElement = domManager.getDomElement(tag)
        if (domElement is GenericDomValue<*>) {
          handleGenericDomValue(domElement, tag)
        }

        super.visitXmlTag(tag)
      }

      override fun visitXmlAttribute(attribute: XmlAttribute) {
        val domElement = domManager.getDomElement(attribute)
        if (domElement is GenericDomValue<*>) {
          // This attribute is part of our DOM definition, including the dynamic extensions from AttributeProcessingUtil. Check if the
          // attribute itself and its value need to be rewritten. Note that rewriting the attribute value after the attribute name has been
          // changed is harder (because the DOM layer no longer recognizes the attribute), so handle the attribute name first to make sure
          // this is covered by tests. When not running in headless mode the order is changed by the "preview usages" window so cannot be
          // easily controlled.
          if (attribute.namespace == AUTO_URI) {
            result += XmlAttributeUsageInfo(attribute)
          }
          handleGenericDomValue(domElement, attribute)
        }
        super.visitXmlAttribute(attribute)
      }

      private fun handleGenericDomValue(domValue: GenericDomValue<*>, sourceXmlElement: XmlElement) {
        val converter = WrappingConverter.getDeepestConverter(domValue.converter, domValue)
        val psiElement = DomUtil.getValueElement(domValue) ?: return
        when (converter) {
          is ResourceReferenceConverter -> {
            references@ for (reference in psiElement.references) {
              if (reference !is AndroidResourceReference) continue@references

              val resourceValue = reference.resourceValue
              when {
                resourceValue.`package` != null -> {
                  // Leave as-is, this is either a reference to a framework resource or sample data.
                }
                resourceValue.resourceType?.startsWith('+') == true -> {
                  // This defines a new id, no need to change.
                }
                else -> {
                  // See if this resource is defined in the same module, otherwise it needs to be rewritten.
                  val name = resourceValue.resourceName.nullize(nullizeSpaces = true) ?: continue@references
                  val resourceType = resourceValue.type ?: continue@references
                  if (referenceNeedsRewriting(resourceType, name)) {
                    result += DomValueUsageInfo(resourceValue, converter, reference, sourceXmlElement)
                  }
                }
              }
            }
          }
          is StyleItemNameConverter -> {
            val url = domValue.stringValue?.let(ResourceUrl::parseAttrReference) ?: return
            if (url.namespace == null && referenceNeedsRewriting(url.type, url.name)) {
              result += StyleItemUsageInfo(psiElement.parentOfType()!!, url)
            }
          }
        // TODO(b/78765120): handle other relevant converters.
        }
      }
    })

    progressIndicator.text2 = null
    return result
  }

  private fun findCodeUsages(): Collection<ResourceUsageInfo> {
    val result = mutableListOf<ResourceUsageInfo>()

    for (facet in allFacets) {
      val moduleRepo = ResourceRepositoryManager.getModuleResources(facet)
      val rClasses = myProject.getProjectSystem().getLightResourceClassService().getLightRClassesAccessibleFromModule(facet.module)

      // TODO(b/78765120): should we rewrite dependent modules as well?
      val scope = facet.module.moduleScope

      // TODO(b/78765120): process references in parallel?
      for (rClass in rClasses) {
        for (psiReference in ReferencesSearch.search(rClass, scope)) {
          val classRef = psiReference.element as? PsiReferenceExpression ?: continue
          val typeRef = classRef.parent as? PsiReferenceExpression ?: continue
          val nameRef = typeRef.parent as? PsiReferenceExpression ?: continue

          // Make sure the PSI structure is as expected for something like "R.string.app_name":
          if (nameRef.qualifierExpression != typeRef || typeRef.qualifierExpression != classRef) continue

          val name = nameRef.referenceName ?: continue
          val resourceType = ResourceType.fromClassName(typeRef.referenceName ?: continue) ?: continue
          if (!moduleRepo.hasResources(ResourceNamespace.RES_AUTO, resourceType, name)) {
            result += CodeUsageInfo(
              fieldReferenceExpression = nameRef,
              classReference = psiReference,
              resourceType = resourceType,
              name = name
            )
          }
        }
      }
    }

    return result
  }

  override fun performRefactoring(usages: Array<UsageInfo>) {
    val progressIndicator = ProgressManager.getInstance().progressIndicator
    progressIndicator.isIndeterminate = false

    progressIndicator.text = "Rewriting resource references..."
    val psiMigration = PsiMigrationManager.getInstance(myProject).startMigration()
    try {
      val totalUsages = usages.size.toDouble()
      usages.forEachIndexed { index, usageInfo ->
        if (usageInfo !is ResourceUsageInfo) error("Don't know how to handle ${usageInfo.javaClass.name}.")

        val inferredPackage = usageInfo.inferredPackage ?: return@forEachIndexed
        when (usageInfo) {
          is DomValueUsageInfo -> {
            val xmlElement = usageInfo.xmlElement
            val tag = when (xmlElement) {
              is XmlTag -> xmlElement
              is XmlAttribute -> xmlElement.parent
              else -> return@forEachIndexed
            }
            val namespace = findOrCreateNamespacePrefix(tag, inferredPackage)
            val resourceValue = usageInfo.resourceValue
            val newStringValue = usageInfo.converter.toString(
              ResourceValue.referenceTo(
                resourceValue.prefix,
                namespace,
                resourceValue.resourceType,
                resourceValue.resourceName
              ),
              null
            ) ?: ""

            when (xmlElement) {
              is XmlTag -> xmlElement.value.text = newStringValue
              is XmlAttribute -> xmlElement.value = newStringValue
              else -> error("Don't know how to handle $xmlElement")
            }
          }
          is XmlAttributeUsageInfo -> {
            val element = usageInfo.element as? XmlAttribute ?: return@forEachIndexed
            val prefix = findOrCreateNamespacePrefix(element.parent, inferredPackage)
            element.references
              .find { it is SchemaPrefixReference }
              ?.handleElementRename(prefix)
          }
          is StyleItemUsageInfo -> {
            val tag = usageInfo.xmlAttribute.parent
            val prefix = findOrCreateNamespacePrefix(tag, inferredPackage)
            val newUrl = ResourceUrl.create(prefix, ResourceType.ATTR, usageInfo.name)
            usageInfo.xmlAttribute.value = newUrl.qualifiedName
          }
          is CodeUsageInfo -> {
            val reference = usageInfo.classReference
            reference.bindToElement(
              findOrCreateClass(
                myProject,
                psiMigration,
                AndroidResourceUtil.packageToRClass(inferredPackage),

                // We're dealing with light R classes, so need to pick the right scope here. This will be handled by
                // AndroidResolveScopeEnlarger.
                scope = reference.element.resolveScope
              )
            )
          }
        }

        progressIndicator.fraction = (index + 1) / totalUsages
      }

    } finally {
      psiMigration.finish()
    }

    progressIndicator.text = "Updating Gradle build files..."
    progressIndicator.fraction = 0.0

    val projectBuildModel = ProjectBuildModel.get(myProject)

    val totalFacets = allFacets.size.toDouble()
    allFacets.forEachIndexed { index, facet ->
      val moduleBuildModel = projectBuildModel.getModuleBuildModel(facet.module) ?: return@forEachIndexed
      moduleBuildModel.android().aaptOptions().namespaced().setValue(true)
      moduleBuildModel.applyChanges()
      progressIndicator.fraction = (index + 1) / totalFacets
    }

    syncBeforeFinishingRefactoring(myProject)
  }

  /**
   * Finds the xmlns prefix used for the given resource package name in the context of the given [tag]. If no such prefix is defined,
   * it gets added to the root tag of the document.
   */
  private fun findOrCreateNamespacePrefix(tag: XmlTag, inferredPackage: String): String {
    return tag.getPrefixByNamespace(URI_PREFIX + inferredPackage) ?: run {
      var newPrefix = choosePrefix(inferredPackage)
      if (tag.getNamespaceByPrefix(newPrefix).isNotEmpty()) {
        var i = 2
        while (tag.getNamespaceByPrefix(newPrefix + i).isNotEmpty()) {
          i++
        }

        newPrefix += i
      }

      tag.parentOfType<XmlDocument>()?.rootTag?.let { addXmlnsDeclaration(it, newPrefix, URI_PREFIX + inferredPackage) }
      newPrefix
    }
  }

  private fun addXmlnsDeclaration(tag: XmlTag, prefix: String, uri: String) {
    tag.addBefore(
      elementFactory.createAttribute("xmlns:$prefix", uri, tag),
      tag.attributes.firstOrNull { it.namespacePrefix != "xmlns" }
    )
  }

  override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
    // TODO(b/78765120): Report conflicts and any other issues. This method runs on the UI thread, so we need to do the actual work in [findUsages].
    return if (refUsages.get().isNotEmpty()) {
      true
    }
    else {
      Messages.showInfoMessage(myProject, "No cross-namespace resource references found", "Migrate to resource namespaces")
      false
    }
  }

  override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor = object : UsageViewDescriptorAdapter() {
    override fun getElements(): Array<PsiElement> = PsiElement.EMPTY_ARRAY
    override fun getProcessedElementsHeader() = "Resource references to migrate"
  }
}

/**
 * Picks the short namespace prefix for the given resource package name.
 *
 * TODO(b/80284538): Decide how to pick a short name for a library. For now we're using the last component of the package name, but we
 *                   discussed storing the suggested short prefix in an AAR's metadata, so that library authors can provide a suggestion.
 */
private fun choosePrefix(packageName: String): String = packageName.substringAfterLast('.')


class ResourcePackageGroupingRuleProvider : UsageGroupingRuleProvider {
  override fun createGroupingActions(view: UsageView): Array<AnAction> = AnAction.EMPTY_ARRAY
  override fun getActiveRules(project: Project) = arrayOf(ResourcePackageGroupingRule())
}

class ResourcePackageGroupingRule : SingleParentUsageGroupingRule() {
  override fun getParentGroupFor(usage: Usage, targets: Array<UsageTarget>): UsageGroup? {
    val packageName = (usage as? UsageInfo2UsageAdapter)?.usageInfo?.let { it as? ResourceUsageInfo }?.inferredPackage ?: return null
    return ResourcePackageUsageGroup(packageName)
  }

  override fun getRank(): Int = -1
}

data class ResourcePackageUsageGroup(val packageName: String) : UsageGroup {
  override fun navigate(requestFocus: Boolean) {}
  override fun update() {}

  override fun getIcon(isOpen: Boolean): Icon? = null
  override fun getFileStatus(): FileStatus? = null
  override fun isValid() = true
  override fun canNavigate() = false
  override fun canNavigateToSource() = false
  override fun getText(view: UsageView?): String = "Namespace '${choosePrefix(packageName)}' to be added for $packageName"

  override fun compareTo(other: UsageGroup): Int {
    return if (other is ResourcePackageUsageGroup) {
      packageName.compareTo(other.packageName)
    }
    else {
      -1
    }
  }
}
