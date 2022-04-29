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
package com.android.tools.idea.compose.gradle.fast

import com.android.flags.junit.SetFlagRule
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.fast.CompilationResult
import com.android.tools.idea.compose.preview.fast.FastPreviewManager
import com.android.tools.idea.compose.preview.renderer.renderPreviewElement
import com.android.tools.idea.compose.preview.toFileNameSet
import com.android.tools.idea.compose.preview.util.SinglePreviewElementInstance
import com.android.tools.idea.concurrency.AndroidDispatchers.diskIoThread
import com.android.tools.idea.editors.literals.FunctionState
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.deployment.liveedit.AndroidLiveEditCodeGenerator
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.replaceText
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

@RunWith(Parameterized::class)
class FastPreviewManagerGradleTest(private val useEmbeddedCompiler: Boolean) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "useEmbeddedCompiler = {0}")
    val useEmbeddedCompilerValues = listOf(true, false)
  }

  @get:Rule
  val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  @get:Rule
  val fastPreviewFlagRule = SetFlagRule(StudioFlags.COMPOSE_FAST_PREVIEW, true)

  @get:Rule
  val useInProcessCompilerFlagRule = SetFlagRule(StudioFlags.COMPOSE_FAST_PREVIEW_USE_IN_PROCESS_DAEMON, useEmbeddedCompiler)
  lateinit var psiMainFile: PsiFile
  lateinit var fastPreviewManager: FastPreviewManager

  @Before
  fun setUp() {
    LiveEditApplicationConfiguration.getInstance().mode = LiveEditApplicationConfiguration.LiveEditMode.LIVE_EDIT
    LiveEditApplicationConfiguration.getInstance().liveEditPreviewEnabled = true
    val mainFile = projectRule.project.guessProjectDir()!!
      .findFileByRelativePath(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path)!!
    psiMainFile = runReadAction { PsiManager.getInstance(projectRule.project).findFile(mainFile)!! }
    fastPreviewManager = FastPreviewManager.getInstance(projectRule.project)
    invokeAndWaitIfNeeded {
      projectRule.buildAndAssertIsSuccessful()
    }
    runWriteActionAndWait {
      projectRule.fixture.openFileInEditor(mainFile)
      WriteCommandAction.runWriteCommandAction(projectRule.project) {
        // Delete the reference to PreviewInOtherFile since it's a top level function not supported
        // by the embedded compiler (b/201728545) and it's not used by the tests.
        projectRule.fixture.editor.replaceText("PreviewInOtherFile()", "")
      }
      projectRule.fixture.moveCaret("Text(\"Hello 2\")|")
      projectRule.fixture.type("\n")
    }
    runInEdtAndWait {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue() // Consume editor events
    }
  }

  @After
  fun tearDown() {
    runBlocking {
      fastPreviewManager.stopAllDaemons().join()
    }
    LiveEditApplicationConfiguration.getInstance().resetDefault()
  }

  @Test
  fun testSingleFileCompileSuccessfully() {
    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    typeAndSaveDocument("Text(\"Hello 3\")\n")
    runBlocking {
      val (result, _) = fastPreviewManager.compileRequest(psiMainFile, module)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
    }
  }

  @Test
  fun testDaemonIsRestartedAutomatically() {
    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    typeAndSaveDocument("Text(\"Hello 3\")\n")
    runBlocking {
      val (result, _) = fastPreviewManager.compileRequest(psiMainFile, module)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
      fastPreviewManager.stopAllDaemons().join()
    }
    runBlocking {
      val (result, _) = fastPreviewManager.compileRequest(psiMainFile, module)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
    }
  }

  @Test
  fun testFastPreviewEditChangeRender() {
    val previewElement = SinglePreviewElementInstance.forTesting("google.simpleapplication.MainActivityKt.TwoElementsPreview")
    val initialState = renderPreviewElement(projectRule.androidFacet(":app"), previewElement).get()!!

    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    typeAndSaveDocument("Text(\"Hello 3\")\n")
    runBlocking {
      val (result, outputPath) = fastPreviewManager.compileRequest(psiMainFile, module)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
      ModuleClassLoaderOverlays.getInstance(module).overlayPath = File(outputPath).toPath()
    }
    val finalState = renderPreviewElement(projectRule.androidFacet(":app"), previewElement).get()!!
    assertTrue(
      "Resulting image is expected to be at least 20% higher since a new text line was added",
      finalState.height > initialState.height * 1.20)
  }

  @Test
  fun testMultipleFilesCompileSuccessfully() {
    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    val psiSecondFile = runReadAction {
      val vFile = projectRule.project.guessProjectDir()!!
        .findFileByRelativePath("app/src/main/java/google/simpleapplication/OtherPreviews.kt")!!
      PsiManager.getInstance(projectRule.project).findFile(vFile)!!
    }
    runBlocking {
      val (result, outputPath) = fastPreviewManager.compileRequest(listOf(psiMainFile, psiSecondFile), module)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
      val generatedFilesSet = withContext(diskIoThread) {
        File(outputPath).toPath().toFileNameSet()
      }
      assertTrue(generatedFilesSet.contains("OtherPreviewsKt.class"))
    }
  }

  // Regression test for b/228168101
  @Test
  fun `test parallel compilations`() {
    // This tests is only to verify the interaction of the internal compiler with the Live Edit
    // on device compilation.
    if (!useEmbeddedCompiler) return

    var compile = true
    val startCountDownLatch = CountDownLatch(1)

    val previewCompilations = AtomicLong(0)
    val previewThread = thread {
      startCountDownLatch.await()
      while (compile) {
        val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
        typeAndSaveDocument("Text(\"Hello 3\")\n")
        runBlocking {
          fastPreviewManager.compileRequest(psiMainFile, module)
        }
        previewCompilations.incrementAndGet()
      }
    }

    val deviceCompilations = AtomicLong(0)
    val deviceThread = thread {
      val output = mutableListOf<AndroidLiveEditCodeGenerator.CodeGeneratorOutput>()
      val function = runReadAction {
        psiMainFile.collectDescendantsOfType<KtNamedFunction>().first { it.name?.contains("TwoElementsPreview") ?: false }
      }
      val state = runReadAction { FunctionState(psiMainFile as KtFile) }

      startCountDownLatch.await()
      while (compile) {
        AndroidLiveEditCodeGenerator(projectRule.project).compile(
          listOf(AndroidLiveEditCodeGenerator.CodeGeneratorInput(psiMainFile, function, state)), output)
        deviceCompilations.incrementAndGet()
      }
    }

    val iterations = 20L

    // Start both threads.
    startCountDownLatch.countDown()

    // Wait for both threads to run the iterations.
    runBlocking {
      while (deviceCompilations.get() < iterations || previewCompilations.get() < iterations) delay(200)
      compile = false
    }

    previewThread.join()
    deviceThread.join()
  }

  private fun typeAndSaveDocument(typedString: String) {
    runWriteActionAndWait {
      projectRule.fixture.type(typedString)
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    runInEdtAndWait {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue() // Consume editor events
    }
  }
}