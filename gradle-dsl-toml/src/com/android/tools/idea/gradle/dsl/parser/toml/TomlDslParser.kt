/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.toml

import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral.LiteralType.LITERAL
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.intellij.psi.PsiElement
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlArrayTable
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlKeyValueOwner
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlRecursiveVisitor
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader
import org.toml.lang.psi.TomlValue
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

class TomlDslParser(
  val psiFile: TomlFile,
  private val context: BuildModelContext,
  val dslFile: GradleDslFile
) : GradleDslParser, TomlDslNameConverter {
  override fun getContext(): BuildModelContext = context

  override fun parse() {
    fun getVisitor(context: GradlePropertiesDslElement): TomlRecursiveVisitor = object : TomlRecursiveVisitor() {
      var stringContext = ""

      // TODO(b/200280395): this is just around for print-debugging the visited structure of the Psi.  Probably remove this (and
      //  any overrides that are just calls to dovisit { super() }) when the parser is more complete.
      private fun doVisit(description: String, thunk: () -> Unit) {
        val printVisit = false
        if (printVisit) {
          println("${stringContext}${description}")
          stringContext += "  "
        }
        thunk()
        if (printVisit) {
          stringContext = stringContext.substring(2)
        }
      }

      override fun visitArrayTable(element: TomlArrayTable) = doVisit("TomlArrayTable") { super.visitArrayTable(element) }
      override fun visitKey(element: TomlKey) = doVisit("TomlKey (${element.text})") { super.visitKey(element) }
      override fun visitKeySegment(element: TomlKeySegment) = doVisit("TomlKeySegment (${element.text})") { super.visitKeySegment(element) }
      override fun visitKeyValueOwner(element: TomlKeyValueOwner) = doVisit("TomlKeyValueOwner") { super.visitKeyValueOwner(element) }
      override fun visitValue(element: TomlValue) = doVisit("TomlValue (${element.text})") { super.visitValue(element) }
      override fun visitTableHeader(element: TomlTableHeader) = doVisit("TomlTableHeader") { super.visitTableHeader(element) }

      override fun visitTable(element: TomlTable) {
        doVisit("TomlTable") {
          val key = element.header.key
          if (key != null) {
            key.doWithContext(context) { segment, context ->
              val map = GradleDslExpressionMap(context, element, GradleNameElement.from(segment, this@TomlDslParser), true)
              context.addParsedElement(map)
              getVisitor(map).let { visitor -> element.entries.forEach { it.accept(visitor) } }
            }
          }
          else {
            super.visitTable(element)
          }
        }
      }

      // TODO(xof): these will only be visited when context is a List: for well-formed Toml, map entries will be handled by
      //  visitKeyValue.  This separation, which is motivated by the fact that TomlValue is apparently deeper in the Psi hierarchy than
      //  these specific value instances, is nevertheless confusing and we should probably rewrite so that all values are handled here
      //  whether the context is List or Map: essentially all we would need is to pass the name of the resultant element down, though the
      //  accept and visitor interface makes this annoying.  (My kingdom for dynamic scope.)
      override fun visitArray(element: TomlArray) = doVisit("TomlArray") {
        val name = GradleNameElement.empty()
        val list = GradleDslExpressionList(context, element, true, name)
        context.addParsedElement(list)
        getVisitor(list).let { visitor -> element.elements.forEach { it.accept(visitor) } }
      }

      override fun visitInlineTable(element: TomlInlineTable) = doVisit("TomlInlineTable") {
        val name = GradleNameElement.empty()
        val map = GradleDslExpressionMap(context, element, name, true)
        context.addParsedElement(map)
        getVisitor(map).let { visitor -> element.entries.forEach { it.accept(visitor) } }
      }
      override fun visitLiteral(element: TomlLiteral) = doVisit("TomlLiteral (${element.text})") {
        val name = GradleNameElement.empty()
        val literal = GradleDslLiteral(context, element, name, element, LITERAL)
        context.addParsedElement(literal)
      }

      override fun visitKeyValue(element: TomlKeyValue) {
        doVisit("TomlKeyValue (${element.text})") {
          element.key.doWithContext(context) { segment, context ->
            val name = GradleNameElement.from(segment, this@TomlDslParser)
            when (val value = element.value) {
              is TomlLiteral -> {
                val literal = GradleDslLiteral(context, element, name, value, LITERAL)
                context.addParsedElement(literal)
              }
              is TomlInlineTable -> {
                val map = GradleDslExpressionMap(context, element, name, true)
                context.addParsedElement(map)
                getVisitor(map).let { visitor -> value.entries.forEach { it.accept(visitor) } }
              }
              is TomlArray -> {
                val list = GradleDslExpressionList(context, element, true, name)
                context.addParsedElement(list)
                getVisitor(list).let { visitor -> value.elements.forEach { it.accept(visitor) } }
              }
              else -> Unit
            }
          }
        }
      }
    }
    psiFile.accept(getVisitor(dslFile))
  }

  override fun convertToPsiElement(context: GradleDslSimpleExpression, literal: Any): PsiElement? {
    return null
  }

  override fun setUpForNewValue(context: GradleDslLiteral, newValue: PsiElement?) {

  }

  override fun extractValue(context: GradleDslSimpleExpression, literal: PsiElement, resolve: Boolean): Any? {
    return when (literal) {
      is TomlLiteral -> when (val kind = literal.kind) {
        is TomlLiteralKind.String -> kind.value
        // TODO(b/200280395): handle the other kinds too!  (TomlLiteralKind is sealed so this should be easy(!))
        else -> literal.text
      }
      else -> literal.text
    }
  }

  override fun shouldInterpolate(elementToCheck: GradleDslElement): Boolean {
    return false
  }

  override fun getResolvedInjections(context: GradleDslSimpleExpression, psiElement: PsiElement): MutableList<GradleReferenceInjection> {
    return mutableListOf()
  }

  override fun getInjections(context: GradleDslSimpleExpression, psiElement: PsiElement): MutableList<GradleReferenceInjection> {
    return mutableListOf()
  }

  override fun getPropertiesElement(nameParts: MutableList<String>,
                                    parentElement: GradlePropertiesDslElement,
                                    nameElement: GradleNameElement?): GradlePropertiesDslElement? {
    return null
  }
}

fun TomlKey.doWithContext(context: GradlePropertiesDslElement, thunk: (TomlKeySegment, GradlePropertiesDslElement) -> Unit) {
  val lastSegmentIndex = segments.size - 1
  var currentContext = context
  segments.forEachIndexed { i, segment ->
    if (i == lastSegmentIndex) return thunk(segment, currentContext)
    val description = PropertiesElementDescription(segment.name, GradleDslExpressionMap::class.java, ::GradleDslExpressionMap)
    currentContext = currentContext.ensurePropertyElement(description)
  }
}