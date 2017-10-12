/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard;

import com.android.testutils.TestUtils;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.config.InstallerData;
import com.android.tools.idea.welcome.install.ComponentCategory;
import com.android.tools.idea.welcome.wizard.deprecated.SdkComponentsStep;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import com.android.tools.idea.wizard.dynamic.SingleStepPath;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

public final class FirstRunWizardTest extends AndroidTestBase {
  public static final Key<Boolean> KEY_TRUE = ScopedStateStore.createKey("true", ScopedStateStore.Scope.WIZARD, Boolean.class);
  public static final Key<Boolean> KEY_FALSE = ScopedStateStore.createKey("false", ScopedStateStore.Scope.WIZARD, Boolean.class);
  public static final Key<Integer> KEY_INTEGER = ScopedStateStore.createKey("42", ScopedStateStore.Scope.WIZARD, Integer.class);

  private static <T> Key<T> createKey(Class<T> clazz) {
    return ScopedStateStore.createKey(clazz.getName(), ScopedStateStore.Scope.STEP, clazz);
  }

  private void assertPagesVisible(@Nullable InstallerData data, boolean isComponentsStepVisible, boolean hasAndroidSdkPath) {
    InstallerData.set(data);
    FirstRunWizardMode mode = data == null ? FirstRunWizardMode.NEW_INSTALL : FirstRunWizardMode.INSTALL_HANDOFF;
    assertVisible(
      new SdkComponentsStep(new ComponentCategory("test", "test"), KEY_TRUE, createKey(String.class), mode, getTestRootDisposable()),
      data, isComponentsStepVisible);

    if (data != null) {
      assertEquals(String.valueOf(data.toString()), hasAndroidSdkPath, data.hasValidSdkLocation());
    }
  }

  private void assertVisible(DynamicWizardStep step, @Nullable InstallerData data, boolean expected) {
    assertEquals(String.format("Step: %s, data: %s", step.getClass(), data), expected, isStepVisible(step));
  }

  public boolean isStepVisible(@NotNull DynamicWizardStep step) {
    SingleStepWizard wizard = new SingleStepWizard(step);
    disposeOnTearDown(wizard.getDisposable());
    wizard.init();
    return step.isStepVisible();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
      IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    finally {
      super.tearDown();
    }
  }

  public void testStepsVisibility() {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    File wrongPath = new File("/$@@  \"\'should/not/exist");
    File androidHome = TestUtils.getSdk();

    assertPagesVisible(null, true, false);

    InstallerData correctData = new InstallerData(null, androidHome, true, "timestamp", "1234");
    assertPagesVisible(correctData, false, true);

    InstallerData noAndroidSdkData = new InstallerData(null, null, true, "timestamp", "1234");
    assertPagesVisible(noAndroidSdkData, true, false);

    InstallerData noJdkData = new InstallerData(null, androidHome, true, "timestamp", "1234");
    assertPagesVisible(noJdkData, false, true);

    InstallerData noInstallAndroidData = new InstallerData(androidHome, androidHome, true, "timestamp", "1234");
    assertPagesVisible(noInstallAndroidData, false, true);

    InstallerData bogusPathsData = new InstallerData(wrongPath, wrongPath, true, "timestamp", "1234");
    assertPagesVisible(bogusPathsData, true, false);
  }

  /**
   * Wizard for testing a single step.
   */
  private static final class SingleStepWizard extends DynamicWizard {
    @NotNull private final DynamicWizardStep myStep;

    public SingleStepWizard(@NotNull DynamicWizardStep step) {
      super(null, null, "Single Step Wizard");
      myStep = step;
    }

    @Override
    public void init() {
      myState.put(KEY_TRUE, true);
      myState.put(KEY_FALSE, false);
      myState.put(KEY_INTEGER, 42);
      addPath(new SingleStepPath(myStep));
      // Need to have at least one visible step
      addPath(new SingleStepPath(new DynamicWizardStep() {
        private final JLabel myLabel = new JLabel();

        @Override
        public void init() {

        }

        @NotNull
        @Override
        public JComponent createStepBody() {
          return myLabel;
        }

        @Nullable
        @Override
        public JLabel getMessageLabel() {
          return myLabel;
        }

        @NotNull
        @Override
        public String getStepName() {
          return "Always visible wizard step";
        }

        @NotNull
        @Override
        protected String getStepTitle() {
          return "test step";
        }

        @Nullable
        @Override
        protected String getStepDescription() {
          return null;
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
          return myLabel;
        }
      }));
      super.init();
    }

    @Override
    public void performFinishingActions() {
      // Nothing.
    }

    @Override
    protected String getProgressTitle() {
      return "test";
    }

    @Override
    protected String getWizardActionDescription() {
      return "Test Wizard";
    }
  }
}