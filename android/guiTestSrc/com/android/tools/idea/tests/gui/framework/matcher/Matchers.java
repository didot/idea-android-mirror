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
package com.android.tools.idea.tests.gui.framework.matcher;

import org.fest.swing.core.GenericTypeMatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/** Utility methods for {@link org.fest.swing.core.ComponentMatcher matchers}. */
public final class Matchers {
  private Matchers() {}

  @NotNull
  public static <T extends AbstractButton> GenericTypeMatcher<T> byText(Class<T> abstractButtonType, @NotNull String text) {
    return new GenericTypeMatcher<T>(abstractButtonType) {
      @Override
      protected boolean isMatching(@NotNull T component) {
        return text.equals(component.getText());
      }
    };
  }
}
