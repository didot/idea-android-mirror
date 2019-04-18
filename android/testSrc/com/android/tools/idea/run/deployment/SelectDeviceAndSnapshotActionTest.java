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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.android.tools.idea.run.AndroidDevice;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.AnAction;
import java.util.Arrays;
import org.junit.Test;
import org.mockito.Mockito;

public final class SelectDeviceAndSnapshotActionTest {
  @Test
  public void selectDeviceAndSnapshotActionSnapshotsIsEmpty() {
    Device device = new VirtualDevice.Builder()
      .setName(TestDevices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(ImmutableList.of())
      .build();

    SelectDeviceAndSnapshotAction action = new SelectDeviceAndSnapshotAction.Builder()
      .setComboBoxAction(Mockito.mock(DeviceAndSnapshotComboBoxAction.class))
      .setDevice(device)
      .build();

    assertNull(action.getSnapshot());
  }

  @Test
  public void selectDeviceAndSnapshotActionSnapshotsEqualsDefaultSnapshotCollection() {
    DeviceAndSnapshotComboBoxAction comboBoxAction = Mockito.mock(DeviceAndSnapshotComboBoxAction.class);
    Mockito.when(comboBoxAction.areSnapshotsEnabled()).thenReturn(true);

    Device device = new VirtualDevice.Builder()
      .setName(TestDevices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION)
      .build();

    SelectDeviceAndSnapshotAction action = new SelectDeviceAndSnapshotAction.Builder()
      .setComboBoxAction(comboBoxAction)
      .setDevice(device)
      .build();

    assertEquals(VirtualDevice.DEFAULT_SNAPSHOT, action.getSnapshot());
  }

  @Test
  public void selectDeviceAndSnapshotActionThrowsIllegalArgumentException() {
    DeviceAndSnapshotComboBoxAction comboBoxAction = Mockito.mock(DeviceAndSnapshotComboBoxAction.class);
    Mockito.when(comboBoxAction.areSnapshotsEnabled()).thenReturn(true);

    Device device = new VirtualDevice.Builder()
      .setName(TestDevices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(ImmutableList.of("snap_2018-08-07_16-27-58"))
      .build();

    try {
      new SelectDeviceAndSnapshotAction.Builder()
        .setComboBoxAction(comboBoxAction)
        .setDevice(device)
        .build();

      fail();
    }
    catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void selectDeviceAndSnapshotActionTwoDevicesHaveSameName() {
    // Arrange
    Device lgeNexus5x1 = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey("00fff9d2279fa601")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device lgeNexus5x2 = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey("00fff9d2279fa602")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    DeviceAndSnapshotComboBoxAction comboBoxAction = Mockito.mock(DeviceAndSnapshotComboBoxAction.class);
    Mockito.when(comboBoxAction.getDevices()).thenReturn(Arrays.asList(lgeNexus5x1, lgeNexus5x2));

    // Act
    AnAction action = new SelectDeviceAndSnapshotAction.Builder()
      .setComboBoxAction(comboBoxAction)
      .setDevice(lgeNexus5x1)
      .build();

    // Assert
    assertEquals("LGE Nexus 5X - 00fff9d2279fa601", action.getTemplatePresentation().getText());
  }
}
