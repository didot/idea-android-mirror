/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.activity;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.model.MergedManifestSnapshot;
import com.android.utils.concurrency.AsyncSupplier;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ActivityLocator {
  public static final class ActivityLocatorException extends Exception {
    public ActivityLocatorException(String message) {
      super(message);
    }

    public ActivityLocatorException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Validates whether an activity can be located.
   *
   * NOTE: This is called before a build is performed, so for certain build systems, it may not be able
   * to perform a full validation, and an exception might be thrown by {@link #getQualifiedActivityName(IDevice)}.
   */
  public abstract void validate() throws ActivityLocatorException;

  /**
   * Returns the fully qualified activity name suitable for launching on the given device.
   */
  @NotNull
  public abstract String getQualifiedActivityName(@NotNull IDevice device) throws ActivityLocatorException;

  @Nullable
  protected static MergedManifestSnapshot getMergedManifest(@NotNull final AndroidFacet facet, boolean usePotentiallyStaleManifest) {
    if (usePotentiallyStaleManifest) {
      AsyncSupplier<MergedManifestSnapshot> manifestSupplier = MergedManifestManager.getMergedManifestSupplier(facet.getModule());
      // This will trigger recomputation of the merged manifest in the background if it's out of date or has never been computed.
      // Doing so won't help us this time, but it will help keep the manifest fresh for future callers.
      manifestSupplier.get();
      return manifestSupplier.getNow();
    }
    return MergedManifestManager.getFreshSnapshot(facet.getModule());
  }
}
