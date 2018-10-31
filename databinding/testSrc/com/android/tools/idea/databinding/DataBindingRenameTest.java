/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.databinding;

import static com.android.tools.idea.databinding.TestDataPaths.PROJECT_WITH_DATA_BINDING;
import static com.android.tools.idea.databinding.TestDataPaths.PROJECT_WITH_DATA_BINDING_ANDROID_X;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.ide.common.blame.Message;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.google.common.collect.Lists;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.refactoring.actions.RenameElementAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.util.ui.UIUtil;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test of rename refactoring involving Java language elements generated by Data Binding.
 */

@RunWith(Parameterized.class)
public class DataBindingRenameTest {
  @Rule
  public final AndroidGradleProjectRule myProjectRule = new AndroidGradleProjectRule();

  @Parameterized.Parameters(name = "{0}")
  public static List<DataBindingMode> getParameters() {
    return Lists.newArrayList(DataBindingMode.SUPPORT, DataBindingMode.ANDROIDX);
  }

  @NotNull
  private final DataBindingMode myDataBindingMode;

  public DataBindingRenameTest(@NotNull DataBindingMode mode) {
    myDataBindingMode = mode;
  }

  @Before
  public void setUp() {
    myProjectRule.getFixture().setTestDataPath(TestDataPaths.TEST_DATA_ROOT);
    myProjectRule.load(myDataBindingMode == DataBindingMode.SUPPORT ? PROJECT_WITH_DATA_BINDING : PROJECT_WITH_DATA_BINDING_ANDROID_X);
  }

  private void checkAndRename(String newName) {
    RenameElementAction action = new RenameElementAction();
    AnActionEvent e =
      new TestActionEvent(DataManager.getInstance().getDataContext(myProjectRule.getFixture().getEditor().getComponent()), action);
    action.update(e);
    assertTrue(e.getPresentation().isEnabled() && e.getPresentation().isVisible());
    // Note: This fails when trying to rename XML attribute values: Use myFixture.renameElementAtCaretUsingHandler() instead!
    myProjectRule.getFixture().renameElementAtCaret(newName);
    // Save the renaming changes to disk.
    saveAllDocuments();
  }

  private void saveAllDocuments() {
    new WriteCommandAction(myProjectRule.getProject()) {
      @Override
      protected void run(@NotNull Result result) {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    }.execute();
  }

  /**
   * Checks renaming of a resource IDs when a Java field generated from that resource by Data Binding is renamed.
   *
   * @see com.android.tools.idea.databinding.DataBindingRenamer
   */
  @Test
  public void assertRenameFieldDerivedFromResource() {
    EdtTestUtil.runInEdtAndWait(() -> {
      // Temporary fix until test model can detect dependencies properly.
      GradleInvocationResult assembleDebug = myProjectRule.invokeTasks(myProjectRule.getProject(), "assembleDebug");
      assertTrue(StringUtil.join(assembleDebug.getCompilerMessages(Message.Kind.ERROR), "\n"), assembleDebug.isBuildSuccessful());

      GradleSyncState syncState = GradleSyncState.getInstance(myProjectRule.getProject());
      assertFalse(syncState.isSyncNeeded().toBoolean());
      assertEquals(ModuleDataBinding.getInstance(myProjectRule.getAndroidFacet()).getDataBindingMode(),
                   myDataBindingMode);

      // Make sure that all file system events up to this point have been processed.
      VirtualFileManager.getInstance().syncRefresh();
      UIUtil.dispatchAllInvocationEvents();

      VirtualFile file =
        myProjectRule.getProject().getBaseDir()
          .findFileByRelativePath("app/src/main/java/com/android/example/appwithdatabinding/MainActivity.java");
      myProjectRule.getFixture().configureFromExistingVirtualFile(file);
      Editor editor = myProjectRule.getFixture().getEditor();
      String text = editor.getDocument().getText();
      int offset = text.indexOf("regularView");
      assertTrue(offset > 0);
      editor.getCaretModel().moveToOffset(offset);
      VirtualFile layoutFile = myProjectRule.getProject().getBaseDir().findFileByRelativePath("app/src/main/res/layout/activity_main.xml");
      String layoutText = VfsUtilCore.loadText(layoutFile);
      // Rename regularView to nameAfterRename in MainActivity.java.
      checkAndRename("nameAfterRename");
      // Check results.
      assertEquals(text.replace("regularView", "nameAfterRename"), VfsUtilCore.loadText(file));
      assertEquals(layoutText.replace("regular_view", "name_after_rename"), VfsUtilCore.loadText(layoutFile));
    });
  }
}
