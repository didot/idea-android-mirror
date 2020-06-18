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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultStats
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.jvm.JvmMethod
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestApplicationManager
import com.intellij.ui.dualView.TreeTableView
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.isNull
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import java.awt.event.MouseEvent
import java.io.File

/**
 * Unit tests for [AndroidTestResultsTableView].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class AndroidTestResultsTableViewTest {

  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)

  @Mock lateinit var mockListener: AndroidTestResultsTableListener
  @Mock lateinit var mockJavaPsiFacade: JavaPsiFacade
  @Mock lateinit var mockTestArtifactSearchScopes: TestArtifactSearchScopes

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun initialTable() {
    val table = AndroidTestResultsTableView(mockListener, mockJavaPsiFacade, mockTestArtifactSearchScopes)

    // Assert columns.
    table.getTableViewForTesting().tableHeader.columnModel
    assertThat(table.getModelForTesting().columnInfos).hasLength(2)
    assertThat(table.getModelForTesting().columnInfos[0].name).isEqualTo("Tests")
    assertThat(table.getModelForTesting().columnInfos[1].name).isEqualTo("Status")

    // Assert rows.
    assertThat(table.getTableViewForTesting().rowCount).isEqualTo(1)  // Root aggregation row
  }

  @Test
  fun addDevice() {
    val table = AndroidTestResultsTableView(mockListener, mockJavaPsiFacade, mockTestArtifactSearchScopes)

    table.addDevice(device("deviceId1", "deviceName1"))
    table.addDevice(device("deviceId2", "deviceName2"))

    assertThat(table.getModelForTesting().columnInfos).hasLength(4)
    assertThat(table.getModelForTesting().columnInfos[2].name).isEqualTo("deviceName1")
    assertThat(table.getModelForTesting().columnInfos[3].name).isEqualTo("deviceName2")

    // No rows are added until any test results come in (except for the root aggregation row).
    assertThat(table.getTableViewForTesting().rowCount).isEqualTo(1)
  }

  @Test
  fun addTestResults() {
    val table = AndroidTestResultsTableView(mockListener, mockJavaPsiFacade, mockTestArtifactSearchScopes)
    val device1 = device("deviceId1", "deviceName1")
    val device2 = device("deviceId2", "deviceName2")
    val testcase1OnDevice1 = AndroidTestCase("testid1", "method1", "class1", "package1")
    val testcase2OnDevice1 = AndroidTestCase("testid2", "method2", "class2", "package2")
    val testcase1OnDevice2 = AndroidTestCase("testid1", "method1", "class1", "package1")

    table.addDevice(device1)
    table.addDevice(device2)
    table.addTestCase(device1, testcase1OnDevice1)
    table.addTestCase(device1, testcase2OnDevice1)
    table.addTestCase(device2, testcase1OnDevice2)

    // No test cases are finished yet.
    assertThat(table.getTableViewForTesting().rowCount).isEqualTo(5)
    assertThat(table.getTableViewForTesting().getItem(2).methodName).isEqualTo("method1")
    assertThat(table.getTableViewForTesting().getItem(2).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.SCHEDULED)
    assertThat(table.getTableViewForTesting().getItem(2).getTestCaseResult(device2)).isEqualTo(AndroidTestCaseResult.SCHEDULED)
    assertThat(table.getTableViewForTesting().getItem(4).methodName).isEqualTo("method2")
    assertThat(table.getTableViewForTesting().getItem(4).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.SCHEDULED)
    assertThat(table.getTableViewForTesting().getItem(4).getTestCaseResult(device2)).isNull()

    // Let test case 1 and 2 finish on the device 1.
    testcase1OnDevice1.result = AndroidTestCaseResult.PASSED
    testcase2OnDevice1.result = AndroidTestCaseResult.FAILED

    assertThat(table.getTableViewForTesting().getItem(2).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.PASSED)
    assertThat(table.getTableViewForTesting().getItem(2).getTestCaseResult(device2)).isEqualTo(AndroidTestCaseResult.SCHEDULED)
    assertThat(table.getTableViewForTesting().getItem(4).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.FAILED)
    assertThat(table.getTableViewForTesting().getItem(4).getTestCaseResult(device2)).isNull()

    // Let test case 1 finish on the device 2.
    testcase1OnDevice2.result = AndroidTestCaseResult.PASSED

    assertThat(table.getTableViewForTesting().getItem(2).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.PASSED)
    assertThat(table.getTableViewForTesting().getItem(2).getTestCaseResult(device2)).isEqualTo(AndroidTestCaseResult.PASSED)
    assertThat(table.getTableViewForTesting().getItem(4).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.FAILED)
    assertThat(table.getTableViewForTesting().getItem(4).getTestCaseResult(device2)).isNull()
  }

  @Test
  fun addTestResultsWithRetention() {
    val table = AndroidTestResultsTableView(mockListener, mockJavaPsiFacade, mockTestArtifactSearchScopes)
    val device1 = device("deviceId1", "deviceName1")
    val retentionFilePath = "foo"
    val testcase1OnDevice1 = AndroidTestCase(id = "testid1",
                                             methodName = "method1",
                                             className = "class1",
                                             packageName = "package1",
                                             result = AndroidTestCaseResult.FAILED,
                                             retentionSnapshot = File(retentionFilePath))

    table.addDevice(device1)
    table.addTestCase(device1, testcase1OnDevice1)

    assertThat(table.getTableViewForTesting().getItem(2).getTestCaseResult(device1)).isEqualTo(AndroidTestCaseResult.FAILED)
    assertThat(table.getTableViewForTesting().getItem(2).getRetentionSnapshot(device1)!!.path).isEqualTo(retentionFilePath)
  }

  @Test
  fun clickTestResultsRow() {
    val table = AndroidTestResultsTableView(mockListener, mockJavaPsiFacade, mockTestArtifactSearchScopes)
    val device1 = device("deviceId1", "deviceName1")
    val device2 = device("deviceId2", "deviceName2")

    table.addDevice(device1)
    table.addDevice(device2)
    table.addTestCase(device1, AndroidTestCase("testid1", "method1", "class1", "package1", AndroidTestCaseResult.PASSED, "test logcat message"))
    table.addTestCase(device1, AndroidTestCase("testid2", "method2", "class2", "package2", AndroidTestCaseResult.FAILED))
    table.addTestCase(device2, AndroidTestCase("testid1", "method1", "class1", "package1", AndroidTestCaseResult.SKIPPED))
    table.addTestCase(device2, AndroidTestCase("testid2", "method2", "class2", "package2", AndroidTestCaseResult.SKIPPED))

    // Select the test case 1. Click on the test name column.
    table.getTableViewForTesting().setColumnSelectionInterval(0, 0)
    table.getTableViewForTesting().selectionModel.setSelectionInterval(2, 2)

    verify(mockListener).onAndroidTestResultsRowSelected(argThat { results ->
      results.methodName == "method1" &&
      results.getTestCaseResult(device1) == AndroidTestCaseResult.PASSED &&
      results.getLogcat(device1) == "test logcat message" &&
      results.getTestCaseResult(device2) == AndroidTestCaseResult.SKIPPED
    }, isNull())

    // Select the test case 2. Click on the device2 column.
    table.getTableViewForTesting().setColumnSelectionInterval(3, 3)
    table.getTableViewForTesting().selectionModel.setSelectionInterval(4, 4)

    verify(mockListener).onAndroidTestResultsRowSelected(argThat { results ->
      results.methodName == "method2" &&
      results.getTestCaseResult(device1) == AndroidTestCaseResult.FAILED &&
      results.getTestCaseResult(device2) == AndroidTestCaseResult.SKIPPED
    }, eq(device2))

    // Selecting the test case 2 again after clearing the selection should trigger the callback.
    // (Because a user may click the same row again after he/she closes the second page.)
    table.clearSelection()
    table.getTableViewForTesting().setColumnSelectionInterval(3, 3)
    table.getTableViewForTesting().selectionModel.setSelectionInterval(4, 4)

    verify(mockListener, times(2)).onAndroidTestResultsRowSelected(argThat { results ->
      results.methodName == "method2" &&
      results.getTestCaseResult(device1) == AndroidTestCaseResult.FAILED &&
      results.getTestCaseResult(device2) == AndroidTestCaseResult.SKIPPED
    }, eq(device2))

    // Single click on the table.
    val clickEvent = MouseEvent(table.getTableViewForTesting(), 0, 0, 0, 0, 0, /*clickCount=*/1, false)
    table.getTableViewForTesting().mouseListeners.forEach { it.mouseClicked(clickEvent) }

    // Make sure that onAndroidTestResultsRowSelected callback is invoked via mouse listener.
    // (This is important since valueChanged() callback may not called if you click on a
    // same row. For instance, TreeTableView doesn't.)
    verify(mockListener, times(3)).onAndroidTestResultsRowSelected(argThat { results ->
      results.methodName == "method2" &&
      results.getTestCaseResult(device1) == AndroidTestCaseResult.FAILED &&
      results.getTestCaseResult(device2) == AndroidTestCaseResult.SKIPPED
    }, eq(device2))
  }

  @Test
  fun setRowFilter() {
    val table = AndroidTestResultsTableView(mockListener, mockJavaPsiFacade, mockTestArtifactSearchScopes)
    val device = device("deviceId", "deviceName")
    val testcase1 = AndroidTestCase("testid1", "method1", "class1", "package1")
    val testcase2 = AndroidTestCase("testid2", "method2", "class2", "package2")

    table.addDevice(device)
    table.addTestCase(device, testcase1)
    table.addTestCase(device, testcase2)
    table.setRowFilter { results ->
      results.methodName == "method2"
    }

    val view = table.getTableViewForTesting()
    assertThat(view.rowCount).isEqualTo(1)
    assertThat(view.convertRowIndexToView(0)).isEqualTo(-1)  // Root aggregation row
    assertThat(view.convertRowIndexToView(1)).isEqualTo(-1)  // Class1 aggregation row
    assertThat(view.convertRowIndexToView(2)).isEqualTo(-1)  // Method1 row
    assertThat(view.convertRowIndexToView(3)).isEqualTo(-1)  // Class2 aggregation row
    assertThat(view.convertRowIndexToView(4)).isEqualTo(0)   // Method2 row
  }

  @Test
  fun setColumnFilter() {
    val table = AndroidTestResultsTableView(mockListener, mockJavaPsiFacade, mockTestArtifactSearchScopes)
    val device1 = device("deviceId1", "deviceName1")
    val device2 = device("deviceId2", "deviceName2")

    table.addDevice(device1)
    table.addDevice(device2)
    table.setColumnFilter { device ->
      device.id == "deviceId2"
    }

    // "Test Name" + "Test Summary" + "Device 1" + "Device 2".
    // Device 1 is still visible but we change its width to 1px because
    // column sorter is not natively supported unlike rows.
    val view = table.getTableViewForTesting()
    val model = table.getModelForTesting()
    assertThat(view.columnCount).isEqualTo(4)
    assertThat(model.columnInfos[2].getWidth(view)).isEqualTo(1)
    assertThat(model.columnInfos[3].getWidth(view)).isEqualTo(120)
  }

  @Test
  fun tableShouldRetainSelectionAfterDataIsUpdated() {
    val table = AndroidTestResultsTableView(mockListener, mockJavaPsiFacade, mockTestArtifactSearchScopes)
    val device1 = device("deviceId1", "deviceName1")
    val device2 = device("deviceId2", "deviceName2")

    table.addDevice(device1)
    table.addTestCase(device1, AndroidTestCase("testid1", "method1", "class1", "package1", AndroidTestCaseResult.PASSED, "test logcat message"))

    // Select the test case 1.
    table.getTableViewForTesting().setColumnSelectionInterval(0, 0)
    table.getTableViewForTesting().selectionModel.setSelectionInterval(2, 2)

    // Then, the test case 2 is added to the table.
    table.addTestCase(device1, AndroidTestCase("testid2", "method2", "class2", "package2", AndroidTestCaseResult.FAILED))

    // Make sure the test method1 is still being selected.
    assertThat(table.getTableViewForTesting().selectedObject?.methodName).isEqualTo("method1")

    // Next, we add a new device.
    table.addDevice(device2)
    table.addTestCase(device2, AndroidTestCase("testid1", "method1", "class1", "package1", AndroidTestCaseResult.SKIPPED))
    table.addTestCase(device2, AndroidTestCase("testid2", "method2", "class2", "package2", AndroidTestCaseResult.SKIPPED))

    // Again, make sure the test method1 is still being selected.
    assertThat(table.getTableViewForTesting().selectedObject?.methodName).isEqualTo("method1")
  }

  @Test
  fun tableShouldImplementDataProviderAndProvideJavaPsiElementForSelectedRow() {
    val mockAndroidTestSourceScope = mock<GlobalSearchScope>()
    val mockPsiClass = mock<PsiClass>()
    val mockJvmMethod = mock<JvmMethod>()
    `when`(mockTestArtifactSearchScopes.androidTestSourceScope).thenReturn(mockAndroidTestSourceScope)
    `when`(mockJavaPsiFacade.findClasses(eq("mytestpackage.mytestclass"), eq(mockAndroidTestSourceScope))).thenReturn(arrayOf(mockPsiClass))
    `when`(mockPsiClass.findMethodsByName(eq("myTestMethodName"))).thenReturn(arrayOf(mockJvmMethod))
    `when`(mockJvmMethod.name).thenReturn("myTestMethodName")

    val table = AndroidTestResultsTableView(mockListener, mockJavaPsiFacade, mockTestArtifactSearchScopes)
    assertThat(table.getTableViewForTesting()).isInstanceOf(DataProvider::class.java)

    val device1 = device("deviceId1", "deviceName1")
    table.addTestCase(device1, AndroidTestCase("testid1", "myTestMethodName", "mytestclass", "mytestpackage"))

    // Select the test case 1.
    table.getTableViewForTesting().setColumnSelectionInterval(0, 0)
    table.getTableViewForTesting().selectionModel.setSelectionInterval(2, 2)

    val data = (table.getTableViewForTesting() as DataProvider).getData(CommonDataKeys.PSI_ELEMENT.name)
    assertThat(data).isInstanceOf(JvmMethod::class.java)
    assertThat((data as JvmMethod).name).isEqualTo("myTestMethodName")

    // Clear the selection.
    table.clearSelection()
    assertThat((table.getTableViewForTesting() as DataProvider).getData(CommonDataKeys.PSI_ELEMENT.name)).isNull()
  }

  @Test
  fun doubleClickOnTableShouldOpenTestSourceCode() {
    val mockDataProvider = mock<DataProvider>()
    TestApplicationManager.getInstance().setDataProvider(mockDataProvider, disposableRule.disposable)

    val table = AndroidTestResultsTableView(mockListener, mockJavaPsiFacade, mockTestArtifactSearchScopes)
    val tableView = table.getTableViewForTesting()

    table.addTestCase(device("deviceId1", "deviceName1"),
                      AndroidTestCase("testid1", "myTestMethodName", "mytestclass", "mytestpackage"))

    // Double click on the table.
    val doubleClickEvent = MouseEvent(tableView, 0, 0, 0, 0, 0, /*clickCount=*/2, false)
    tableView.mouseListeners.forEach { it.mouseClicked(doubleClickEvent) }

    // We cannot directly check whether or not the source code is opened, so we test it indirectly
    // by verifying that the navigatable array lookup is made.
    verify(mockDataProvider).getData(eq(CommonDataKeys.NAVIGATABLE_ARRAY.name))
  }

  // Workaround for Kotlin nullability check.
  // ArgumentMatchers.argThat returns null for interface types.
  private fun argThat(matcher: (AndroidTestResults) -> Boolean): AndroidTestResults {
    ArgumentMatchers.argThat(matcher)
    return object : AndroidTestResults {
      override val methodName: String = ""
      override val className: String = ""
      override val packageName: String = ""
      override fun getTestCaseResult(device: AndroidDevice): AndroidTestCaseResult? = null
      override fun getTestResultSummary(): AndroidTestCaseResult = AndroidTestCaseResult.SCHEDULED
      override fun getTestResultSummaryText(): String = ""
      override fun getResultStats(): AndroidTestResultStats = AndroidTestResultStats()
      override fun getResultStats(device: AndroidDevice): AndroidTestResultStats = AndroidTestResultStats()
      override fun getLogcat(device: AndroidDevice): String = ""
      override fun getErrorStackTrace(device: AndroidDevice): String = ""
      override fun getBenchmark(device: AndroidDevice): String = ""
      override fun getRetentionSnapshot(device: AndroidDevice): File? = null
    }
  }

  private fun device(id: String, name: String): AndroidDevice {
    return AndroidDevice(id, name, AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(28))
  }

  private fun TreeTableView.getItem(index: Int): AndroidTestResults {
    return getValueAt(index, 0) as AndroidTestResults
  }

  private val TreeTableView.selectedObject: AndroidTestResults?
    get() = selection?.firstOrNull() as? AndroidTestResults
}