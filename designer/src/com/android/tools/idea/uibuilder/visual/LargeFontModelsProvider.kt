/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.intellij.openapi.Disposable
import com.intellij.psi.PsiFile
import org.jetbrains.android.facet.AndroidFacet


object LargeFontModelsProvider : VisualizationModelsProvider {

  // scale factors here matches the framework.
  private const val SCALE_LARGER = 1.15f
  private const val SCALE_SMALLER = 0.85f
  private const val SCALE_LARGEST = 1.3f

  override fun createNlModels(parentDisposable: Disposable, file: PsiFile, facet: AndroidFacet): List<NlModel> {

    if (file.typeOf() != LayoutFileType) {
      return emptyList()
    }

    val virtualFile = file.virtualFile ?: return emptyList()
    val configurationManager = ConfigurationManager.getOrCreateInstance(facet)

    val defaultConfig = configurationManager.getConfiguration(virtualFile)

    val models = mutableListOf<NlModel>()
    models.add(NlModel.builder(facet, virtualFile, defaultConfig)
                 .withParentDisposable(parentDisposable)
                 .withModelDisplayName("Default (100%)")
                 .withModelTooltip(defaultConfig.toHtmlTooltip())
                 .withComponentRegistrar(NlComponentRegistrar)
                 .build())

    val smallerFontConfig = Configuration.create(defaultConfig, virtualFile)
    smallerFontConfig.fontScale = SCALE_SMALLER
    val largerFontConfig = Configuration.create(defaultConfig, virtualFile)
    largerFontConfig.fontScale = SCALE_LARGER
    val largestFontConfig = Configuration.create(defaultConfig, virtualFile)
    largestFontConfig.fontScale = SCALE_LARGEST

    models.add(NlModel.builder(facet, virtualFile, smallerFontConfig)
                 .withParentDisposable(parentDisposable)
                 .withModelDisplayName("Small (85%)")
                 .withModelTooltip(smallerFontConfig.toHtmlTooltip())
                 .withComponentRegistrar(NlComponentRegistrar)
                 .build())

    models.add(NlModel.builder(facet, virtualFile, largerFontConfig)
                 .withParentDisposable(parentDisposable)
                 .withModelDisplayName("Large (115%)")
                 .withModelTooltip(largerFontConfig.toHtmlTooltip())
                 .withComponentRegistrar(NlComponentRegistrar)
                 .build())

    models.add(NlModel.builder(facet, virtualFile, largestFontConfig)
                 .withParentDisposable(parentDisposable)
                 .withModelDisplayName("Largest (130%)")
                 .withModelTooltip(largestFontConfig.toHtmlTooltip())
                 .withComponentRegistrar(NlComponentRegistrar)
                 .build())
    return models
  }
}