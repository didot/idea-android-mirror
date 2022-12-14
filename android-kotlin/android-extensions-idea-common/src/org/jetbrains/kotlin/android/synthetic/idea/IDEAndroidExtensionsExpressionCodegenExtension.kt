/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.android.synthetic.idea

import kotlinx.android.extensions.CacheImplementation
import org.jetbrains.kotlin.android.synthetic.codegen.AbstractAndroidExtensionsExpressionCodegenExtension
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoOrNull
import org.jetbrains.kotlin.psi.KtElement

class IDEAndroidExtensionsExpressionCodegenExtension : AbstractAndroidExtensionsExpressionCodegenExtension() {
    override fun isExperimental(element: KtElement?) =
            element?.moduleInfoOrNull?.androidExtensionsIsExperimental ?: false

    override fun isEnabled(element: KtElement?) =
            element?.moduleInfoOrNull?.androidExtensionsIsEnabled ?: false

    override fun getGlobalCacheImpl(element: KtElement?) =
            element?.moduleInfoOrNull?.androidExtensionsGlobalCacheImpl ?: CacheImplementation.DEFAULT
}