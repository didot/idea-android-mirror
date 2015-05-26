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
package com.android.tools.idea.editors.hprof;

import com.android.tools.idea.editors.hprof.tables.InstanceReferenceTree;
import com.android.tools.idea.editors.hprof.tables.InstancesTree;
import com.android.tools.idea.editors.hprof.tables.SelectionModel;
import com.android.tools.idea.editors.hprof.tables.classtable.ClassTable;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Snapshot;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class HprofViewPanel implements Disposable {
  private static final int DIVIDER_WIDTH = 4;
  @NotNull private JPanel myContainer;
  @NotNull private SelectionModel mySelectionModel;

  public HprofViewPanel(@NotNull final Project project, @NotNull HprofEditor editor, @NotNull final Snapshot snapshot) {
    JBPanel treePanel = new JBPanel(new BorderLayout());
    treePanel.setBackground(JBColor.background());

    assert (snapshot.getHeaps().size() > 0);
    Heap currentHeap = null;
    for (Heap heap : snapshot.getHeaps()) {
      if ("app".equals(heap.getName())) {
        currentHeap = heap;
        break;
      }
      else if (currentHeap == null) {
        currentHeap = heap;
      }
    }

    if (currentHeap == null) {
      editor.setInvalid();
      return;
    }
    mySelectionModel = new SelectionModel(currentHeap);

    final InstanceReferenceTree referenceTree = new InstanceReferenceTree(mySelectionModel);
    treePanel.add(referenceTree.getComponent(), BorderLayout.CENTER);

    final InstancesTree instancesTree = new InstancesTree(project, mySelectionModel);
    final ClassTable classTable = createClassTable(mySelectionModel);
    JBScrollPane classTableScrollPane = new JBScrollPane();
    classTableScrollPane.setViewportView(classTable);
    JBSplitter splitter = createNavigationSplitter(classTableScrollPane, instancesTree.getComponent());

    JBPanel classPanel = new JBPanel(new BorderLayout());
    classPanel.add(splitter, BorderLayout.CENTER);

    DefaultActionGroup group = new DefaultActionGroup(new ComboBoxAction() {
      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        DefaultActionGroup group = new DefaultActionGroup();
        for (final Heap heap : snapshot.getHeaps()) {
          if ("default".equals(heap.getName()) && heap.getClasses().isEmpty() && heap.getInstances().isEmpty()) {
            continue;
          }
          group.add(new AnAction(heap.getName() + " heap") {
            @Override
            public void actionPerformed(AnActionEvent e) {
              mySelectionModel.setHeap(heap);
            }
          });
        }
        return group;
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        getTemplatePresentation().setText(mySelectionModel.getHeap().getName() + " heap");
        e.getPresentation().setText(mySelectionModel.getHeap().getName() + " heap");
      }
    });

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    classPanel.add(toolbar.getComponent(), BorderLayout.NORTH);

    JBSplitter mainSplitter = new JBSplitter(true);
    mainSplitter.setFirstComponent(classPanel);
    mainSplitter.setSecondComponent(treePanel);
    mainSplitter.setDividerWidth(DIVIDER_WIDTH);

    myContainer = new JPanel(new BorderLayout());
    myContainer.add(mainSplitter);

    // TODO Determine if the processing of hprof is good enough, and integrate this call if it is.
    classTable.notifyDominatorsComputed();
  }

  @NotNull
  private ClassTable createClassTable(@NotNull SelectionModel selectionModel) {
    final ClassTable classTable = new ClassTable(selectionModel);
    classTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent mouseEvent) {
        super.mousePressed(mouseEvent);
        int row = classTable.getSelectedRow();
        if (row >= 0) {
          int modelRow = classTable.getRowSorter().convertRowIndexToModel(row);
          ClassObj classObj = (ClassObj)classTable.getModel().getValueAt(modelRow, 0);
          mySelectionModel.setClassObj(classObj);
        }
      }
    });
    return classTable;
  }

  @NotNull
  public static JBSplitter createNavigationSplitter(@Nullable JComponent leftPanelContents, @Nullable JComponent rightPanelContents) {
    JBPanel navigationPanel = new JBPanel(new BorderLayout());
    navigationPanel.setBackground(JBColor.background());
    if (leftPanelContents != null) {
      navigationPanel.add(leftPanelContents, BorderLayout.CENTER);
    }

    JBPanel contextInformationPanel = new JBPanel(new BorderLayout());
    contextInformationPanel.setBackground(JBColor.background());
    if (rightPanelContents != null) {
      contextInformationPanel.add(rightPanelContents, BorderLayout.CENTER);
    }

    JBSplitter navigationSplitter = new JBSplitter(false);
    navigationSplitter.setFirstComponent(navigationPanel);
    navigationSplitter.setSecondComponent(contextInformationPanel);
    navigationSplitter.setDividerWidth(DIVIDER_WIDTH);

    return navigationSplitter;
  }

  @NotNull
  public JPanel getComponent() {
    return myContainer;
  }

  @Override
  public void dispose() {

  }
}
