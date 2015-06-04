/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.ptable.PTable;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Iterables;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class NlPropertiesPanel extends JPanel implements ChangeListener {
  private final PTable myTable;
  private final ScreenView myScreenView;
  private final NlPropertiesModel myModel;
  private MergingUpdateQueue myUpdateQueue;
  private JBLabel mySelectedComponentLabel;

  public NlPropertiesPanel(@NotNull ScreenView screenView) {
    super(new BorderLayout());
    setOpaque(true);
    setFocusable(true);
    setRequestFocusEnabled(true);
    setBackground(UIUtil.TRANSPARENT_COLOR);

    myScreenView = screenView;

    myModel = new NlPropertiesModel();
    myTable = new PTable(myModel);

    myTable.getEmptyText().setText("No selected component");

    JPanel headerPanel = createHeaderPanel();
    add(headerPanel, BorderLayout.NORTH);
    add(new JBScrollPane(myTable), BorderLayout.CENTER);

    myScreenView.getSelectionModel().addListener(this);
  }

  @NotNull
  private JPanel createHeaderPanel() {
    JBPanel panel = new JBPanel(new BorderLayout());

    mySelectedComponentLabel = new JBLabel("");
    panel.add(mySelectedComponentLabel, BorderLayout.CENTER);

    ShowExpertProperties showExpertAction = new ShowExpertProperties(myModel);
    ActionButton showExpertButton = new ActionButton(showExpertAction, showExpertAction.getTemplatePresentation(), ActionPlaces.UNKNOWN,
                                                     ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    panel.add(showExpertButton, BorderLayout.LINE_END);

    return panel;
  }

  @Override
  public void stateChanged(ChangeEvent e) {
    myTable.setPaintBusy(true);
    getUpdateQueue().queue(new Update("updateProperties") {
      @Override
      public void run() {
        myModel.update(myScreenView.getSelectionModel().getSelection(), new Runnable() {
          @Override
          public void run() {
            // TODO: handle multiple selections
            final NlComponent first = Iterables.getFirst(myScreenView.getSelectionModel().getSelection(), null);
            if (first != null) {
              mySelectedComponentLabel.setText(first.getTagName());
            }
            myTable.setPaintBusy(false);
          }
        });
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }

  @NotNull
  private MergingUpdateQueue getUpdateQueue() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myUpdateQueue == null) {
      myUpdateQueue = new MergingUpdateQueue("android.layout.propertysheet", 250, true, null, myScreenView.getModel(), null,
                                             Alarm.ThreadToUse.SWING_THREAD);
    }
    return myUpdateQueue;
  }
}
