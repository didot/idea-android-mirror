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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.android.tools.idea.gradle.structure.model.PsProject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class PsAllModulesFakeModuleTest {
  private PsProject myProject;
  private PsAllModulesFakeModule myallModules;

  @Before
  public void setUp() {
    myProject = mock(PsProject.class);
    myallModules = new PsAllModulesFakeModule(myProject);
  }

  @Test
  public void isModified() throws Exception {
    when(myProject.isModified()).thenReturn(false);
    assertThat(myallModules.isModified(), is(false));

    when(myProject.isModified()).thenReturn(true);
    assertThat(myallModules.isModified(), is(true));
  }

  @Test
  public void appplyChanges() throws Exception {
    myallModules.applyChanges();

    verify(myProject, only()).applyChanges();
  }
}