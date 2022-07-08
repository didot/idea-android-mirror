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
package com.android.tools.idea.editors.build

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.editors.fast.BlockingDaemonClient
import com.android.tools.idea.editors.fast.FastPreviewConfiguration
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.fast.FastPreviewRule
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiElement
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor

class ProjectBuildStatusManagerTest {
  val projectRule = AndroidProjectRule.inMemory()
  val project: Project
    get() = projectRule.project

  @get:Rule
  val chainRule: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(FastPreviewRule())

  /**
   * [PsiFileSnapshotFilter] that allows changing the filter on the fly. Alter the [filter] is updated or when the filter changes behaviour,
   * [incModificationCount] should be called.
   */
  class TestFilter: PsiFileSnapshotFilter, SimpleModificationTracker() {
    var filter: (PsiElement) -> Boolean = { true }

    override fun accepts(element: PsiElement): Boolean = filter(element)
  }

  @Test
  fun testFastPreviewTriggersCompileState() {
    val psiFile = projectRule.fixture.addFileToProject("src/a/Test.kt", "fun a() {}")

    val blockingDaemon = BlockingDaemonClient()
    val fastPreviewManager = FastPreviewManager.getTestInstance(project, { _, _, _, _ -> blockingDaemon }).also {
      Disposer.register(projectRule.fixture.testRootDisposable, it)
    }
    projectRule.replaceProjectService(FastPreviewManager::class.java, fastPreviewManager)

    val fileFilter = TestFilter()
    val statusManager = ProjectBuildStatusManager.create(
      projectRule.fixture.testRootDisposable,
      psiFile,
      fileFilter,
      scope = CoroutineScope(Executor { command -> command.run() }.asCoroutineDispatcher()))

    runBlocking {
      val module = projectRule.fixture.module
      val asyncScope = AndroidCoroutineScope(projectRule.fixture.testRootDisposable)
      val latch = CountDownLatch(11)
      asyncScope.launch(AndroidDispatchers.workerThread) {
        fastPreviewManager.compileRequest(psiFile, module)
        latch.countDown()
      }
      blockingDaemon.firstRequestReceived.await()
      Assert.assertTrue(statusManager.isBuilding)

      // Launch additional requests
      repeat(10) {
        asyncScope.launch(AndroidDispatchers.workerThread) {
          fastPreviewManager.compileRequest(psiFile, module)
          latch.countDown()
        }
      }
      blockingDaemon.complete()
      latch.await()
      Assert.assertFalse(statusManager.isBuilding)
    }
  }

  @Test
  fun testFastPreviewStatusChangeInvalidatesFile() {
    val psiFile = projectRule.fixture.addFileToProject("src/a/Test.kt", "fun a() {}")

    val fileFilter = ProjectBuildStatusManagerTest.TestFilter()
    val statusManager = ProjectBuildStatusManager.create(
      projectRule.fixture.testRootDisposable,
      psiFile,
      fileFilter,
      scope = CoroutineScope(Executor { command -> command.run() }.asCoroutineDispatcher()))

    try {
      FastPreviewConfiguration.getInstance().isEnabled = true

      // Simulate a successful build
      (statusManager as ProjectBuildStatusManagerForTests).getBuildListenerForTest().buildStarted(ProjectSystemBuildManager.BuildMode.COMPILE)
      (statusManager as ProjectBuildStatusManagerForTests).getBuildListenerForTest().buildCompleted(
        ProjectSystemBuildManager.BuildResult(ProjectSystemBuildManager.BuildMode.COMPILE, ProjectSystemBuildManager.BuildStatus.SUCCESS, 1L))

      assertEquals(ProjectStatus.Ready, statusManager.status)

      // Disabling Live Edit will bring the out of date state
      FastPreviewConfiguration.getInstance().isEnabled = false
      assertEquals(ProjectStatus.OutOfDate, statusManager.status)
    } finally {
      FastPreviewConfiguration.getInstance().resetDefault()
    }
  }
}