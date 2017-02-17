/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.explorer.fs;

import com.google.common.util.concurrent.ListenableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An file or directory entry in a {@link DeviceFileSystem}
 */
public interface DeviceFileEntry {
  /**
   * The {@link DeviceFileSystem} this entry belongs to.
   */
  @NotNull
  DeviceFileSystem getFileSystem();

  /**
   * The parent {@link DeviceFileEntry} or <code>null</code> if this is the root directory.
   */
  @Nullable
  DeviceFileEntry getParent();

  /**
   * The name of this entry in its parent directory.
   */
  @NotNull
  String getName();

  /**
   * The list of entries contained in this directory.
   */
  @NotNull
  ListenableFuture<List<DeviceFileEntry>> getEntries();

  /**
   * Returns {@code true} if the entry is a symbolic link that points to a directory.
   * @see com.android.tools.idea.explorer.adbimpl.AdbFileListing#isDirectoryLink
   */
  @NotNull
  ListenableFuture<Boolean> isSymbolicLinkToDirectory();

  /**
   * The permissions associated to this entry, similar to unix permissions.
   */
  @NotNull
  Permissions getPermissions();

  /**
   * The last modification date & time of this entry
   */
  @NotNull
  DateTime getLastModifiedDate();

  /**
   * The size (in bytes) of this entry, or <code>-1</code> if the size is unknown.
   */
  long getSize();

  /**
   * <code>true</code> if the entry is a directory, i.e. it contains entries.
   */
  boolean isDirectory();

  /**
   * <code>true</code> if the entry is a file, i.e. it has content and does not contain entries.
   */
  boolean isFile();

  /**
   * <code>true</code> if the entry is a symbolic link.
   */
  boolean isSymbolicLink();

  /**
   * The link target of the entry if {@link #isSymbolicLink()} is <code>true</code>, <code>null</code> otherwise.
   */
  @Nullable
  String getSymbolicLinkTarget();

  /**
   * Permissions associated to a {@link DeviceFileEntry}.
   */
  interface Permissions {
    @NotNull
    String getText();
  }

  /**
   * Date & time associated to a {@link DeviceFileEntry}.
   */
  interface DateTime {
    @NotNull
    String getText();
  }
}
