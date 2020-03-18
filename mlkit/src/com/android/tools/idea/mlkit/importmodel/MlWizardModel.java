
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
package com.android.tools.idea.mlkit.importmodel;

import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

/**
 * {@link WizardModel} that contains model location to import.
 */
public class MlWizardModel extends WizardModel {

  /**
   * The Directory of new ml folder
   */
  private final File myMlDirectory;
  private final Project myProject;

  public final StringValueProperty sourceLocation = new StringValueProperty();

  public MlWizardModel(@NotNull File mlDirectory, @NotNull Project project) {
    myMlDirectory = mlDirectory;
    myProject = project;
  }

  @Override
  protected void handleFinished() {
    VirtualFile fromFile = VfsUtil.findFileByIoFile(new File(sourceLocation.get()), false);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          VirtualFile toDir = VfsUtil.createDirectoryIfMissing(myMlDirectory.getAbsolutePath());
          if (fromFile != null && toDir != null) {
            VirtualFile virtualFile = VfsUtilCore.copyFile(this, fromFile, toDir);
            FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
            if (fileEditorManager != null) {
              fileEditorManager.openFile(virtualFile, true);
            }
          }
        }
        catch (IOException e) {
          Logger.getInstance(MlWizardModel.class).error(String.format("Error copying %s to %s", fromFile, myMlDirectory), e);
        }
      }
    });
  }
}
