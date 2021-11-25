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
package com.android.tools.idea.compose.preview.scene

import com.android.tools.compose.ComposeLibraryNamespace
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.PreviewGroup
import com.android.tools.idea.compose.preview.analytics.AnimationToolingEvent
import com.android.tools.idea.compose.preview.analytics.AnimationToolingUsageTracker
import com.android.tools.idea.compose.preview.util.PreviewConfiguration
import com.android.tools.idea.compose.preview.util.PreviewDisplaySettings
import com.android.tools.idea.compose.preview.util.PreviewElementInstance
import com.android.tools.idea.compose.preview.util.SinglePreviewElementInstance
import com.google.protobuf.TextFormat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.psi.PsiFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeView(var _hasAnimations: Boolean) {
  @Suppress("unused") // This method is called via reflection
  private fun hasAnimations(): Boolean = _hasAnimations
}

private class TestComposePreviewManager(var interactiveMode: ComposePreviewManager.InteractiveMode = ComposePreviewManager.InteractiveMode.DISABLED) : ComposePreviewManager {

  override fun status(): ComposePreviewManager.Status =
    ComposePreviewManager.Status(hasRuntimeErrors = false,
                                 hasSyntaxErrors = false,
                                 isOutOfDate = false,
                                 isRefreshing = false,
                                 interactiveMode = interactiveMode)

  override var isBuildOnSaveEnabled: Boolean = false
  override val availableGroups: Collection<PreviewGroup> = emptyList()
  override var groupFilter: PreviewGroup = PreviewGroup.ALL_PREVIEW_GROUP
  override var interactivePreviewElementInstance: PreviewElementInstance? = null
  override var animationInspectionPreviewElementInstance: PreviewElementInstance? = null
  override val hasLiveLiterals: Boolean = false
  override val isLiveLiteralsEnabled: Boolean = false
  override val hasDesignInfoProviders: Boolean = false
  override val previewedFile: PsiFile? = null

  override fun dispose() {}
}

internal class ComposeSceneUpdateListenerTest {
  private val logTracker = object : AnimationToolingUsageTracker {
    val loggedEvents = mutableListOf<AnimationToolingEvent>()
    override fun logEvent(event: AnimationToolingEvent): AndroidStudioEvent.Builder {
      loggedEvents.add(event)
      return AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.COMPOSE_ANIMATION_TOOLING)
        .setComposeAnimationToolingEvent(event.build())
    }
  }

  val composable = SinglePreviewElementInstance("composableMethodName",
                                                PreviewDisplaySettings("A name", null, false, false, null), null, null,
                                                PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null, null),
                                                ComposeLibraryNamespace.ANDROIDX_COMPOSE_WITH_API)

  @Test
  fun `check hasAnimations is updated and logged correctly in static mode`() {
    val previewManager = TestComposePreviewManager()
    val fakeView = FakeView(true)
    updateAnimationInspectorToolbarIcon(fakeView, previewManager, composable) { logTracker }
    assertTrue(composable.hasAnimations)
    assertEquals("type: ANIMATION_INSPECTOR_AVAILABLE",
                 logTracker.loggedEvents.joinToString("\n") { TextFormat.shortDebugString(it.build()) })

    logTracker.loggedEvents.clear()
    fakeView._hasAnimations = false
    updateAnimationInspectorToolbarIcon(fakeView, previewManager, composable) { logTracker }
    assertFalse(composable.hasAnimations)
    // this change should be logged again since it was false
    fakeView._hasAnimations = true
    updateAnimationInspectorToolbarIcon(fakeView, previewManager, composable) { logTracker }
    assertTrue(composable.hasAnimations)
    assertEquals("type: ANIMATION_INSPECTOR_AVAILABLE",
                 logTracker.loggedEvents.joinToString("\n") { TextFormat.shortDebugString(it.build()) })
  }

  @Test
  fun `check animation mode only logs transitions from false to true`() {
    val previewManager = TestComposePreviewManager()
    val fakeView = FakeView(true)
    repeat(10) {
      updateAnimationInspectorToolbarIcon(fakeView, previewManager, composable) { logTracker }
    }
    // Only logged once
    assertEquals("type: ANIMATION_INSPECTOR_AVAILABLE",
                 logTracker.loggedEvents.joinToString("\n") { TextFormat.shortDebugString(it.build()) })

    logTracker.loggedEvents.clear()
    fakeView._hasAnimations = false
    updateAnimationInspectorToolbarIcon(fakeView, previewManager, composable) { logTracker }
    fakeView._hasAnimations = true
    updateAnimationInspectorToolbarIcon(fakeView, previewManager, composable) { logTracker }
    assertEquals("type: ANIMATION_INSPECTOR_AVAILABLE",
                 logTracker.loggedEvents.joinToString("\n") { TextFormat.shortDebugString(it.build()) })
  }

  @Test
  fun `check hasAnimations is not updated in interactive`() {
    val previewManager = TestComposePreviewManager(ComposePreviewManager.InteractiveMode.READY)
    val fakeView = FakeView(true)
    updateAnimationInspectorToolbarIcon(fakeView, previewManager, composable) { logTracker }
    assertFalse(composable.hasAnimations)

    previewManager.interactiveMode = ComposePreviewManager.InteractiveMode.DISABLED
    updateAnimationInspectorToolbarIcon(fakeView, previewManager, composable) { logTracker }
    assertTrue(composable.hasAnimations)
  }
}