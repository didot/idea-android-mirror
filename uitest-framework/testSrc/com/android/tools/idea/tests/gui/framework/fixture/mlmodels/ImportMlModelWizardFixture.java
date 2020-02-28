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
package com.android.tools.idea.tests.gui.framework.fixture.mlmodels;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.adtui.ASGallery;
import com.android.tools.idea.npw.module.ModuleGalleryEntry;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureAndroidModuleStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureDynamicFeatureStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureLibraryStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureNewModuleFromJarStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import javax.swing.JDialog;
import javax.swing.JTextField;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class ImportMlModelWizardFixture extends AbstractWizardFixture<ImportMlModelWizardFixture> {

  public static ImportMlModelWizardFixture find(IdeFrameFixture ideFrame) {
    JDialog dialog = waitUntilShowing(ideFrame.robot(), Matchers.byTitle(JDialog.class, "Import TensorFlow Lite model"));
    return new ImportMlModelWizardFixture(ideFrame, dialog);
  }

  private final IdeFrameFixture myIdeFrameFixture;

  private ImportMlModelWizardFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog dialog) {
    super(ImportMlModelWizardFixture.class, ideFrameFixture.robot(), dialog);
    myIdeFrameFixture = ideFrameFixture;
  }

  public ChooseMlModelStepFixture getImportModelStep() {
    return new ChooseMlModelStepFixture(this, target().getRootPane());
  }

  @NotNull
  public IdeFrameFixture clickFinish() {
    super.clickFinish(Wait.seconds(2));

    return myIdeFrameFixture;
  }
}
