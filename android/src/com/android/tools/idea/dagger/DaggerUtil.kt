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
package com.android.tools.idea.dagger

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.util.EmptyQuery
import com.intellij.util.Query
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty

const val DAGGER_MODULE_ANNOTATION = "dagger.Module"
const val DAGGER_PROVIDES_ANNOTATION = "dagger.Provides"
const val INJECT_ANNOTATION = "javax.inject.Inject"

/**
 * Returns all @Module-annotated classes in given [scope].
 */
private fun getDaggerModules(scope: GlobalSearchScope): Query<PsiClass> {
  val scopeAnnotationClass = JavaPsiFacade.getInstance(scope.project).findClass(DAGGER_MODULE_ANNOTATION, scope) ?: return EmptyQuery()
  return AnnotatedElementsSearch.searchPsiClasses(scopeAnnotationClass, scope)
}

/**
 * Returns all Dagger providers (@Provide/@Binds-annotated methods, @Inject-annotated constructors) for given [type] within given [scope].
 *
 * TODO: add @Binds functions and constructors.
 */
fun getDaggerProvidersForType(type: PsiType, scope: GlobalSearchScope): Collection<PsiMethod> = getDaggerProvidesMethodsForType(type, scope)

/**
 * True if PsiMethod belongs to a class annotated with @Module.
 */
private val PsiMethod.isInDaggerModule: Boolean
  get() = containingClass?.hasAnnotation(DAGGER_MODULE_ANNOTATION) == true

/**
 * Returns all @Provide-annotated methods that return given [type] within [scope].
 */
private fun getDaggerProvidesMethodsForType(type: PsiType, scope: GlobalSearchScope): Collection<PsiMethod> {
  return getMethodsWithAnnotation(DAGGER_PROVIDES_ANNOTATION, scope).filter { it.returnType == type && it.isInDaggerModule }
}

/**
 * Returns all methods with [annotationName] within [scope].
 */
private fun getMethodsWithAnnotation(annotationName: String, scope: GlobalSearchScope): Query<PsiMethod> {
  val annotationClass = JavaPsiFacade.getInstance(scope.project).findClass(annotationName, scope) ?: return EmptyQuery()
  return AnnotatedElementsSearch.searchPsiMethods(annotationClass, scope)
}

/**
 * True if PsiField has @Inject annotation.
 */
private val PsiField.isInjected get() = hasAnnotation(INJECT_ANNOTATION)

/**
 * True if KtProperty has @Inject annotation.
 */
private val KtProperty.isInjected get() = findAnnotation(FqName(INJECT_ANNOTATION)) != null

/**
 * True if PsiElement is Dagger provider i.e @Provides-annotated method.
 *
 * TODO: Add @Binds-annotated methods and @Inject-annotated constructor.
 */
val PsiElement?.isDaggerProvider: Boolean
  get() {
    return this is PsiMethod && hasAnnotation(DAGGER_PROVIDES_ANNOTATION) ||
           this is KtFunction && findAnnotation(FqName(DAGGER_PROVIDES_ANNOTATION)) != null
  }

/**
 * True if PsiElement is Dagger consumer i.e @Inject-annotated field,
 * param of @Inject-annotated method or param of Dagger provider, see [isDaggerProvider].
 *
 * TODO: Add support for params.
 */
val PsiElement?.isDaggerConsumer: Boolean
  get() = this is PsiField && isInjected || this is KtProperty && isInjected
