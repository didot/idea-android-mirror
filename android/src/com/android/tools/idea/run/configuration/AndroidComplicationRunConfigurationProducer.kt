/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run.configuration

import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil

/**
 * Producer of [AndroidComplicationConfiguration] for classes that extend
 * `androidx.wear.watchface.complications.datasource.ComplicationDataSourceService` or
 * `android.support.wearable.complications.ComplicationProviderService`.
 */
class AndroidComplicationRunConfigurationProducer :
  AndroidWearRunConfigurationProducer<AndroidComplicationConfiguration>(AndroidComplicationConfigurationType::class.java) {

  override fun isValidService(psiClass: PsiClass): Boolean = psiClass.isValidComplicationService()
}

internal fun PsiClass.isValidComplicationService(): Boolean {
  return WearBaseClasses.COMPLICATIONS.any { wearBase -> InheritanceUtil.isInheritor(this, wearBase) }
}
