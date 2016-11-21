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
package com.android.tools.profilers.network;

import com.android.tools.adtui.ProportionalLayout;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.labels.BoldLabel;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.panels.NonOpaquePanel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * View to display a single network request and its response's detailed information.
 */
public class ConnectionDetailsView extends JPanel {

  private final Splitter myResponseSplitter;
  private final JPanel myResponsePanel;
  private final JPanel myFieldsPanel;

  public ConnectionDetailsView() {
    myResponseSplitter = new Splitter(true, 0.8f);
    myResponsePanel = new JPanel(new BorderLayout());
    myResponseSplitter.setFirstComponent(myResponsePanel);
    myFieldsPanel = new JPanel(ProportionalLayout.fromString("Fit,20px,*"));
    myResponseSplitter.setSecondComponent(myFieldsPanel);

    setLayout(new BorderLayout());
    add(myResponseSplitter, BorderLayout.CENTER);

    NonOpaquePanel headPanel = new NonOpaquePanel(new BorderLayout());
    add(headPanel, BorderLayout.NORTH);

    NonOpaquePanel titleLabelPanel = new NonOpaquePanel();
    titleLabelPanel.setLayout(new BoxLayout(titleLabelPanel, BoxLayout.X_AXIS));
    titleLabelPanel.add(new JLabel("Response"));
    headPanel.add(titleLabelPanel, BorderLayout.WEST);

    IconButton close = new IconButton("Close", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered);
    InplaceButton closeButton = new InplaceButton(close, e -> this.update((HttpData) null));
    headPanel.add(closeButton, BorderLayout.EAST);
  }

  /**
   * Updates the view to show given data. If given {@code httpData} is not null, show the details and set the view to be visible;
   * otherwise, clears the view and set view to be invisible.
   */
  public void update(@Nullable HttpData httpData) {
    setBackground(JBColor.background());
    myResponsePanel.removeAll();
    myFieldsPanel.removeAll();

    if (httpData != null) {
      VirtualFile payloadVirtualFile = httpData.getHttpResponsePayloadFile() != null
                                       ? LocalFileSystem.getInstance().findFileByIoFile(httpData.getHttpResponsePayloadFile()) : null;
      if (payloadVirtualFile != null) {
        // TODO: Find proper project to refactor this.
        Project project = ProjectManager.getInstance().getDefaultProject();
        FileEditorProvider editorProvider = FileEditorProviderManager.getInstance().getProviders(project, payloadVirtualFile)[0];
        FileEditor editor = editorProvider.createEditor(project, payloadVirtualFile);
        myResponsePanel.add(editor.getComponent(), BorderLayout.CENTER);
      }

      int row = 0;
      myFieldsPanel.add(new BoldLabel("Request"), new ProportionalLayout.Constraint(row, 0));
      HyperlinkLabel urlLabel = new HyperlinkLabel(httpData.getUrl());
      urlLabel.setHyperlinkTarget(httpData.getUrl());
      myFieldsPanel.add(urlLabel, new ProportionalLayout.Constraint(row, 2));
      Map<String, String> responseFields = httpData.getHttpResponseFields();
      if (responseFields != null && responseFields.containsKey(HttpData.FIELD_CONTENT_TYPE)) {
        row++;
        myFieldsPanel.add(new BoldLabel("Content type"), new ProportionalLayout.Constraint(row, 0));
        String contentType = responseFields.get(HttpData.FIELD_CONTENT_TYPE);
        // Content type looks like "type/subtype;" or "type/subtype; parameters".
        // Always convert to "type"
        contentType = contentType.split(";")[0];
        myFieldsPanel.add(new JLabel(contentType), new ProportionalLayout.Constraint(row, 2));
      }

      myResponseSplitter.repaint();
    }
    setVisible(httpData != null);
    revalidate();
  }
}
