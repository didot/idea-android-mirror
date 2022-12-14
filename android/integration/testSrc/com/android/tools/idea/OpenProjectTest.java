/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea;

import com.android.tools.asdriver.tests.AndroidProject;
import com.android.tools.asdriver.tests.AndroidStudio;
import com.android.tools.asdriver.tests.AndroidSystem;
import com.android.tools.asdriver.tests.MavenRepo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class OpenProjectTest {
  @Rule
  public AndroidSystem system = AndroidSystem.standard();

  @Test
  public void openProjectTest() throws Exception {
    // Create a new android project, and set a fixed distribution
    AndroidProject project = new AndroidProject("tools/adt/idea/android/integration/testData/minapp");
    project.setDistribution("tools/external/gradle/gradle-7.2-bin.zip");

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(new MavenRepo("tools/adt/idea/android/integration/openproject_deps.manifest"));

    try (AndroidStudio studio = system.runStudio(project)) {
      studio.waitForSync();
    }
  }
}
