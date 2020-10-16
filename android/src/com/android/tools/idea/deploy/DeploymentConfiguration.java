/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.deploy;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
  name = "DeploymentConfiguration",
  storages = @Storage(value = "android-deployment.xml", roamingType = RoamingType.DISABLED)
)
public class DeploymentConfiguration implements PersistentStateComponent<DeploymentConfiguration> {
  public boolean APPLY_CHANGES_FALLBACK_TO_RUN = false;
  public boolean APPLY_CODE_CHANGES_FALLBACK_TO_RUN = false;

  public static DeploymentConfiguration getInstance() {
    return ApplicationManager.getApplication().getService(DeploymentConfiguration.class);
  }

  @Nullable
  @Override
  public DeploymentConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull DeploymentConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}