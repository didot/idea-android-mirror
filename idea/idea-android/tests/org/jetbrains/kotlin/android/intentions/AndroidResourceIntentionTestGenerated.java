/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.android.intentions;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("idea/testData/android/resourceIntentions")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class AndroidResourceIntentionTestGenerated extends AbstractAndroidResourceIntentionTest {
    public void testAllFilesPresentInResourceIntentions() throws Exception {
        KotlinTestUtils.assertAllTestsPresentInSingleGeneratedClass(this.getClass(), new File("idea/testData/android/resourceIntentions"), Pattern.compile("^(.+)\\.test$"));
    }

    @TestMetadata("kotlinAndroidAddStringResource/activityExtension/activityExtension.test")
    public void testKotlinAndroidAddStringResource_activityExtension_ActivityExtension() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/resourceIntentions/kotlinAndroidAddStringResource/activityExtension/activityExtension.test");
        doTest(fileName);
    }

    @TestMetadata("kotlinAndroidAddStringResource/activityMethod/activityMethod.test")
    public void testKotlinAndroidAddStringResource_activityMethod_ActivityMethod() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/resourceIntentions/kotlinAndroidAddStringResource/activityMethod/activityMethod.test");
        doTest(fileName);
    }

    @TestMetadata("kotlinAndroidAddStringResource/classInActivity/classInActivity.test")
    public void testKotlinAndroidAddStringResource_classInActivity_ClassInActivity() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/resourceIntentions/kotlinAndroidAddStringResource/classInActivity/classInActivity.test");
        doTest(fileName);
    }

    @TestMetadata("kotlinAndroidAddStringResource/extensionLambda/extensionLambda.test")
    public void testKotlinAndroidAddStringResource_extensionLambda_ExtensionLambda() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/resourceIntentions/kotlinAndroidAddStringResource/extensionLambda/extensionLambda.test");
        doTest(fileName);
    }

    @TestMetadata("kotlinAndroidAddStringResource/function/function.test")
    public void testKotlinAndroidAddStringResource_function_Function() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/resourceIntentions/kotlinAndroidAddStringResource/function/function.test");
        doTest(fileName);
    }

    @TestMetadata("kotlinAndroidAddStringResource/innerClassInActivity/innerClassInActivity.test")
    public void testKotlinAndroidAddStringResource_innerClassInActivity_InnerClassInActivity() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/resourceIntentions/kotlinAndroidAddStringResource/innerClassInActivity/innerClassInActivity.test");
        doTest(fileName);
    }

    @TestMetadata("kotlinAndroidAddStringResource/innerViewInActivity/innerViewInActivity.test")
    public void testKotlinAndroidAddStringResource_innerViewInActivity_InnerViewInActivity() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/resourceIntentions/kotlinAndroidAddStringResource/innerViewInActivity/innerViewInActivity.test");
        doTest(fileName);
    }

    @TestMetadata("kotlinAndroidAddStringResource/objectInActivity/objectInActivity.test")
    public void testKotlinAndroidAddStringResource_objectInActivity_ObjectInActivity() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/resourceIntentions/kotlinAndroidAddStringResource/objectInActivity/objectInActivity.test");
        doTest(fileName);
    }

    @TestMetadata("kotlinAndroidAddStringResource/objectInActivityMethod/objectInActivityMethod.test")
    public void testKotlinAndroidAddStringResource_objectInActivityMethod_ObjectInActivityMethod() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/resourceIntentions/kotlinAndroidAddStringResource/objectInActivityMethod/objectInActivityMethod.test");
        doTest(fileName);
    }

    @TestMetadata("kotlinAndroidAddStringResource/objectInFunction/objectInFunction.test")
    public void testKotlinAndroidAddStringResource_objectInFunction_ObjectInFunction() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/resourceIntentions/kotlinAndroidAddStringResource/objectInFunction/objectInFunction.test");
        doTest(fileName);
    }

    @TestMetadata("kotlinAndroidAddStringResource/stringTemplate/stringTemplate.test")
    public void testKotlinAndroidAddStringResource_stringTemplate_StringTemplate() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/resourceIntentions/kotlinAndroidAddStringResource/stringTemplate/stringTemplate.test");
        doTest(fileName);
    }

    @TestMetadata("kotlinAndroidAddStringResource/viewExtensionActivityMethod/viewExtensionActivityMethod.test")
    public void testKotlinAndroidAddStringResource_viewExtensionActivityMethod_ViewExtensionActivityMethod() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/resourceIntentions/kotlinAndroidAddStringResource/viewExtensionActivityMethod/viewExtensionActivityMethod.test");
        doTest(fileName);
    }

    @TestMetadata("kotlinAndroidAddStringResource/viewMethod/viewMethod.test")
    public void testKotlinAndroidAddStringResource_viewMethod_ViewMethod() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/resourceIntentions/kotlinAndroidAddStringResource/viewMethod/viewMethod.test");
        doTest(fileName);
    }
}
