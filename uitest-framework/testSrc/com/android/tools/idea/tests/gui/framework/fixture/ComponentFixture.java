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
package com.android.tools.idea.tests.gui.framework.fixture;

import java.awt.Component;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.ComponentDriver;
import org.fest.swing.fixture.AbstractComponentFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ComponentFixture<S, C extends Component> extends AbstractComponentFixture<S, C, ComponentDriver> {
  public ComponentFixture(@NotNull Class<S> selfType, @NotNull Robot robot, @NotNull Class<? extends C> type) {
    super(selfType, robot, type);
  }

  public ComponentFixture(@NotNull Class<S> selfType, @NotNull Robot robot, @Nullable String name, @NotNull Class<? extends C> type) {
    super(selfType, robot, name, type);
  }

  public ComponentFixture(@NotNull Class<S> selfType, @NotNull Robot robot, @NotNull C target) {
    super(selfType, robot, target);
  }

  @Override
  @NotNull
  protected ComponentDriver createDriver(@NotNull Robot robot) {
    return new ComponentDriver(robot);
  }
}
