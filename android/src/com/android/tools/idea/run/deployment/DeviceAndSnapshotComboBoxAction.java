/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiAction;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.android.actions.RunAndroidAvdManagerAction;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeviceAndSnapshotComboBoxAction extends ComboBoxAction {
  /**
   * Run configurations that aren't {@link AndroidRunConfiguration} or {@link AndroidTestRunConfiguration} can use this key
   * to express their applicability for DeviceAndSnapshotComboBoxAction by setting it to true in their user data.
   */
  public static final com.intellij.openapi.util.Key<Boolean> DEPLOYS_TO_LOCAL_DEVICE =
    com.intellij.openapi.util.Key.create("DeviceAndSnapshotComboBoxAction.deploysToLocalDevice");

  @NotNull
  private final Supplier<Boolean> mySelectDeviceSnapshotComboBoxSnapshotsEnabled;

  @NotNull
  private final Function<Project, AsyncDevicesGetter> myDevicesGetterGetter;

  @NotNull
  private final Function<Project, ExecutionTargetService> myExecutionTargetServiceGetInstance;

  @NotNull
  private final Function<Project, DevicesSelectedService> myDevicesSelectedServiceGetInstance;

  @NotNull
  private final Function<Project, RunManager> myGetRunManager;

  @NotNull
  private final AnAction myMultipleDevicesAction;

  @NotNull
  private final AnAction myModifyDeviceSetAction;

  @VisibleForTesting
  static final class Builder {
    @Nullable
    private Supplier<Boolean> mySelectDeviceSnapshotComboBoxSnapshotsEnabled;

    @Nullable
    private Function<Project, AsyncDevicesGetter> myDevicesGetterGetter;

    @Nullable
    private Function<Project, ExecutionTargetService> myExecutionTargetServiceGetInstance;

    @Nullable
    private Function<Project, DevicesSelectedService> myDevicesSelectedServiceGetInstance;

    @Nullable
    private Function<Project, RunManager> myGetRunManager;

    Builder() {
      mySelectDeviceSnapshotComboBoxSnapshotsEnabled = () -> false;
      myDevicesGetterGetter = project -> null;
      myExecutionTargetServiceGetInstance = project -> null;
      myDevicesSelectedServiceGetInstance = project -> null;
      myGetRunManager = project -> null;
    }

    @NotNull
    Builder setSelectDeviceSnapshotComboBoxSnapshotsEnabled(@NotNull Supplier<Boolean> selectDeviceSnapshotComboBoxSnapshotsEnabled) {
      mySelectDeviceSnapshotComboBoxSnapshotsEnabled = selectDeviceSnapshotComboBoxSnapshotsEnabled;
      return this;
    }

    @NotNull
    Builder setDevicesGetterGetter(@NotNull Function<Project, AsyncDevicesGetter> devicesGetterGetter) {
      myDevicesGetterGetter = devicesGetterGetter;
      return this;
    }

    @NotNull
    Builder setExecutionTargetServiceGetInstance(@NotNull Function<Project, ExecutionTargetService> executionTargetServiceGetInstance) {
      myExecutionTargetServiceGetInstance = executionTargetServiceGetInstance;
      return this;
    }

    @NotNull
    Builder setDevicesSelectedServiceGetInstance(@NotNull Function<Project, DevicesSelectedService> devicesSelectedServiceGetInstance) {
      myDevicesSelectedServiceGetInstance = devicesSelectedServiceGetInstance;
      return this;
    }

    @NotNull
    Builder setGetRunManager(@NotNull Function<Project, RunManager> getRunManager) {
      myGetRunManager = getRunManager;
      return this;
    }

    @NotNull
    DeviceAndSnapshotComboBoxAction build() {
      return new DeviceAndSnapshotComboBoxAction(this);
    }
  }

  @SuppressWarnings("unused")
  private DeviceAndSnapshotComboBoxAction() {
    this(new Builder()
           .setSelectDeviceSnapshotComboBoxSnapshotsEnabled(() -> StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_SNAPSHOTS_ENABLED.get())
           .setDevicesGetterGetter(AsyncDevicesGetter::getInstance)
           .setExecutionTargetServiceGetInstance(ExecutionTargetService::getInstance)
           .setDevicesSelectedServiceGetInstance(DevicesSelectedService::getInstance)
           .setGetRunManager(RunManager::getInstance));
  }

  @NonInjectable
  private DeviceAndSnapshotComboBoxAction(@NotNull Builder builder) {
    assert builder.mySelectDeviceSnapshotComboBoxSnapshotsEnabled != null;
    mySelectDeviceSnapshotComboBoxSnapshotsEnabled = builder.mySelectDeviceSnapshotComboBoxSnapshotsEnabled;

    assert builder.myDevicesGetterGetter != null;
    myDevicesGetterGetter = builder.myDevicesGetterGetter;

    assert builder.myExecutionTargetServiceGetInstance != null;
    myExecutionTargetServiceGetInstance = builder.myExecutionTargetServiceGetInstance;

    assert builder.myDevicesSelectedServiceGetInstance != null;
    myDevicesSelectedServiceGetInstance = builder.myDevicesSelectedServiceGetInstance;

    assert builder.myGetRunManager != null;
    myGetRunManager = builder.myGetRunManager;

    myMultipleDevicesAction = new MultipleDevicesAction();
    myModifyDeviceSetAction = new ModifyDeviceSetAction(this);
  }

  @NotNull
  static DeviceAndSnapshotComboBoxAction getInstance() {
    return (DeviceAndSnapshotComboBoxAction)ActionManager.getInstance().getAction("DeviceAndSnapshotComboBox");
  }

  boolean areSnapshotsEnabled() {
    return mySelectDeviceSnapshotComboBoxSnapshotsEnabled.get();
  }

  @NotNull
  @VisibleForTesting
  AnAction getMultipleDevicesAction() {
    return myMultipleDevicesAction;
  }

  @NotNull
  @VisibleForTesting
  AnAction getModifyDeviceSetAction() {
    return myModifyDeviceSetAction;
  }

  @NotNull
  List<Device> getDevices(@NotNull Project project) {
    List<Device> devices = myDevicesGetterGetter.apply(project).get();
    devices.sort(new DeviceComparator());

    return devices;
  }

  @Nullable
  @VisibleForTesting
  Device getSelectedDevice(@NotNull Project project) {
    return myDevicesSelectedServiceGetInstance.apply(project).getDeviceSelectedWithComboBox(getDevices(project));
  }

  @NotNull
  List<Device> getSelectedDevices(@NotNull Project project) {
    return getSelectedDevices(project, getDevices(project));
  }

  @NotNull
  private List<Device> getSelectedDevices(@NotNull Project project, @NotNull List<Device> devices) {
    DevicesSelectedService service = myDevicesSelectedServiceGetInstance.apply(project);

    if (service.isMultipleDevicesSelectedInComboBox()) {
      return service.getDevicesSelectedWithDialog();
    }

    Device device = service.getDeviceSelectedWithComboBox(devices);

    if (device == null) {
      return Collections.emptyList();
    }

    return Collections.singletonList(device);
  }

  void modifyDeviceSet(@NotNull Project project) {
    DevicesSelectedService service = myDevicesSelectedServiceGetInstance.apply(project);

    if (!service.isMultipleDevicesSelectedInComboBox()) {
      return;
    }

    ExecutionTarget target = new DeviceAndSnapshotComboBoxExecutionTarget(service.getDevicesSelectedWithDialog());
    myExecutionTargetServiceGetInstance.apply(project).setActiveTarget(target);
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return createCustomComponent(presentation, JBUI::scale);
  }

  @NotNull
  @VisibleForTesting
  JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull IntUnaryOperator scale) {
    JComponent panel = new JPanel(null);
    GroupLayout layout = new GroupLayout(panel);
    Component button = createComboBoxButton(presentation);

    Group horizontalGroup = layout.createSequentialGroup()
      .addComponent(button, 0, scale.applyAsInt(GroupLayout.DEFAULT_SIZE), scale.applyAsInt(250))
      .addGap(scale.applyAsInt(3));

    Group verticalGroup = layout.createParallelGroup()
      .addComponent(button);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    panel.setLayout(layout);
    return panel;
  }

  @NotNull
  @Override
  protected ComboBoxButton createComboBoxButton(@NotNull Presentation presentation) {
    ComboBoxButton button = new ComboBoxButton(presentation) {
      @Override
      protected JBPopup createPopup(@NotNull Runnable runnable) {
        DataContext context = getDataContext();
        return new Popup(createPopupActionGroup(this, context), context, runnable);
      }
    };

    button.setName("deviceAndSnapshotComboBoxButton");
    return button;
  }

  @Override
  protected boolean shouldShowDisabledActions() {
    return true;
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
    DefaultActionGroup group = new DefaultActionGroup();

    Project project = context.getData(CommonDataKeys.PROJECT);
    assert project != null;

    Collection<AnAction> actions = mySelectDeviceSnapshotComboBoxSnapshotsEnabled.get()
                                   ? newSelectDeviceActionsIncludeSnapshots(project)
                                   : newSelectDeviceActions(project);

    group.addAll(actions);

    if (!actions.isEmpty()) {
      group.addSeparator();
    }

    ActionManager manager = ActionManager.getInstance();

    group.add(myMultipleDevicesAction);
    group.add(myModifyDeviceSetAction);
    group.add(manager.getAction(PairDevicesUsingWiFiAction.ID));
    group.add(manager.getAction(RunAndroidAvdManagerAction.ID));

    AnAction action = manager.getAction("DeveloperServices.ConnectionAssistant");

    if (action == null) {
      return group;
    }

    group.addSeparator();
    group.add(action);

    return group;
  }

  @NotNull
  private Collection<AnAction> newSelectDeviceActions(@NotNull Project project) {
    Map<Boolean, List<Device>> connectednessToDeviceMap = getDevices(project).stream().collect(Collectors.groupingBy(Device::isConnected));

    Collection<Device> connectedDevices = connectednessToDeviceMap.getOrDefault(true, Collections.emptyList());
    Collection<Device> disconnectedDevices = connectednessToDeviceMap.getOrDefault(false, Collections.emptyList());

    boolean connectedDevicesPresent = !connectedDevices.isEmpty();
    Collection<AnAction> actions = new ArrayList<>(connectedDevices.size() + disconnectedDevices.size() + 3);

    if (connectedDevicesPresent) {
      actions.add(new Heading("Running devices"));
    }

    connectedDevices.stream()
      .map(device -> SelectDeviceAction.newSelectDeviceAction(this, project, device))
      .forEach(actions::add);

    boolean disconnectedDevicesPresent = !disconnectedDevices.isEmpty();

    if (connectedDevicesPresent && disconnectedDevicesPresent) {
      actions.add(Separator.create());
    }

    if (disconnectedDevicesPresent) {
      actions.add(new Heading("Available devices"));
    }

    disconnectedDevices.stream()
      .map(device -> SelectDeviceAction.newSelectDeviceAction(this, project, device))
      .forEach(actions::add);

    return actions;
  }

  @NotNull
  private Collection<AnAction> newSelectDeviceActionsIncludeSnapshots(@NotNull Project project) {
    ListMultimap<String, Device> multimap = getDeviceKeyToDeviceMultimap(project);
    Collection<String> deviceKeys = multimap.keySet();
    Collection<AnAction> actions = new ArrayList<>(deviceKeys.size() + 1);

    if (!deviceKeys.isEmpty()) {
      actions.add(new Heading("Available devices"));
    }

    deviceKeys.stream()
      .map(multimap::get)
      .map(devices -> newAction(devices, project))
      .forEach(actions::add);

    return actions;
  }

  @NotNull
  private ListMultimap<String, Device> getDeviceKeyToDeviceMultimap(@NotNull Project project) {
    Collection<Device> devices = myDevicesGetterGetter.apply(project).get();

    // noinspection UnstableApiUsage
    Collector<Device, ?, ListMultimap<String, Device>> collector =
      Multimaps.toMultimap(device -> device.getKey().getDeviceKey(), device -> device, () -> buildListMultimap(devices.size()));

    return devices.stream().collect(collector);
  }

  @NotNull
  private static ListMultimap<String, Device> buildListMultimap(int expectedKeyCount) {
    return MultimapBuilder
      .hashKeys(expectedKeyCount)
      .arrayListValues()
      .build();
  }

  @NotNull
  private AnAction newAction(@NotNull List<Device> devices, @NotNull Project project) {
    if (devices.size() == 1) {
      return SelectDeviceAction.newSelectDeviceAction(this, project, devices.get(0));
    }

    return new SnapshotActionGroup(devices, this, project);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Project project = event.getProject();

    if (project == null) {
      return;
    }

    Presentation presentation = event.getPresentation();

    if (!AndroidUtils.hasAndroidFacets(project)) {
      presentation.setVisible(false);
      return;
    }

    updatePresentation(presentation, myGetRunManager.apply(project).getSelectedConfiguration());

    String place = event.getPlace();
    List<Device> devices = getDevices(project);

    switch (place) {
      case ActionPlaces.MAIN_MENU:
      case ActionPlaces.ACTION_SEARCH:
        presentation.setIcon(null);
        presentation.setText("Select Device...");

        break;
      case ActionPlaces.MAIN_TOOLBAR:
      case ActionPlaces.NAVIGATION_BAR_TOOLBAR:
        if (myDevicesSelectedServiceGetInstance.apply(project).isMultipleDevicesSelectedInComboBox()) {
          updateInToolbarForMultipleDevices(presentation, project, devices);
        }
        else {
          updateInToolbarForSingleDevice(presentation, project, devices);
        }

        break;
      default:
        assert false : place;
    }

    List<Device> selectedDevices = getSelectedDevices(project, devices);
    myExecutionTargetServiceGetInstance.apply(project).setActiveTarget(new DeviceAndSnapshotComboBoxExecutionTarget(selectedDevices));
  }

  @VisibleForTesting
  static void updatePresentation(@NotNull Presentation presentation, @Nullable RunnerAndConfigurationSettings settings) {
    if (settings == null) {
      presentation.setDescription("Add a run/debug configuration");
      presentation.setEnabled(false);

      return;
    }

    RunProfile configuration = settings.getConfiguration();

    // Run configurations can explicitly specify they target a local device through DEPLOY_TO_LOCAL_DEVICE.
    if (configuration instanceof UserDataHolder) {
      Boolean deploysToLocalDevice = ((UserDataHolder)configuration).getUserData(DEPLOYS_TO_LOCAL_DEVICE);
      if (deploysToLocalDevice != null && deploysToLocalDevice.booleanValue()) {
        presentation.setDescription(null);
        presentation.setEnabled(true);

        return;
      }
    }

    if (!(configuration instanceof AndroidRunConfiguration || configuration instanceof AndroidTestRunConfiguration)) {
      presentation.setDescription("Not applicable for the \"" + configuration.getName() + "\" configuration");
      presentation.setEnabled(false);

      return;
    }

    presentation.setDescription(null);
    presentation.setEnabled(true);
  }

  private void updateInToolbarForMultipleDevices(@NotNull Presentation presentation,
                                                 @NotNull Project project,
                                                 @NotNull List<Device> devices) {
    DevicesSelectedService service = myDevicesSelectedServiceGetInstance.apply(project);
    Set<Key> selectedKeys = service.getDeviceKeysSelectedWithDialog();

    Set<Key> keys = devices.stream()
      .map(Device::getKey)
      .collect(Collectors.toSet());

    if (selectedKeys.retainAll(keys)) {
      service.setDeviceKeysSelectedWithDialog(selectedKeys);

      if (selectedKeys.isEmpty()) {
        service.setMultipleDevicesSelectedInComboBox(false);
        service.setDeviceSelectedWithComboBox(service.getDeviceSelectedWithComboBox(devices));

        updateInToolbarForSingleDevice(presentation, project, devices);
        return;
      }
    }

    presentation.setIcon(null);
    presentation.setText("Multiple Devices");
  }

  private void updateInToolbarForSingleDevice(@NotNull Presentation presentation, @NotNull Project project, @NotNull List<Device> devices) {
    if (devices.isEmpty()) {
      presentation.setIcon(null);
      presentation.setText("No Devices");

      return;
    }

    Device device = myDevicesSelectedServiceGetInstance.apply(project).getDeviceSelectedWithComboBox(devices);
    assert device != null;

    presentation.setIcon(device.getIcon());
    presentation.setText(getText(device, devices, mySelectDeviceSnapshotComboBoxSnapshotsEnabled.get()), false);
  }

  /**
   * Formats the selected device for display in the drop down button. If there's another device with the same name, the text will have the
   * selected device's key appended to it to disambiguate it from the other one. If the SNAPSHOTS_ENABLED flag is on and the device has a
   * nondefault snapshot, the text will have the snapshot's name appended to it. Finally, if the {@link
   * com.android.tools.idea.run.LaunchCompatibilityChecker LaunchCompatibilityChecker} found issues with the device, the text will have the
   * issue appended to it.
   *
   * @param device           the selected device
   * @param devices          the devices to check if any other has the same name as the selected device. The selected device may be in the
   *                         collection.
   * @param snapshotsEnabled the value of the SELECT_DEVICE_SNAPSHOT_COMBO_BOX_SNAPSHOTS_ENABLED flag. Passed this way for testability.
   */
  @NotNull
  @VisibleForTesting
  static String getText(@NotNull Device device, @NotNull Collection<Device> devices, boolean snapshotsEnabled) {
    boolean anotherDeviceHasSameName = Devices.containsAnotherDeviceWithSameName(devices, device);
    return Devices.getText(device, anotherDeviceHasSameName ? device.getKey() : null, snapshotsEnabled ? device.getSnapshot() : null);
  }
}
