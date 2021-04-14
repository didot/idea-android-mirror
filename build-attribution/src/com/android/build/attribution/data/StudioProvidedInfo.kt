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
package com.android.build.attribution.data

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.util.GradleProperties
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil

data class StudioProvidedInfo(
  val agpVersion: GradleVersion?,
  val configurationCachingGradlePropertyState: String?
) {
  companion object {
    private const val CONFIGURATION_CACHE_PROPERTY_NAME = "org.gradle.unsafe.configuration-cache"

    fun fromProject(project: Project) = StudioProvidedInfo(
      agpVersion = AndroidPluginInfo.find(project)?.pluginVersion,
      configurationCachingGradlePropertyState = GradleProperties(project).properties.getProperty(CONFIGURATION_CACHE_PROPERTY_NAME)
    )

    fun turnOnConfigurationCacheInProperties(project: Project) {
      GradleProperties(project).apply {
        properties.setProperty("org.gradle.unsafe.configuration-cache", "true")
        save()
      }
      VfsUtil.findFileByIoFile(GradleProperties.getGradlePropertiesFile(project), true)?.let {
        OpenFileDescriptor(project, it).navigate(true)
      }
    }
  }
}