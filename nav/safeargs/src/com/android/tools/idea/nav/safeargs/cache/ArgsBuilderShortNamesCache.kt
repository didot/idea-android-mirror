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
package com.android.tools.idea.nav.safeargs.cache

import com.android.tools.idea.nav.safeargs.module.SafeArgsCacheModuleService
import com.android.tools.idea.nav.safeargs.project.ProjectNavigationResourceModificationTracker
import com.android.tools.idea.nav.safeargs.project.SafeArgsEnabledFacetsProjectComponent
import com.android.tools.idea.nav.safeargs.psi.LightArgsBuilderClass
import com.android.tools.idea.nav.safeargs.safeArgsModeTracker
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.Processor

/**
 * A short names cache for finding any [LightArgsBuilderClass] instances by their unqualified name.
 */
class ArgsBuilderShortNamesCache(project: Project) : PsiShortNamesCache() {
  private val component = project.getComponent(SafeArgsEnabledFacetsProjectComponent::class.java)
  private val lightClassesCache: CachedValue<List<LightArgsBuilderClass>>

  init {
    val cachedValuesManager = CachedValuesManager.getManager(project)

    lightClassesCache = cachedValuesManager.createCachedValue {
      val builders = component.modulesUsingSafeArgs
        .asSequence()
        .flatMap { facet -> SafeArgsCacheModuleService.getInstance(facet).args.asSequence()}
        .map { it.builderClass }
        .toList()

      CachedValueProvider.Result.create(builders,
                                        ProjectNavigationResourceModificationTracker.getInstance(project),
                                        project.safeArgsModeTracker)
    }
  }

  override fun getAllClassNames(): Array<String> = arrayOf("Builder")

  override fun getClassesByName(name: String, scope: GlobalSearchScope): Array<PsiClass> {
    if (name != LightArgsBuilderClass.BUILDER_NAME) {
      return PsiClass.EMPTY_ARRAY
    }

    return lightClassesCache.value.toTypedArray()
  }

  override fun getAllMethodNames() = arrayOf<String>()
  override fun getMethodsByName(name: String, scope: GlobalSearchScope) = arrayOf<PsiMethod>()
  override fun getMethodsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array<PsiMethod> {
    return getMethodsByName(name, scope).take(maxCount).toTypedArray()
  }

  override fun processMethodsWithName(name: String,
                                      scope: GlobalSearchScope,
                                      processor: Processor<PsiMethod>): Boolean {
    // We are asked to process each method in turn, aborting if false is ever returned, and passing
    // that result back up the chain.
    return getMethodsByName(name, scope).all { method -> processor.process(method) }
  }

  override fun getAllFieldNames() = arrayOf<String>()
  override fun getFieldsByName(name: String, scope: GlobalSearchScope) = arrayOf<PsiField>()
  override fun getFieldsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array<PsiField> {
    return getFieldsByName(name, scope).take(maxCount).toTypedArray()
  }
}
