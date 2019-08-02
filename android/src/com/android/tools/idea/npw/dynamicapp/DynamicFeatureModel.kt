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
package com.android.tools.idea.npw.dynamicapp

import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt
import com.android.tools.idea.npw.model.NewProjectModel.Companion.nameToJavaPackage
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.platform.AndroidVersionsInfo.VersionItem
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.collections.ObservableList
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_DEVICE_FEATURE_LIST
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_FUSING
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_INSTALL_TIME_DELIVERY
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_INSTALL_TIME_WITH_CONDITIONS_DELIVERY
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_ON_DEMAND
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_ON_DEMAND_DELIVERY
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_TITLE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_IS_INSTANT_MODULE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_DYNAMIC_FEATURE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_NEW_MODULE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_MODULE_SIMPLE_NAME
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Modal
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message

class DynamicFeatureModel(
  project: Project, templateHandle: TemplateHandle, projectSyncInvoker: ProjectSyncInvoker, isInstant: Boolean
) : ModuleModel(project, templateHandle, projectSyncInvoker, "dynamicfeature") {
  @JvmField val featureTitle = StringValueProperty("Module Title")
  @JvmField val packageName = StringValueProperty()
  @JvmField val androidSdkInfo = OptionalValueProperty<VersionItem>()
  @JvmField val baseApplication = OptionalValueProperty<Module>()
  @JvmField val featureOnDemand = BoolValueProperty(true)
  @JvmField val featureFusing = BoolValueProperty(true)
  @JvmField val instantModule = BoolValueProperty(false)
  @JvmField val deviceFeatures = ObservableList<DeviceFeatureModel>()
  @JvmField val downloadInstallKind =
    OptionalValueProperty(if (isInstant) DownloadInstallKind.INCLUDE_AT_INSTALL_TIME else DownloadInstallKind.ON_DEMAND_ONLY)

  override fun handleFinished() {
    object : Modal(project, message(
      "android.compile.messages.generating.r.java.content.name"), false) {
      override fun run(indicator: ProgressIndicator) {
        val modulePaths = createDefaultTemplateAt(myProject.basePath!!, moduleName.get()).paths
        TemplateValueInjector(templateValues)
          .setModuleRoots(modulePaths, myProject.basePath!!, moduleName.get(), packageName.get())
          .setBuildVersion(androidSdkInfo.value, myProject).setBaseFeature(baseApplication.value)

        templateValues.let {
          it[ATTR_IS_DYNAMIC_FEATURE] = true
          it[ATTR_MODULE_SIMPLE_NAME] = nameToJavaPackage(moduleName.get())
          it[ATTR_DYNAMIC_FEATURE_TITLE] = featureTitle.get()
          it[ATTR_DYNAMIC_FEATURE_ON_DEMAND] = featureOnDemand.get()
          it[ATTR_DYNAMIC_FEATURE_FUSING] = featureFusing.get()
          it[ATTR_IS_NEW_MODULE] = true
          it[ATTR_IS_LIBRARY_MODULE] = false
          it[ATTR_DYNAMIC_IS_INSTANT_MODULE] = instantModule.get()
          // Dynamic delivery conditions
          it[ATTR_DYNAMIC_FEATURE_INSTALL_TIME_DELIVERY] = downloadInstallKind.value == DownloadInstallKind.INCLUDE_AT_INSTALL_TIME
          it[ATTR_DYNAMIC_FEATURE_INSTALL_TIME_WITH_CONDITIONS_DELIVERY] = downloadInstallKind.value == DownloadInstallKind.INCLUDE_AT_INSTALL_TIME_WITH_CONDITIONS
          it[ATTR_DYNAMIC_FEATURE_ON_DEMAND_DELIVERY] = downloadInstallKind.value == DownloadInstallKind.ON_DEMAND_ONLY
          it[ATTR_DYNAMIC_FEATURE_DEVICE_FEATURE_LIST] = deviceFeatures
        }
        if (doDryRun()) {
          render()
        }
      }
    }.queue()
  }
}