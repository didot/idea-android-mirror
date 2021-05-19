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
package com.android.tools.idea.adb.wireless;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.AsyncProcessIcon;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class PinCodePanel {
  @NotNull private final Consumer<MdnsService> myPinCodePairInvoked;
  @NotNull private final PinCodeContentPanel myContentPanel;
  @NotNull private final JBLoadingPanel myLoadingPanel;
  @NotNull private JBLabel myFirstLineLabel;
  @NotNull private JBLabel mySecondLineLabel;
  @NotNull private JPanel myRootComponent;
  @NotNull private JPanel myContentPanelContainer;

  public PinCodePanel(@NotNull Disposable parentDisposable, @NotNull Consumer<MdnsService> pinCodePairInvoked) {
    myPinCodePairInvoked = pinCodePairInvoked;
    myContentPanelContainer.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myRootComponent.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myFirstLineLabel.setForeground(UIColors.PAIRING_HINT_LABEL);
    mySecondLineLabel.setForeground(UIColors.PAIRING_HINT_LABEL);

    myContentPanel = new PinCodeContentPanel();
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), panel -> new LoadingDecorator(panel, parentDisposable, -1) {
      @Override
      protected NonOpaquePanel customizeLoadingLayer(JPanel parent, JLabel text, AsyncProcessIcon icon) {
        Font font = text.getFont();
        final NonOpaquePanel panel = super.customizeLoadingLayer(parent, text, icon);
        text.setFont(font);
        return panel;
      }
    });
    myLoadingPanel.add(myContentPanel.getComponent(), BorderLayout.CENTER);
    myLoadingPanel.setLoadingText("Searching for devices in pairing mode");
    myContentPanelContainer.add(myLoadingPanel, BorderLayout.CENTER);

    showAvailableServices(new ArrayList<>());
  }

  public void showAvailableServices(@NotNull List<@NotNull MdnsService> devices) {
    if (devices.isEmpty()) {
      myLoadingPanel.startLoading();
    } else {
      myLoadingPanel.stopLoading();
    }
    myContentPanel.showDevices(devices, myPinCodePairInvoked);
  }

  @NotNull
  public JComponent getComponent() {
    return myRootComponent;
  }
}
