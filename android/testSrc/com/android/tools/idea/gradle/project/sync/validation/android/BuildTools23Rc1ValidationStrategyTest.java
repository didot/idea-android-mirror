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
package com.android.tools.idea.gradle.project.sync.validation.android;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessagesStub;
import com.android.tools.idea.gradle.project.sync.validation.android.BuildTools23Rc1ValidationStrategy.BuildToolsVersionReader;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mock;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link BuildTools23Rc1ValidationStrategy}.
 */
public class BuildTools23Rc1ValidationStrategyTest extends AndroidGradleTestCase {
  @Mock private BuildToolsVersionReader myBuildToolsVersionReader;
  private BuildTools23Rc1ValidationStrategy myStrategy;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myStrategy = new BuildTools23Rc1ValidationStrategy(getProject(), myBuildToolsVersionReader);
  }

  public void testValidate() {
    AndroidGradleModel androidModel = mockAndroidModel("1.1", 2);
    Module module = mock(Module.class);
    when(module.getName()).thenReturn("app");
    when(myBuildToolsVersionReader.getBuildToolsVersion(module)).thenReturn("23.0.0 rc1");

    myStrategy.validate(module, androidModel);

    assertThat(myStrategy.getModules()).containsExactly("app");
  }

  public void testValidateWithPlugin1Dot3() {
    AndroidGradleModel androidModel = mockAndroidModel("1.3", 3);
    Module module = mock(Module.class);

    myStrategy.validate(module, androidModel);

    assertThat(myStrategy.getModules()).isEmpty();
    verify(myBuildToolsVersionReader, never()).getBuildToolsVersion(module);
  }

  public void testValidateWithBuildTools24() {
    AndroidGradleModel androidModel = mockAndroidModel("1.1", 2);
    Module module = mock(Module.class);
    when(module.getName()).thenReturn("app");
    when(myBuildToolsVersionReader.getBuildToolsVersion(module)).thenReturn("24.0.0");

    myStrategy.validate(module, androidModel);

    assertThat(myStrategy.getModules()).isEmpty();
  }

  @NotNull
  private static AndroidGradleModel mockAndroidModel(@Nullable String modelVersion, int apiVersion) {
    AndroidProject androidProject = mock(AndroidProject.class);
    when(androidProject.getModelVersion()).thenReturn(modelVersion);
    when(androidProject.getApiVersion()).thenReturn(apiVersion);

    AndroidGradleModel androidModel = mock(AndroidGradleModel.class);
    when(androidModel.getAndroidProject()).thenReturn(androidProject);
    return androidModel;
  }

  public void testFixAndReportFoundIssues() {
    Project project = getProject();
    SyncMessagesStub syncMessages = SyncMessagesStub.replaceSyncMessagesService(project);

    myStrategy.getModules().add("app");
    myStrategy.fixAndReportFoundIssues();

    SyncMessage message = syncMessages.getFirstReportedMessage();
    assertNotNull(message);

    String[] text = message.getText();
    assertThat(text).isNotEmpty();
    assertThat(text[0]).startsWith("Build Tools 23.0.0 rc1 is <b>deprecated</b>");
  }

  public void testFixAndReportFoundIssuesWithNoIssues() {
    Project project = getProject();
    SyncMessagesStub syncMessages = SyncMessagesStub.replaceSyncMessagesService(project);

    myStrategy.getModules().clear();
    myStrategy.fixAndReportFoundIssues();

    SyncMessage message = syncMessages.getFirstReportedMessage();
    assertNull(message);
  }
}