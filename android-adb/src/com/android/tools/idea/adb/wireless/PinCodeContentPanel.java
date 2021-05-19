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

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class PinCodeContentPanel {
  @NotNull private JPanel myRootComponent;
  @NotNull private JPanel myEmptyPanel;
  @NotNull private JPanel myDevicesPanel;
  @NotNull private JPanel myDevicesHeaderPanel;
  @NotNull private JPanel myDeviceList;
  @NotNull private JBScrollPane myDeviceListScrollPane;
  @NotNull List<PinCodeDevicePanel> myPanels = new ArrayList<>();

  public PinCodeContentPanel() {
    myDeviceList.setLayout(new VerticalFlowLayout());

    myEmptyPanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myDevicesHeaderPanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myDeviceListScrollPane.setBorder(JBUI.Borders.empty());
    myDeviceList.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    EditorPaneUtils.setTitlePanelBorder(myDevicesHeaderPanel);
  }

  @NotNull
  public JComponent getComponent() {
    return myRootComponent;
  }

  public void showDevices(@NotNull List<@NotNull MdnsService> services, @NotNull Consumer<MdnsService> pinCodePairInvoked) {
    if (services.isEmpty()) {
      myEmptyPanel.setVisible(true);
      myDevicesPanel.setVisible(false);
      myDeviceList.removeAll();
      myDeviceList.revalidate();
      myDeviceList.repaint();
      myPanels.clear();
    } else {
      myEmptyPanel.setVisible(false);
      myDevicesPanel.setVisible(true);
      boolean needRepaint = false;

      // Keep existing panels & add add new panels for new devices
      for (MdnsService service : services) {
        if (isPanelPresent(myPanels, service)) {
          continue;
        }
        PinCodeDevicePanel devicePanel = new PinCodeDevicePanel(service, () -> pinCodePairInvoked.accept(service));
        myDeviceList.add(devicePanel.getComponent());
        myPanels.add(devicePanel);
        needRepaint = true;
      }

      // Remove panels for devices that are gone
      List<Integer> indicesToRemove = new ArrayList<>();
      int index = 0;
      for (PinCodeDevicePanel panel : myPanels) {
        if (isPanelDeleted(services, panel)) {
          indicesToRemove.add(index);
        }
        index++;
      }
      for (int i = indicesToRemove.size() - 1; i >= 0; i--) {
        int indexToRemove = indicesToRemove.get(i);
        myPanels.remove(indexToRemove);
        myDeviceList.remove(indexToRemove);
        needRepaint = true;
      }

      if (needRepaint) {
        myDeviceList.revalidate();
        myDeviceList.repaint();
      }

      // Assert various lists are equivalent
      assert myPanels.size() == myDeviceList.getComponentCount();
      assert services.size() == myDeviceList.getComponentCount();
      for(int i = 0; i < myPanels.size(); i++) {
        // Reference equality (since components are always updated together)
        assert myPanels.get(i).getComponent() == myDeviceList.getComponent(i);
        // Value equality (since service instances are coming from external source)
        assert myPanels.get(i).getMdnsService().equals(services.get(i));
      }
    }
  }

  private static boolean isPanelDeleted(@NotNull List<MdnsService> services, @NotNull PinCodeDevicePanel panel) {
    //TODO: Add test that MdnsService implements value equality
    return services.stream().noneMatch(service -> service.equals(panel.getMdnsService()));
  }

  private static boolean isPanelPresent(@NotNull List<PinCodeDevicePanel> panels,
                                        @NotNull MdnsService device) {
    //TODO: Add test that MdnsService implements value equality
    return panels.stream().anyMatch(panel -> panel.getMdnsService().equals(device));
  }

  private void createUIComponents() {
    myDeviceListScrollPane = new JBScrollPane(0);
  }
}
