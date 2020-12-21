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
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.LaunchableAndroidDevice;
import com.android.tools.idea.run.editor.DeployTarget;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.Optional;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceAndSnapshotComboBoxTargetTest {
  @Test
  public void getDevices() {
    // Arrange
    Key key = new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd");
    AndroidDevice androidDevice = Mockito.mock(LaunchableAndroidDevice.class);

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(key)
      .setAndroidDevice(androidDevice)
      .build();

    AsyncDevicesGetter getter = Mockito.mock(AsyncDevicesGetter.class);
    Mockito.when(getter.get()).thenReturn(Optional.of(Collections.singletonList(device)));

    DeployTarget target = new DeviceAndSnapshotComboBoxTarget(Collections.singleton(new Target(key)), project -> getter);

    Project project = Mockito.mock(Project.class);

    Module module = Mockito.mock(Module.class);
    Mockito.when(module.getProject()).thenReturn(project);

    AndroidFacet facet = Mockito.mock(AndroidFacet.class);
    Mockito.when(facet.getModule()).thenReturn(module);

    // Act
    Object futures = target.getDevices(facet);

    // Assert
    assertEquals(new DeviceFutures(Collections.singletonList(androidDevice)), futures);
  }
}
