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
package com.android.tools.idea.gradle.project;

import com.android.ide.common.repository.GradleVersion;
import org.junit.Test;

import static org.junit.Assert.*;

public class PostProjectSetupTasksExecutorTest {

  @Test
  public void testCompareVersions() throws Exception {
    assertFalse(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(GradleVersion.parse("2.0.0"), "2.0.0"));
    assertFalse(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(GradleVersion.parse("2.0.0"), "2.0.0-alpha1"));
    assertFalse(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(GradleVersion.parse("2.0.0"), "2.0.0-beta1"));
    assertFalse(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(GradleVersion.parse("2.0.0-alpha6"), "2.0.0-alpha5"));
    assertTrue(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(GradleVersion.parse("2.0.0-alpha1"), "2.0.0"));
    assertTrue(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(GradleVersion.parse("2.0.0-alpha1"), "2.0.0-alpha2"));
    assertTrue(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(GradleVersion.parse("2.0.0-alpha1"), "2.0.0-rc1"));
    assertTrue(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(GradleVersion.parse("2.0.0-alpha2"), "2.0.0-rc1"));
    assertTrue(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(GradleVersion.parse("2.0.0-alpha1"), "2.0.0-beta1"));
    assertTrue(PostProjectSetupTasksExecutor.androidPluginNeedsUpdate(GradleVersion.parse("2.0.0-beta1"), "2.0.0"));
  }
}