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

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.module.Module;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class WorkerAsyncDevicesGetterTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private WorkerAsyncDevicesGetter myGetter;

  @Before
  public void setUp() {
    Clock clock = Mockito.mock(Clock.class);
    Mockito.when(clock.instant()).thenReturn(Instant.parse("2018-11-28T01:15:27.000Z"));

    myGetter = new WorkerAsyncDevicesGetter(myRule.getProject(), new KeyToConnectionTimeMap(clock));
  }

  @Test
  public void getImpl() {
    // Arrange
    AndroidDevice pixel2ApiQAndroidDevice = Mockito.mock(AndroidDevice.class);

    VirtualDevice pixel2ApiQVirtualDevice = new VirtualDevice.Builder()
      .setName("Pixel 2 API Q")
      .setKey("Pixel_2_API_Q")
      .setAndroidDevice(pixel2ApiQAndroidDevice)
      .build();

    VirtualDevice pixel3ApiQVirtualDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API Q")
      .setKey("Pixel_3_API_Q")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    AndroidDevice googlePixel3AndroidDevice = Mockito.mock(AndroidDevice.class);

    ConnectedDevice googlePixel3ConnectedDevice = new TestConnectedDevice.Builder()
      .setName("Connected Device")
      .setKey("86UX00F4R")
      .setAndroidDevice(googlePixel3AndroidDevice)
      .setPhysicalDeviceName("Google Pixel 3")
      .build();

    AndroidDevice pixel3ApiQAndroidDevice = Mockito.mock(AndroidDevice.class);

    ConnectedDevice pixel3ApiQConnectedDevice = new TestConnectedDevice.Builder()
      .setName("Connected Device")
      .setKey("emulator-5554")
      .setAndroidDevice(pixel3ApiQAndroidDevice)
      .setVirtualDeviceKey("Pixel_3_API_Q")
      .build();

    // Act
    Object actualDevices = myGetter.getImpl(
      Arrays.asList(pixel2ApiQVirtualDevice, pixel3ApiQVirtualDevice),
      Arrays.asList(googlePixel3ConnectedDevice, pixel3ApiQConnectedDevice));

    // Assert
    Object expectedPixel3ApiQDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API Q")
      .setKey("Pixel_3_API_Q")
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27.000Z"))
      .setAndroidDevice(pixel3ApiQAndroidDevice)
      .build();

    Object expectedGooglePixel3Device = new PhysicalDevice.Builder()
      .setName("Google Pixel 3")
      .setKey("86UX00F4R")
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27.000Z"))
      .setAndroidDevice(googlePixel3AndroidDevice)
      .build();

    Object expectedPixel2ApiQDevice = new VirtualDevice.Builder()
      .setName("Pixel 2 API Q")
      .setKey("Pixel_2_API_Q")
      .setAndroidDevice(pixel2ApiQAndroidDevice)
      .build();

    assertEquals(Arrays.asList(expectedPixel3ApiQDevice, expectedGooglePixel3Device, expectedPixel2ApiQDevice), actualDevices);
  }

  @Test
  public void initChecker() {
    RunConfigurationModule configurationModule = Mockito.mock(RunConfigurationModule.class);
    Mockito.when(configurationModule.getModule()).thenReturn(myRule.getModule());

    ModuleBasedConfiguration configuration = Mockito.mock(ModuleBasedConfiguration.class);
    Mockito.when(configuration.getConfigurationModule()).thenReturn(configurationModule);

    RunnerAndConfigurationSettings configurationAndSettings = Mockito.mock(RunnerAndConfigurationSettings.class);
    Mockito.when(configurationAndSettings.getConfiguration()).thenReturn(configuration);

    myGetter.initChecker(configurationAndSettings, WorkerAsyncDevicesGetterTest::newAndroidFacet);
    assertNull(myGetter.getChecker());
  }

  @NotNull
  private static AndroidFacet newAndroidFacet(@NotNull Module module) {
    return new AndroidFacet(module, "Android", Mockito.mock(AndroidFacetConfiguration.class));
  }
}
