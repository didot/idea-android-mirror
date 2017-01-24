/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.android.lint;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TargetBackend;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("idea/testData/android/lint")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class KotlinLintTestGenerated extends AbstractKotlinLintTest {
    @TestMetadata("alarm.kt")
    public void testAlarm() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/alarm.kt");
        doTest(fileName);
    }

    public void testAllFilesPresentInLint() throws Exception {
        KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("idea/testData/android/lint"), Pattern.compile("^(.+)\\.kt$"), TargetBackend.ANY, true);
    }

    @TestMetadata("apiCheck.kt")
    public void testApiCheck() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/apiCheck.kt");
        doTest(fileName);
    }

    @TestMetadata("callSuper.kt")
    public void testCallSuper() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/callSuper.kt");
        doTest(fileName);
    }

    @TestMetadata("closeCursor.kt")
    public void testCloseCursor() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/closeCursor.kt");
        doTest(fileName);
    }

    @TestMetadata("commitFragment.kt")
    public void testCommitFragment() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/commitFragment.kt");
        doTest(fileName);
    }

    @TestMetadata("javaPerformance.kt")
    public void testJavaPerformance() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/javaPerformance.kt");
        doTest(fileName);
    }

    @TestMetadata("javaScriptInterface.kt")
    public void testJavaScriptInterface() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/javaScriptInterface.kt");
        doTest(fileName);
    }

    @TestMetadata("layoutInflation.kt")
    public void testLayoutInflation() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/layoutInflation.kt");
        doTest(fileName);
    }

    @TestMetadata("log.kt")
    public void testLog() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/log.kt");
        doTest(fileName);
    }

    @TestMetadata("noInternationalSms.kt")
    public void testNoInternationalSms() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/noInternationalSms.kt");
        doTest(fileName);
    }

    @TestMetadata("overrideConcrete.kt")
    public void testOverrideConcrete() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/overrideConcrete.kt");
        doTest(fileName);
    }

    @TestMetadata("parcel.kt")
    public void testParcel() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/parcel.kt");
        doTest(fileName);
    }

    @TestMetadata("sdCardTest.kt")
    public void testSdCardTest() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/sdCardTest.kt");
        doTest(fileName);
    }

    @TestMetadata("setJavaScriptEnabled.kt")
    public void testSetJavaScriptEnabled() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/setJavaScriptEnabled.kt");
        doTest(fileName);
    }

    @TestMetadata("sharedPrefs.kt")
    public void testSharedPrefs() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/sharedPrefs.kt");
        doTest(fileName);
    }

    @TestMetadata("showDiagnosticsWhenFileIsRed.kt")
    public void testShowDiagnosticsWhenFileIsRed() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/showDiagnosticsWhenFileIsRed.kt");
        doTest(fileName);
    }

    @TestMetadata("sqlite.kt")
    public void testSqlite() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/sqlite.kt");
        doTest(fileName);
    }

    @TestMetadata("systemServices.kt")
    public void testSystemServices() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/systemServices.kt");
        doTest(fileName);
    }

    @TestMetadata("toast.kt")
    public void testToast() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/toast.kt");
        doTest(fileName);
    }

    @TestMetadata("valueOf.kt")
    public void testValueOf() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/valueOf.kt");
        doTest(fileName);
    }

    @TestMetadata("velocityTrackerRecycle.kt")
    public void testVelocityTrackerRecycle() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/velocityTrackerRecycle.kt");
        doTest(fileName);
    }

    @TestMetadata("viewConstructor.kt")
    public void testViewConstructor() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/viewConstructor.kt");
        doTest(fileName);
    }

    @TestMetadata("viewHolder.kt")
    public void testViewHolder() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/viewHolder.kt");
        doTest(fileName);
    }

    @TestMetadata("wrongAnnotation.kt")
    public void testWrongAnnotation() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/wrongAnnotation.kt");
        doTest(fileName);
    }

    @TestMetadata("wrongImport.kt")
    public void testWrongImport() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/wrongImport.kt");
        doTest(fileName);
    }

    @TestMetadata("wrongViewCall.kt")
    public void testWrongViewCall() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lint/wrongViewCall.kt");
        doTest(fileName);
    }
}
