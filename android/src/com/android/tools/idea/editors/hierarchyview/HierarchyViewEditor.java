/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.hierarchyview;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.io.IOException;

public class HierarchyViewEditor extends UserDataHolderBase implements FileEditor {
  private final VirtualFile myVirtualFile;
  private final Project myProject;
  private HierarchyViewEditorPanel myPanel;

  public HierarchyViewEditor(@NotNull Project project, @NotNull VirtualFile file) {
    myVirtualFile = file;
    myProject = project;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    if (myPanel == null) {
      LayoutInspectorContext context;
      try {
        context = new LayoutInspectorContext(new LayoutFileData(myVirtualFile));
      }
      catch (IOException e) {
        return new JLabel(e.getLocalizedMessage(), SwingConstants.CENTER);
      }
      myPanel = new HierarchyViewEditorPanel(this, myProject, context);
    }

    return myPanel;
  }

  @Override
  public void dispose() {
  }

  @NotNull
  @Override
  public String getName() {
    return "Hierarchy Viewer";
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void deselectNotify() {
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @Override
  public void selectNotify() {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @Override
  public boolean isValid() {
    return myVirtualFile.isValid();
  }
}
