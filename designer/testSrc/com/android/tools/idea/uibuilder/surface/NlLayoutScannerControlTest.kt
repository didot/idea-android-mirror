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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.error.IssuePanel
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import junit.framework.TestCase
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NlLayoutScannerControlTest : LayoutTestCase() {

  @Volatile
  private var disposable: Disposable? = null
  private var surface: NlDesignSurface? = null
  private var sceneManager: LayoutlibSceneManager? = null
  private val check = object : LayoutScannerConfiguration {
    override var isLayoutScannerEnabled: Boolean = false
  }

  override fun setUp() {
    super.setUp()
    disposable = object : Disposable {
      // If using a lambda, it can be reused by the JVM and causing an exception
      // because the Disposable is already disposed.
      //noinspection Convert2Lambda
      override fun dispose() { }
    }
    surface = mockSurfaceNoSceneManager()
    sceneManager = Mockito.mock(LayoutlibSceneManager::class.java)
    Mockito.`when`(surface!!.sceneManager).thenReturn(sceneManager)
    Mockito.`when`(sceneManager!!.layoutScannerConfig).thenReturn(check)

    val analyticsManager = NlAnalyticsManager(surface!!)
    Mockito.`when`(surface!!.analyticsManager).thenReturn(analyticsManager)
  }

  override fun tearDown() {
    try {
      Disposer.dispose(disposable!!)
      disposable = null
      surface = null
      sceneManager = null
    } finally {
      super.tearDown()
    }
  }

  fun testRunLayoutScannerNoSceneManager() {
    val surface = mockSurfaceNoSceneManager()
    val control = NlLayoutScannerControl(surface, disposable!!)

    assertNotNull(control.runLayoutScanner())
  }

  fun testRunLayoutScanner() {
    val surface = surface!!
    val sceneManager = sceneManager!!
    Mockito.`when`(surface.sceneManager).thenReturn(sceneManager)

    val control = NlLayoutScannerControl(surface, disposable!!)

    assertNotNull(control.runLayoutScanner())

    // Verify request to render
    Mockito.verify(sceneManager, Mockito.times(1)).forceReinflate()
    Mockito.verify(surface, Mockito.times(1)).requestRender()
  }

  fun testLayoutScannerResult() {
    val surface = surface!!
    val control = NlLayoutScannerControl(surface, disposable!!)

    val latch = CountDownLatch(1)
    val result = control.runLayoutScanner()
    result.thenAccept {
      assertTrue(it)
      latch.countDown()
    }

    // Simulate render -> atf result
    simulateSurfaceRefreshedWithScanner(control, 2)

    // check if result from runLayoutScanner triggered
    latch.await(500, TimeUnit.MILLISECONDS)
    TestCase.assertEquals(0, latch.count)

    // check if issue panel is opened
    val showIssuePanelArg = ArgumentCaptor.forClass(Boolean::class.java)
    Mockito.verify(surface, Mockito.times(1)).setShowIssuePanel(showIssuePanelArg.capture())
    assertTrue(showIssuePanelArg.value)
  }

  fun testTryRefreshWithScanner() {
    val surface = surface!!
    val control = NlLayoutScannerControl(surface, disposable!!)
    assertFalse(check.isLayoutScannerEnabled)

    assertTrue(control.tryRefreshWithScanner())

    assertTrue(check.isLayoutScannerEnabled)
    Mockito.verify(surface, Mockito.times(1)).requestRender()
    // Make sure we don't trigger force user request as it messes up metrics.
    Mockito.verify(surface, Mockito.never()).forceUserRequestedRefresh()
  }

  fun testIssuePanelExpanded() {
    val surface = surface!!
    val control = NlLayoutScannerControl(surface, disposable!!)

    control.issuePanelListener.onMinimizeChanged(false)

    assertTrue(check.isLayoutScannerEnabled)
    Mockito.verify(surface, Mockito.times(1)).requestRender()
    // Make sure we don't trigger force user request as it messes up metrics.
    Mockito.verify(surface, Mockito.never()).forceUserRequestedRefresh()
  }

  fun testIssuePanelExpandedWithIssues() {
    val surface = surface!!
    val control = NlLayoutScannerControl(surface, disposable!!)

    // Precondition: Scanner has some issues already
    val issuesSize = 3
    simulateSurfaceRefreshedWithScanner(control, issuesSize)
    assertEquals(issuesSize, control.scanner.issues.size)
    assertTrue(check.isLayoutScannerEnabled)

    // Test: ensure it doesn't re-render.
    control.issuePanelListener.onMinimizeChanged(false)

    Mockito.verify(surface, Mockito.never()).requestRender()
  }

  fun testIssuePanelMinimizedWithoutIssue() {
    val surface = surface!!
    val control = NlLayoutScannerControl(surface, disposable!!)

    // Test when issue panel is minimized
    control.issuePanelListener.onMinimizeChanged(true)

    // When minimized it should be cleared.
    assertTrue(control.scanner.issues.isEmpty())
    assertFalse(check.isLayoutScannerEnabled)
  }

  fun testIssuePanelMinimizedWithIssues() {
    val surface = surface!!
    val control = NlLayoutScannerControl(surface, disposable!!)

    // Precondition: Scanner has some issues already
    val issuesSize = 5
    simulateSurfaceRefreshedWithScanner(control, issuesSize)
    assertEquals(issuesSize, control.scanner.issues.size)

    // Test when issue panel is minimized
    control.issuePanelListener.onMinimizeChanged(true)

    // When minimized it should be cleared.
    assertTrue(control.scanner.issues.isEmpty())
    assertFalse(check.isLayoutScannerEnabled)
  }

  /**
   * Simulate surface render with scanner option turned on, and generate
   * issues thru [NlLayoutScanner.validateAndUpdateLint].
   */
  private fun simulateSurfaceRefreshedWithScanner(control: NlLayoutScannerControl, issuesSize: Int) {
    val helper = ScannerTestHelper()
    val model = helper.buildModel(issuesSize)
    check.isLayoutScannerEnabled = true
    control.scanner.validateAndUpdateLint(helper.mockRenderResult(model), model)
  }

  private fun mockSurfaceNoSceneManager(): NlDesignSurface {
    val surface = Mockito.mock(NlDesignSurface::class.java)
    val issueModel = IssueModel()
    val issuePanel = IssuePanel(surface, issueModel)
    Mockito.`when`(surface.issueModel).thenReturn(issueModel)
    Mockito.`when`(surface.issuePanel).thenReturn(issuePanel)
    return surface
  }
}
