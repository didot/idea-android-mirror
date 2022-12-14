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
package com.android.tools.idea.apk.viewer.arsc;

import com.google.common.collect.ImmutableList;
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceFile;
import com.google.devrel.gmscore.tools.apk.arsc.Chunk;
import com.google.devrel.gmscore.tools.apk.arsc.PackageChunk;
import com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk;
import com.google.devrel.gmscore.tools.apk.arsc.TypeSpecChunk;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.table.JBTable;
import java.awt.BorderLayout;
import java.util.Collection;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;

public class ResourceTablePanel {
  private JPanel myContainer;
  private ComboBox myPackageCombo;

  private Splitter mySplitter;
  private JBList myTypesList;
  private JBTable myResourceTypeTable;
  private SimpleColoredComponent myResourceTableHeader;

  public ResourceTablePanel(@NotNull BinaryResourceFile resourceFile) {
    List<Chunk> chunks = resourceFile.getChunks();
    if (chunks.isEmpty()) {
      throw new IllegalArgumentException("no chunks");
    }

    if (!(chunks.get(0) instanceof ResourceTableChunk)) {
      throw new IllegalArgumentException("no res table chunk");
    }

    ResourceTableChunk resourceTableChunk = (ResourceTableChunk)chunks.get(0);
    Collection<PackageChunk> packages = resourceTableChunk.getPackages();
    myPackageCombo.setModel(new CollectionComboBoxModel<>(ImmutableList.copyOf(packages)));
    myPackageCombo.setRenderer(SimpleListCellRenderer.create("", PackageChunk::getPackageName));

    assert packages.size() == 1;
    PackageChunk packageChunk = packages.stream().findFirst().get();

    myTypesList.setModel(new CollectionListModel<>(packageChunk.getTypeSpecChunks()));
    myTypesList.setCellRenderer(SimpleListCellRenderer.create("", TypeSpecChunk::getTypeName));
    myTypesList.addListSelectionListener(e -> {
      Object selectedValue = myTypesList.getSelectedValue();
      if (!(selectedValue instanceof TypeSpecChunk)) {
        return;
      }

      TypeSpecChunk typeSpecChunk = (TypeSpecChunk)selectedValue;
      myResourceTypeTable
        .setModel(new ResourceTypeTableModel(resourceTableChunk.getStringPool(), packageChunk, typeSpecChunk));

      myResourceTypeTable.getColumnModel().getColumn(0).setMinWidth(100); // resource id column
      myResourceTypeTable.getColumnModel().getColumn(1).setMinWidth(250); // resource name column

      int resourceCount = typeSpecChunk.getResourceCount();
      int configCount = packageChunk.getTypeChunks(typeSpecChunk.getId()).size();

      // Render a sentence like: "There [is|are] N layout resources across M configuration[s]."
      myResourceTableHeader.clear();
      myResourceTableHeader.append("There " + (resourceCount > 1 ? "are " : "is "));
      myResourceTableHeader.append(Integer.toString(resourceCount), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      myResourceTableHeader.append(" " + typeSpecChunk.getTypeName() + (resourceCount > 1 ? " resources" : " resource") + " across ");
      myResourceTableHeader.append(Integer.toString(configCount), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      myResourceTableHeader.append(" configuration" + (configCount > 1 ? "s" : ""));
    });
  }

  private void createUIComponents() {
    JBLabel label = new JBLabel("Resource Types");
    myTypesList = new JBList();

    JPanel resourceTypesPanel = new JPanel(new BorderLayout());
    resourceTypesPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
    resourceTypesPanel.add(label, BorderLayout.NORTH);
    resourceTypesPanel.add(ScrollPaneFactory.createScrollPane(myTypesList), BorderLayout.CENTER);

    myResourceTypeTable = new JBTable();
    myResourceTypeTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    myResourceTypeTable.getEmptyText().setText("No resource type selected.");

    myResourceTableHeader = new SimpleColoredComponent();

    JPanel resourceTablePanel = new JPanel(new BorderLayout());
    resourceTablePanel.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
    resourceTablePanel.add(myResourceTableHeader, BorderLayout.NORTH);
    resourceTablePanel.add(ScrollPaneFactory.createScrollPane(myResourceTypeTable), BorderLayout.CENTER);

    mySplitter = new OnePixelSplitter(false, 0.2f);
    mySplitter.setFirstComponent(resourceTypesPanel);
    mySplitter.setSecondComponent(resourceTablePanel);
  }

  public JComponent getPanel() {
    return myContainer;
  }
}
