/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.explorer.adbimpl;

import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.explorer.fs.FileTransferProgress;
import com.google.common.util.concurrent.ListenableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A {@link AdbDeviceFileEntry} that goes directly to the remote file system for its file operations.
 *
 * <p>The (optional) {@code runAs} parameter is directly passed to the {@link AdbFileListing} and
 * {@link AdbFileOperations} methods to use as a {@code "run-as package-name" prefix}.
 */
public class AdbDeviceDirectFileEntry extends AdbDeviceFileEntry {
  @Nullable private final String myRunAs;

  public AdbDeviceDirectFileEntry(@NotNull AdbDeviceFileSystem device,
                                  @NotNull AdbFileListingEntry entry,
                                  @Nullable AdbDeviceFileEntry parent,
                                  @Nullable String runAs) {
    super(device, entry, parent);
    myRunAs = runAs;
  }

  @NotNull
  @Override
  public ListenableFuture<List<DeviceFileEntry>> getEntries() {
    ListenableFuture<List<AdbFileListingEntry>> children = myDevice.getAdbFileListing().getChildrenRunAs(myEntry, myRunAs);
    return myDevice.getTaskExecutor().transform(children, result -> {
      assert result != null;
      return result.stream()
        .map(listingEntry -> new AdbDeviceDefaultFileEntry(myDevice, listingEntry, this))
        .collect(Collectors.toList());
    });
  }

  @NotNull
  @Override
  public ListenableFuture<Void> delete() {
    if (isDirectory()) {
      return myDevice.getAdbFileOperations().deleteRecursiveRunAs(getFullPath(), myRunAs);
    }
    else {
      return myDevice.getAdbFileOperations().deleteFileRunAs(getFullPath(), myRunAs);
    }
  }

  @NotNull
  @Override
  public ListenableFuture<Void> createNewFile(@NotNull String fileName) {
    return myDevice.getAdbFileOperations().createNewFileRunAs(getFullPath(), fileName, myRunAs);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> createNewDirectory(@NotNull String directoryName) {
    return myDevice.getAdbFileOperations().createNewDirectoryRunAs(getFullPath(), directoryName, myRunAs);
  }

  @NotNull
  @Override
  public ListenableFuture<Boolean> isSymbolicLinkToDirectory() {
    return myDevice.getAdbFileListing().isDirectoryLinkRunAs(myEntry, myRunAs);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> downloadFile(@NotNull Path localPath,
                                             @NotNull FileTransferProgress progress) {
    return myDevice.getAdbFileTransfer().downloadFile(this.myEntry, localPath, progress);
  }

  @NotNull
  @Override
  public ListenableFuture<Void> uploadFile(@NotNull Path localPath,
                                           @NotNull String fileName,
                                           @NotNull FileTransferProgress progress) {
    return myDevice.getAdbFileTransfer().uploadFile(localPath,
                                                    AdbPathUtil.resolve(myEntry.getFullPath(), fileName),
                                                    progress);
  }
}
