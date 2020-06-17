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
package com.android.tools.idea.run.deployment;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.run.AndroidDevice;
import com.google.common.collect.Sets;
import com.intellij.execution.ExecutionTarget;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceAndSnapshotComboBoxExecutionTargetTest {
  private final AsyncDevicesGetter myGetter = Mockito.mock(AsyncDevicesGetter.class);

  @Test
  public void getDevices() {
    // Arrange
    Key key = new Key("Pixel_3_API_29");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myGetter.get()).thenReturn(Optional.of(Collections.singletonList(device)));

    AndroidExecutionTarget target = new DeviceAndSnapshotComboBoxExecutionTarget(Collections.singleton(key), myGetter);

    // Act
    Object actualDevices = target.getDevices();

    // Assert
    assertEquals(Collections.emptyList(), actualDevices);
  }

  @Test
  public void getDisplayNameCase1() {
    // Arrange
    Key key = new Key("Pixel_3_API_29");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myGetter.get()).thenReturn(Optional.of(Collections.singletonList(device)));

    ExecutionTarget target = new DeviceAndSnapshotComboBoxExecutionTarget(Collections.singleton(key), myGetter);

    // Act
    Object actualDisplayName = target.getDisplayName();

    // Assert
    assertEquals("Pixel 3 API 29", actualDisplayName);
  }

  @Test
  public void getDisplayNameDefault() {
    // Arrange
    Key key1 = new Key("Pixel_2_API_29");
    Key key2 = new Key("Pixel_3_API_29");

    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 2 API 29")
      .setKey(key1)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(key2)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myGetter.get()).thenReturn(Optional.of(Arrays.asList(device1, device2)));

    ExecutionTarget target = new DeviceAndSnapshotComboBoxExecutionTarget(Sets.newHashSet(key1, key2), myGetter);

    // Act
    Object actualDisplayName = target.getDisplayName();

    // Assert
    assertEquals("Multiple Devices", actualDisplayName);
  }
}
