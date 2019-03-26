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
package com.android.tools.idea.gradle.project

import com.android.tools.idea.gradle.project.importing.GradleProjectImporter
import com.android.tools.idea.gradle.util.GradleProjects
import com.intellij.ide.GeneralSettings
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import javax.swing.Icon
import com.android.tools.idea.util.toPathString
import com.android.tools.idea.util.toVirtualFile
import com.intellij.ide.impl.ProjectUtil.confirmOpenNewProject


/**
 * A project open processor to open Gradle projects in Android Studio.
 *
 * It supports opening projects with or without .idea directory.
 */
class AndroidGradleProjectOpenProcessor : ProjectOpenProcessor() {
  override fun getName(): String = "Android Gradle"

  override fun getIcon(): Icon? = null

  override fun canOpenProject(file: VirtualFile): Boolean =
      GradleProjects.canImportAsGradleProject(file)

  override fun doOpenProject(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    if (!canOpenProject(virtualFile)) return null

    val importTarget = ProjectImportUtil.findImportTarget(virtualFile)
    val adjustedOpenTarget =
        if (importTarget.isDirectory)importTarget
        else importTarget.parent

    if (!canOpenAsExistingProject(adjustedOpenTarget)) {
      if (!forceOpenInNewFrame) {
        if (!promptToCloseIfNecessary(projectToClose)) {
          return null
        }
      }

      val gradleImporter = GradleProjectImporter.getInstance()
      return gradleImporter.importProjectCore(virtualFile)
    }

    return ProjectUtil.openProject(adjustedOpenTarget.path, projectToClose, forceOpenInNewFrame)
  }

  private fun promptToCloseIfNecessary(project: Project?): Boolean {
    var success = true
    val openProjects = ProjectManager.getInstance().openProjects
    if (openProjects.isNotEmpty()) {
      val exitCode = confirmOpenNewProject(false)
      if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
        val toClose = if (project != null && !project.isDefault) project else openProjects[openProjects.size - 1]
        if (!ProjectUtil.closeAndDispose(toClose)) {
          success = false
        }
      }
      else if (exitCode != GeneralSettings.OPEN_PROJECT_NEW_WINDOW) {
        success = false
      }
    }
    return success
  }

  private fun canOpenAsExistingProject(file: VirtualFile): Boolean =
      file.toPathString().resolve(Project.DIRECTORY_STORE_FOLDER).toVirtualFile(true) != null
}