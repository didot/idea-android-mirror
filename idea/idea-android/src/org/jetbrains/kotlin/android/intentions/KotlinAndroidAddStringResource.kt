/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.android.intentions

import com.android.resources.ResourceType
import com.intellij.CommonBundle
import com.intellij.codeInsight.template.*
import com.intellij.codeInsight.template.impl.*
import com.intellij.codeInsight.template.macro.VariableOfTypeMacro
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.Messages
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.android.actions.CreateXmlResourceDialog
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.android.util.AndroidResourceUtil
import org.jetbrains.kotlin.builtins.isExtensionFunctionType
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptor
import org.jetbrains.kotlin.idea.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode


class KotlinAndroidAddStringResource : SelfTargetingIntention<KtLiteralStringTemplateEntry>(KtLiteralStringTemplateEntry::class.java,
                                                                                            "Extract string resource") {
    private val GET_STRING_METHOD = "getString"
    private val EXTRACT_RESOURCE_DIALOG_TITLE = "Extract Resource"
    private val PACKAGE_NOT_FOUND_ERROR = "package.not.found.error"

    override fun isApplicableTo(element: KtLiteralStringTemplateEntry, caretOffset: Int): Boolean {
        if (AndroidFacet.getInstance(element.containingFile) == null) {
            return false
        }

        return element.parent.children.size == 1
    }

    override fun applyTo(element: KtLiteralStringTemplateEntry, editor: Editor?) {
        val facet = AndroidFacet.getInstance(element.containingFile)
        if (editor == null) {
            throw IllegalArgumentException("This intention requires an editor.")
        }

        if (facet == null) {
            throw IllegalStateException("This intention requires android facet.")
        }

        val file = element.containingFile
        val project = file.project

        val manifestPackage = getManifestPackage(facet)
        if (manifestPackage == null) {
            Messages.showErrorDialog(project, AndroidBundle.message(PACKAGE_NOT_FOUND_ERROR), CommonBundle.getErrorTitle())
            return
        }

        val parameters = getCreateXmlResourceParameters(facet.module, element) ?:
                         return

        if (!AndroidResourceUtil.createValueResource(facet.module, parameters.name, ResourceType.STRING,
                                                     parameters.fileName, parameters.directoryNames, parameters.value)) {
            return
        }

        createResourceReference(facet.module, editor, file, element, manifestPackage, parameters.name, ResourceType.STRING)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        UndoUtil.markPsiFileForUndo(file)
    }

    private fun getCreateXmlResourceParameters(module: Module, element: KtLiteralStringTemplateEntry): CreateXmlResourceParameters? {

        val stringValue = element.text

        val showDialog = !ApplicationManager.getApplication().isUnitTestMode
        val resourceName = element.getUserData(CREATE_XML_RESOURCE_PARAMETERS_NAME_KEY)

        val dialog = CreateXmlResourceDialog(module, ResourceType.STRING, resourceName, stringValue, true)
        dialog.title = EXTRACT_RESOURCE_DIALOG_TITLE
        if (showDialog && !dialog.showAndGet()) {
            return null
        }

        return CreateXmlResourceParameters(dialog.resourceName,
                                           dialog.value,
                                           dialog.fileName,
                                           dialog.dirNames)
    }

    private fun createResourceReference(module: Module, editor: Editor, file: PsiFile, element: PsiElement, aPackage: String,
                                        resName: String, resType: ResourceType) {
        val rFieldName = AndroidResourceUtil.getRJavaFieldName(resName)
        val fieldName = "$aPackage.R.$resType.$rFieldName"

        val template: TemplateImpl
        if (!needContextReceiver(element)) {
            template = TemplateImpl("", "$GET_STRING_METHOD($fieldName)", "")
        }
        else {
            template = TemplateImpl("", "\$context\$.$GET_STRING_METHOD($fieldName)", "")
            val marker = MacroCallNode(VariableOfTypeMacro())
            marker.addParameter(ConstantNode("android.content.Context"))
            template.addVariable("context", marker, ConstantNode("context"), true)
        }

        val containingLiteralExpression = element.parent
        editor.caretModel.moveToOffset(containingLiteralExpression.textOffset)
        editor.document.deleteString(containingLiteralExpression.textRange.startOffset, containingLiteralExpression.textRange.endOffset)
        val marker = editor.document.createRangeMarker(containingLiteralExpression.textOffset, containingLiteralExpression.textOffset)
        marker.isGreedyToLeft = true
        marker.isGreedyToRight = true

        TemplateManager.getInstance(module.project).startTemplate(editor, template, false, null, object : TemplateEditingAdapter() {
            override fun waitingForInput(template: Template?) {
                JavaCodeStyleManager.getInstance(module.project).shortenClassReferences(file, marker.startOffset, marker.endOffset)
            }

            override fun beforeTemplateFinished(state: TemplateState?, template: Template?) {
                JavaCodeStyleManager.getInstance(module.project).shortenClassReferences(file, marker.startOffset, marker.endOffset)
            }
        })
    }

    private fun needContextReceiver(element: PsiElement): Boolean {
        val classesWithGetSting = listOf("android.content.Context", "android.app.Fragment", "android.support.v4.app.Fragment")
        val viewClass = listOf("android.view.View")
        var parent = PsiTreeUtil.findFirstParent(element, true) { it is KtClassOrObject || it is KtFunction || it is KtLambdaExpression }

        while (parent != null) {

            if (parent.isSubclassOrSubclassExtension(classesWithGetSting)) {
                return false
            }

            if (parent.isSubclassOrSubclassExtension(viewClass) ||
                (parent is KtClassOrObject && !parent.isInnerClass() && !parent.isObjectLiteral())) {
                return true
            }

            parent = PsiTreeUtil.findFirstParent(parent, true) { it is KtClassOrObject || it is KtFunction || it is KtLambdaExpression }
        }

        return true
    }

    private fun getManifestPackage(facet: AndroidFacet) = facet.manifest?.`package`?.value

    private fun PsiElement.isSubclassOrSubclassExtension(baseClasses: Collection<String>) =
            (this as? KtClassOrObject)?.isSubclassOfAny(baseClasses) ?:
            this.isSubclassExtensionOfAny(baseClasses)

    private fun PsiElement.isSubclassExtensionOfAny(baseClasses: Collection<String>) =
            (this as? KtLambdaExpression)?.isSubclassExtensionOfAny(baseClasses) ?:
            (this as? KtFunction)?.isSubclassExtensionOfAny(baseClasses) ?:
            false

    private fun KtClassOrObject.isObjectLiteral() = (this as? KtObjectDeclaration)?.isObjectLiteral() ?: false

    private fun KtClassOrObject.isInnerClass() = (this as? KtClass)?.isInner() ?: false

    private fun KtFunction.isSubclassExtensionOfAny(baseClasses: Collection<String>): Boolean {
        val descriptor = resolveToDescriptor() as FunctionDescriptor
        val extendedTypeDescriptor = descriptor.extensionReceiverParameter?.type?.constructor?.declarationDescriptor as? ClassDescriptor
        return extendedTypeDescriptor != null && baseClasses.any { extendedTypeDescriptor.isSubclassOf(it) }
    }

    private fun KtLambdaExpression.isSubclassExtensionOfAny(baseClasses: Collection<String>): Boolean {
        val bindingContext = analyze(BodyResolveMode.PARTIAL)
        val type = bindingContext.getType(this)

        if (type == null || !type.isExtensionFunctionType) {
            return false
        }

        val extendedTypeDescriptor = type.arguments.first().type.constructor.declarationDescriptor
        if (extendedTypeDescriptor is ClassDescriptor) {
            return baseClasses.any { extendedTypeDescriptor.isSubclassOf(it) }
        }

        return false
    }

    private fun KtClassOrObject.isSubclassOfAny(baseClasses: Collection<String>): Boolean {
        val bindingContext = analyze(BodyResolveMode.PARTIAL)
        val declarationDescriptor = bindingContext.get(BindingContext.CLASS, this)
        return baseClasses.any { declarationDescriptor?.isSubclassOf(it) ?: false }
    }

    private fun ClassDescriptor.isSubclassOf(className: String): Boolean {
        return fqNameSafe.asString() == className || defaultType.constructor.supertypes.any {
            (it.constructor.declarationDescriptor as? ClassDescriptor)?.isSubclassOf(className) ?: false
        }
    }
}