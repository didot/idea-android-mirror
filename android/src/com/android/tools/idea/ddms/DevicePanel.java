/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.ddms;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.utils.Pair;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DevicePanel implements Disposable,
                                    AndroidDebugBridge.IClientChangeListener,
                                    AndroidDebugBridge.IDeviceChangeListener {
  private static final String NO_DEVICES = "No Connected Devices";
  private JPanel myPanel;
  private JComboBox myDevicesComboBox;
  private JBList myClientsList;

  private final DefaultComboBoxModel myComboBoxModel = new DefaultComboBoxModel();
  private final CollectionListModel<Client> myClientsListModel = new CollectionListModel<Client>();

  private final DeviceContext myDeviceContext;
  private final Project myProject;
  private AndroidDebugBridge myBridge;

  public DevicePanel(@NotNull Project project, @NotNull DeviceContext context) {
    myProject = project;
    myDeviceContext = context;
    Disposer.register(myProject, this);

    if (!AndroidSdkUtils.activateDdmsIfNecessary(project, new Computable<AndroidDebugBridge>() {
      @Nullable
      @Override
      public AndroidDebugBridge compute() {
        return AndroidSdkUtils.getDebugBridge(myProject);
      }
    })) {
      myBridge = null;
      return;
    }

    myBridge = AndroidSdkUtils.getDebugBridge(myProject);
    if (myBridge == null) {
      return;
    }

    myBridge.addDeviceChangeListener(this);
    myBridge.addClientChangeListener(this);

    initializeDeviceCombo();
    initializeClientsList();
  }

  private void initializeDeviceCombo() {
    myDevicesComboBox.setModel(myComboBoxModel);
    myDevicesComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Object sel = myDevicesComboBox.getSelectedItem();
        IDevice device = (sel instanceof IDevice) ? (IDevice)sel : null;
        updateClientsForDevice(device);
        myDeviceContext.fireDeviceSelected(device);
        myDeviceContext.fireClientSelected(null);
      }
    });
    myDevicesComboBox.setRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list,
                                           Object value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        if (value == null) {
          append(AndroidBundle.message("android.ddms.nodevices"),
                 SimpleTextAttributes.ERROR_ATTRIBUTES);
        } else if (value instanceof IDevice) {
          IDevice d = (IDevice)value;
          if (d.isEmulator()) {
            append(AndroidBundle.message("android.emulator"));
            append(" ");
            append(d.getAvdName());
          } else {
            append(DevicePropertyUtil.getManufacturer(d, ""));
            append(" ");
            append(DevicePropertyUtil.getModel(d, ""));
          }
          append(" ");
          append(DevicePropertyUtil.getBuild(d), SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      }
    });

    IDevice[] devices = myBridge.getDevices();
    if (devices.length == 0) {
      myComboBoxModel.addElement(NO_DEVICES);
    } else {
      for (IDevice device : devices) {
        myComboBoxModel.addElement(device);
      }
    }
    myDevicesComboBox.setSelectedIndex(0);
  }

  private void initializeClientsList() {
    myClientsList.setModel(myClientsListModel);
    myClientsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myClientsList.setEmptyText("No debuggable applications");
    myClientsList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list,
                                           Object value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        if (!(value instanceof Client)) {
          return;
        }

        Client c = (Client)value;
        ClientData cd = c.getClientData();
        String name = cd.getClientDescription();
        if (name != null) {
          List<Pair<String, SimpleTextAttributes>> nameComponents = getAppNameRenderOptions(name);
          for (Pair<String, SimpleTextAttributes> component: nameComponents) {
            append(component.getFirst(), component.getSecond());
          }
        }

        append(String.format(" (%d)", cd.getPid()), SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    });
    new ListSpeedSearch(myClientsList) {
      @Override
      protected boolean isMatchingElement(Object element, String pattern) {
        if (element instanceof Client) {
          String pkg = ((Client)element).getClientData().getClientDescription();
          return pkg != null && pkg.contains(pattern);
        }
        return false;
      }
    };
    myClientsList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        Object sel = myClientsList.getSelectedValue();
        Client c = (sel instanceof Client) ? (Client)sel : null;
        myDeviceContext.fireClientSelected(c);
      }
    });
  }

  @VisibleForTesting
  static List<Pair<String, SimpleTextAttributes>> getAppNameRenderOptions(String name) {
    int index = name.lastIndexOf('.');
    if (index == -1) {
      return Collections.singletonList(Pair.of(name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));
    } else {
      List<Pair<String, SimpleTextAttributes>> components = new ArrayList<Pair<String, SimpleTextAttributes>>(2);
      components.add(Pair.of(name.substring(0, index + 1), SimpleTextAttributes.REGULAR_ATTRIBUTES));
      if (index < name.length() - 1) {
        components.add(Pair.of(name.substring(index + 1), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));
      }
      return components;
    }
  }

  @Override
  public void dispose() {
    if (myBridge != null) {
      AndroidDebugBridge.removeDeviceChangeListener(this);
      AndroidDebugBridge.removeClientChangeListener(this);

      myBridge = null;
    }
  }

  public JPanel getContentPanel() {
    return myPanel;
  }

  @Override
  public void clientChanged(Client client, int changeMask) {
  }

  @Override
  public void deviceConnected(final IDevice device) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        myComboBoxModel.removeElement(NO_DEVICES);
        myComboBoxModel.addElement(device);
      }
    });
  }

  @Override
  public void deviceDisconnected(final IDevice device) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        myComboBoxModel.removeElement(device);
        if (myComboBoxModel.getSize() == 0) {
          myComboBoxModel.addElement(NO_DEVICES);
        }
      }
    });
  }

  @Override
  public void deviceChanged(final IDevice device, int changeMask) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        if (!myDevicesComboBox.getSelectedItem().equals(device)) {
          return;
        }

        updateClientsForDevice(device);
      }
    });
  }

  private void updateClientsForDevice(@Nullable IDevice device) {
    myClientsListModel.removeAll();

    if (device == null) {
      return;
    }

    for (Client c: device.getClients()) {
      myClientsListModel.add(c);
    }
  }

  @NotNull
  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new MyTerminateVMAction());
    group.add(new MyGcAction());
    return group;
  }

  private abstract class MyClientAction extends AnAction {
    public MyClientAction(@Nullable String text,
                          @Nullable String description,
                          @Nullable Icon icon) {
      super(text, description, icon);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myDeviceContext.getSelectedClient() != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Client c = myDeviceContext.getSelectedClient();
      if (c != null) {
        performAction(c);
      }
    }

    abstract void performAction(@NotNull Client c);
  }

  private class MyTerminateVMAction extends MyClientAction {
    public MyTerminateVMAction() {
      super(AndroidBundle.message("android.ddms.actions.terminate.vm"),
            AndroidBundle.message("android.ddms.actions.terminate.vm.description"),
            AllIcons.Process.Stop);
    }

    @Override
    void performAction(@NotNull Client c) {
      c.kill();
    }
  }

  private class MyGcAction extends MyClientAction {
    public MyGcAction() {
      super(AndroidBundle.message("android.ddms.actions.initiate.gc"),
            AndroidBundle.message("android.ddms.actions.initiate.gc.description"),
            AllIcons.Actions.GC);
    }

    @Override
    void performAction(@NotNull Client c) {
      c.executeGarbageCollector();
    }
  }
}
