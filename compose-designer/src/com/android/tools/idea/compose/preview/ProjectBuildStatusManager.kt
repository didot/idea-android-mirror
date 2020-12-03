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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.compose.preview.util.hasBeenBuiltSuccessfully
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/** The project status */
sealed class ProjectStatus

/** The project is indexing or not synced yet */
object NotReady : ProjectStatus()

/** The project has not been built */
object NeedsBuild : ProjectStatus()

/** The project is compiled but one or more files are out of date **/
object OutOfDate : ProjectStatus()

/** The project is compiled and up to date */
class Ready(val successfulBuild: Boolean = true) : ProjectStatus()

private val LOG = Logger.getInstance(ProjectStatus::class.java)

/**
 * Class managing the build status of a project and its state.
 *
 * @param parentDisposable [Disposable] to track for disposing this manager.
 * @param editorFile the file in the editor to track changes and the build status. If the project has not been
 *  built since it was open, this file is used to find if there are any existing .class files that indicate that has
 *  been built before.
 */
class ProjectBuildStatusManager(parentDisposable: Disposable, editorFile: PsiFile) {
  private val modificationTracker: ModificationTracker = editorFile.virtualFile
  private var lastModificationCount = modificationTracker.modificationCount
  private val project: Project = editorFile.project
  private val _isBuilding = AtomicBoolean(false)
  var status: ProjectStatus = NotReady
    get() = if (isBuildOutOfDate()) {
      OutOfDate
    }
    else field
    private set(value) {
      if (field != value) {
        LOG.debug("Status change old = $field, new = $value")
        field = value
      }
    }
  val isBuilding: Boolean get() = _isBuilding.get()

  init {
    ProjectSystemService.getInstance(project).projectSystem.getBuildManager().addBuildListener(parentDisposable, object : ProjectSystemBuildManager.BuildListener {
      override fun buildStarted(mode: ProjectSystemBuildManager.BuildMode) {
        _isBuilding.set(true)
        LOG.debug("buildStarted $mode")
        if (mode == ProjectSystemBuildManager.BuildMode.CLEAN) {
          status = NeedsBuild
        }
      }

      override fun buildCompleted(result: ProjectSystemBuildManager.BuildResult) {
        _isBuilding.set(false)
        LOG.debug("buildFinished $result")
        lastModificationCount = modificationTracker.modificationCount
        if (result.mode == ProjectSystemBuildManager.BuildMode.CLEAN) return
        if (result.status == ProjectSystemBuildManager.BuildStatus.SUCCESS) {
          status = Ready()
        }
        else {
          status = when (status) {
            // If the project was ready before, we keep it as Ready since it was just the new build
            // that failed.
            is Ready -> Ready(false)
            // If the project was not ready, then it needs a build since this one failed.
            else -> NeedsBuild
          }
        }
      }
    })

    project.runWhenSmartAndSyncedOnEdt(parentDisposable, Consumer {
      if (status === NotReady) {
        // Set the initial state of the project and initialize the modification count.
        lastModificationCount = modificationTracker.modificationCount
        status = if (hasBeenBuiltSuccessfully(project) { editorFile }) Ready() else NeedsBuild
      }
    })
  }

  private fun isBuildOutOfDate() = lastModificationCount < modificationTracker.modificationCount
}