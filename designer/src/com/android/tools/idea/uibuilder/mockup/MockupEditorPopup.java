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
package com.android.tools.idea.uibuilder.mockup;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Build and display the Mockup Editor dialog
 */
public class MockupEditorPopup {
  public static final String TITLE = "Mockup Editor";
  private static final double RELATIVE_SIZE_TO_SOURCE = 0.90;
  private static final IconButton CANCEL_BUTTON = new IconButton("Close", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered);
  private static JBPopup POPUP_INSTANCE = null;
  private final ScreenView myScreenView;
  private final Mockup myMockup;
  NlModel myModel;

  // Form generated components (Do not removed if referenced in the form)
  private TextFieldWithBrowseButton myFileChooser;
  private MockupInteractionPanel myMockupInteractionPanel;
  private JSlider mySlider;
  private JPanel myContentPane;
  private JCheckBox myShowGuideline;
  private JButton myOkButton;

  public MockupEditorPopup(ScreenView screenView, Mockup mockup, NlModel model) {
    myScreenView = screenView;
    myModel = model;
    myMockup = mockup;

    initFileChooserText();
    initFileChooserActionListener();
    initSlider();
    initShowGuidelineCheckBox();
    initCreateSelectedGuidelineButton(mockup, model);
  }

  private void initCreateSelectedGuidelineButton(Mockup mockup, NlModel model) {
    myOkButton.addActionListener(e -> {
      createSelectedGuidelines(mockup, model);
    });
  }

  private void createSelectedGuidelines(Mockup mockupComponentAttributes, final NlModel model) {
    final List<MockupGuide> selectedGuidelines = myMockupInteractionPanel.getSelectedGuidelines();
    final NlComponent parent = mockupComponentAttributes.getComponent();
    final WriteCommandAction action = new WriteCommandAction(model.getProject(), "Create Guidelines", model.getFile()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {

        // Create all corresponding NlComponent Guidelines from the selected guidelines list
        for (int i = 0; i < selectedGuidelines.size(); i++) {
          selectedGuidelines.get(i).createConstraintGuideline(myScreenView, model, parent);
        }
      }

    };
    action.execute();
  }

  private void initShowGuidelineCheckBox() {
    myShowGuideline.addChangeListener(e -> myMockupInteractionPanel.setShowGuideline(((JCheckBox)e.getSource()).isSelected()));
    myMockupInteractionPanel.setShowGuideline(myShowGuideline.isSelected());
  }

  private void initSlider() {
    mySlider.setValue(Math.round(myMockup.getAlpha() * mySlider.getMaximum()));
    mySlider.addMouseListener(new MouseAdapter() {

      @Override
      public void mouseReleased(MouseEvent e) {
        MockupFileHelper.writeOpacityToXML(mySlider.getValue() / (float)mySlider.getMaximum(), myMockup.getComponent());
      }

    });
    mySlider.addChangeListener(e -> {
      final JSlider source = (JSlider)e.getSource();
      myMockup.setAlpha(source.getValue() / (float)source.getMaximum());
      myContentPane.repaint();
    });
  }

  private void initFileChooserActionListener() {
    myFileChooser.addActionListener(e -> {
      if (myMockup == null) {
        return;
      }
      final FileChooserDescriptor descriptor = MockupFileHelper.getFileChooserDescriptor();
      VirtualFile selectedFile = myMockup.getVirtualFile();

      FileChooser.chooseFile(descriptor, null, myContentPane, selectedFile, (virtualFile) -> {
        MockupFileHelper.writeFileNameToXML(virtualFile, myMockup.getComponent());
        myFileChooser.setText(virtualFile.getName());
      });
    });
  }

  private void initFileChooserText() {
    if (myMockup != null) {
      final VirtualFile virtualFile = myMockup.getVirtualFile();
      if (virtualFile != null) {
        myFileChooser.setText(virtualFile.getName());
      }
    }
  }

  /**
   * Create a popup showing the tools to edit the mockup of the selected component
   *
   * @param designSurface
   * @param nlModel
   */
  public static void create(DesignSurface designSurface, NlModel nlModel) {
    // Close any pop-up already opened
    if (POPUP_INSTANCE != null) {
      POPUP_INSTANCE.cancel();
    }

    // Get the selected component if it exist or get the root component
    List<NlComponent> selection = nlModel.getSelectionModel().getSelection();
    NlComponent component = null;
    if (selection.isEmpty()) {
      selection = nlModel.getComponents();
      if (!selection.isEmpty()) {
        component = selection.get(0).getRoot();
      }
    }
    else {
      component = selection.get(0);
    }

    // Do not show the popup if nothing is selected
    if (component == null) {
      return;
    }
    final Mockup mockup = Mockup.create(component, true);
    if (mockup == null) {
      return;
    }

    final ScreenView screenView = new ScreenView(designSurface, ScreenView.ScreenViewType.BLUEPRINT, nlModel);
    final MockupEditorPopup mockupEditorPopup = new MockupEditorPopup(screenView, mockup, component.getModel());
    final Dimension minSize = new Dimension((int)Math.round(designSurface.getWidth() * RELATIVE_SIZE_TO_SOURCE),
                                            (int)Math.round(designSurface.getHeight() * RELATIVE_SIZE_TO_SOURCE));

    JBPopup builder = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(mockupEditorPopup.myContentPane, mockupEditorPopup.myContentPane)
      .setTitle(TITLE)
      .setResizable(true)
      .setMovable(true)
      .setMinSize(minSize)
      .setRequestFocus(true)
      .setCancelOnClickOutside(false)
      .setLocateWithinScreenBounds(true)
      .setShowShadow(true)
      .setCancelOnWindowDeactivation(false)
      .setCancelButton(CANCEL_BUTTON)
      .createPopup();

    // Center the popup in the design surface
    RelativePoint point = new RelativePoint(
      designSurface,
      new Point(
        (int)Math.round(designSurface.getWidth() / 2 - minSize.getWidth() / 2),
        (int)Math.round(designSurface.getHeight() / 2 - minSize.getHeight() / 2))
    );
    builder.show(point);

    POPUP_INSTANCE = builder;
  }

  private void createUIComponents() {
    myMockupInteractionPanel = new MockupInteractionPanel(myScreenView, myMockup);
  }
}
