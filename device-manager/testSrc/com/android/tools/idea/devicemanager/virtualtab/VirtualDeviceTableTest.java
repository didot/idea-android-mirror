/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import static org.junit.Assert.assertEquals;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.CountDownLatchAssert;
import com.android.tools.idea.devicemanager.CountDownLatchFutureCallback;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceTable.SetDevices;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceTableTest {
  private final @NotNull VirtualDevicePanel myPanel = Mockito.mock(VirtualDevicePanel.class);
  private final CountDownLatch myLatch = new CountDownLatch(1);

  @Test
  public void emptyTable() throws InterruptedException {
    VirtualDeviceTable table = new VirtualDeviceTable(myPanel, mockSupplier(Collections.emptyList()), this::newSetDevices);

    CountDownLatchAssert.await(myLatch);

    assertEquals(Optional.empty(), table.getSelectedDevice());
  }

  @Test
  public void selectDevice() throws InterruptedException {
    AvdInfo avdInfo = new AvdInfo("Pixel 5",
                                  Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  null);

    AvdManagerConnection avdManagerConnection = Mockito.mock(AvdManagerConnection.class);

    VirtualDevice device = TestVirtualDevices.pixel5Api31(avdInfo,
                                                          () -> avdManagerConnection);

    VirtualDeviceTable table = new VirtualDeviceTable(myPanel, mockSupplier(Collections.singletonList(device)), this::newSetDevices);

    CountDownLatchAssert.await(myLatch);

    assertEquals(Optional.empty(), table.getSelectedDevice());

    table.setRowSelectionInterval(0, 0);
    assertEquals(Optional.of(device), table.getSelectedDevice());
  }

  private static @NotNull VirtualDeviceAsyncSupplier mockSupplier(@NotNull List<@NotNull VirtualDevice> devices) {
    VirtualDeviceAsyncSupplier supplier = Mockito.mock(VirtualDeviceAsyncSupplier.class);
    Mockito.when(supplier.get()).thenReturn(Futures.immediateFuture(devices));
    return supplier;
  }

  private @NotNull FutureCallback<@NotNull List<@NotNull VirtualDevice>> newSetDevices(@NotNull VirtualDeviceTableModel model) {
    return new CountDownLatchFutureCallback<>(new SetDevices(model), myLatch);
  }
}
