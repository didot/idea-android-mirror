/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.resource

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTreeVisitor
import org.jetbrains.kotlin.psi.psiUtil.endOffset

/**
 * Service to find the [SourceLocation] of a lambda found in Compose.
 */
class LambdaResolver(project: Project) : ComposeResolver(project) {
  /**
   * Find the lambda [SourceLocation].
   *
   * The compiler will generate a synthetic class for each lambda invocation.
   * @param packageName the class name of the enclosing class of the lambda. This is the first part of the synthetic class name.
   * @param fileName the name of the enclosing file (without the path).
   * @param lambdaName the second part of the synthetic class name i.e. without the enclosed class name
   * @param startLine the starting line of the lambda invoke method as seen by JVMTI (zero based)
   * @param startLine the last line of the lambda invoke method as seen by JVMTI (zero based)
   */
  fun findLambdaLocation(packageName: String, fileName: String, lambdaName: String, startLine: Int, endLine: Int): SourceLocation? {
    if (startLine < 0 || endLine < 0) {
      return null
    }
    val ktFile = findKotlinFile(fileName) { it == packageName } ?: return null
    val doc = ktFile.virtualFile?.let { FileDocumentManager.getInstance().getDocument(it) } ?: return null

    val possible = findPossibleLambdas(ktFile, doc, startLine, endLine)
    val lambda = selectLambdaBasedOnSynthesizedLambdaClassName(possible, lambdaName)

    val navigatable = lambda?.navigationElement as? Navigatable ?: return null
    val startLineOffset = doc.getLineStartOffset(startLine)
    val line = startLine + if (lambda.startOffset < startLineOffset) 0 else 1
    return SourceLocation("${fileName}:$line", navigatable)
  }

  /**
   * Return all the lambda expressions from [ktFile] that are contained entirely within the line range.
   */
  private fun findPossibleLambdas(ktFile: KtFile, doc: Document, startLine: Int, endLine: Int): List<KtLambdaExpression> {
    val offsetRange = doc.getLineStartOffset(startLine)..(doc.getLineEndOffset(endLine))
    if (offsetRange.isEmpty()) {
      return emptyList()
    }
    val possible = mutableListOf<KtLambdaExpression>()
    val findLambdaWithinRangeVisitor = object : KtTreeVisitor<Unit>() {
      override fun visitLambdaExpression(expression: KtLambdaExpression, data: Unit?): Void? {
        super.visitLambdaExpression(expression, data)
        if (expression.startOffset <= offsetRange.last && expression.endOffset >= offsetRange.first) {
          possible.add(expression)
        }
        return null
      }
    }
    ktFile.acceptChildren(findLambdaWithinRangeVisitor, null)
    return possible
  }

  /**
   * Select the most likely lambda from the [lambdas] found from line numbers, by using the synthetic name [lambdaName].
   */
  private fun selectLambdaBasedOnSynthesizedLambdaClassName(lambdas: List<KtLambdaExpression>, lambdaName: String): KtLambdaExpression? {
    when (lambdas.size) {
      0 -> return null
      1 -> return lambdas.single() // no need investigate the lambdaName
    }
    val arbitrary = lambdas.first()
    val topElement = findTopElement(arbitrary) ?: return arbitrary
    val selector = findDesiredLambdaSelectorFromName(lambdaName)
    if (selector.isEmpty()) {
      return arbitrary
    }
    return findLambdaFromSelector(topElement, selector, lambdas) ?: return arbitrary
  }

  /**
   * Find the selector as a list of indices.
   *
   * Each list element correspond to a nesting level among the lambdas found under a top element.
   * The indices generated by the compiler are 1 based.
   */
  private fun findDesiredLambdaSelectorFromName(lambdaName: String): List<Int> {
    val elements = lambdaName.split('$')
    val index = elements.indexOfLast { !isPureDigits(it) } + 1
    if (index > elements.size) {
      return emptyList()
    }
    return elements.subList(index, elements.size).map { it.toInt() }
  }

  private fun isPureDigits(value: String): Boolean =
    value.isNotEmpty() && value.all { it.isDigit() }

  /**
   * Find the closest parent element of interest that contains this [lambda].
   *
   * The synthetic name will include class names, method names, and variable names.
   * Find the closest parent to [lambda] which is one of those 3 elements.
   */
  private fun findTopElement(lambda: KtLambdaExpression): KtElement? {
    var next = lambda.parent as? KtElement
    while (next != null) {
      when (next) {
        is KtClass,
        is KtNamedFunction,
        is KtProperty -> return next

        else -> next = next.parent as? KtElement
      }
    }
    return null
  }

  /**
   * Find the most likely lambda from the [top] element.
   *
   * @param top the top element which is either a class, method, or variable.
   * @param selector the indices for each nesting level as indicated by the synthetic class name of the lambda.
   * @param lambdas the lambdas found in the line interval.
   */
  private fun findLambdaFromSelector(top: KtElement, selector: List<Int>, lambdas: List<KtLambdaExpression>): KtLambdaExpression? {
    var nestingLevel = 0
    val indices = IntArray(selector.size)
    var stop = false
    var bestLambda: KtLambdaExpression? = null

    val findLambdaFromSelectorVisitor = object : KtTreeVisitor<Unit>() {
      override fun visitLambdaExpression(expression: KtLambdaExpression, data: Unit?): Void? {
        if (stop || (nestingLevel < selector.size && selector[nestingLevel] < ++indices[nestingLevel])) {
          stop = true
          return null
        }
        if (nestingLevel >= selector.size || selector[nestingLevel] > indices[nestingLevel]) {
          return null
        }
        if (expression in lambdas) {
          bestLambda = expression
        }
        nestingLevel++
        super.visitLambdaExpression(expression, data)
        nestingLevel--
        return null
      }

      override fun visitClass(klass: KtClass, data: Unit?): Void? {
        // Do not recurse into a class, since this would be a different top element
        return null
      }

      override fun visitProperty(property: KtProperty, data: Unit?): Void? {
        // Do not recurse into a property, since this would be a different top element
        return null
      }

      override fun visitNamedFunction(function: KtNamedFunction, data: Unit?): Void? {
        // Do not recurse into a property, since this would be a different top element
        return null
      }
    }
    top.acceptChildren(findLambdaFromSelectorVisitor, null)
    return bestLambda
  }
}
