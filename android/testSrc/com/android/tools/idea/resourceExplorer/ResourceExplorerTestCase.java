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
package com.android.tools.idea.resourceExplorer;

import com.android.tools.idea.flags.StudioFlags;
import org.jetbrains.android.AndroidTestCase;

public abstract class ResourceExplorerTestCase extends AndroidTestCase {
  private boolean myOriginalFlagState;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myOriginalFlagState = StudioFlags.RESOURCE_MANAGER_ENABLED.get();
    StudioFlags.RESOURCE_MANAGER_ENABLED.override(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.RESOURCE_MANAGER_ENABLED.override(myOriginalFlagState);
    }
    finally {
      super.tearDown();
    }
  }

  public static void runWithResourceExplorerFlagDisabled(Runnable runnable) {
    boolean resourceManagerEnabled = StudioFlags.RESOURCE_MANAGER_ENABLED.get();
    try {
      StudioFlags.RESOURCE_MANAGER_ENABLED.override(false);
      runnable.run();
    }
    finally {
      StudioFlags.RESOURCE_MANAGER_ENABLED.override(resourceManagerEnabled);
    }
  }
}
