/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.notifications;

import static com.android.tools.idea.gradle.dsl.api.BuildModelNotification.NotificationType.INVALID_EXPRESSION;

import com.android.tools.idea.gradle.dsl.api.BuildModelNotification;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class InvalidExpressionNotification implements BuildModelNotification {
  @NotNull
  private List<Throwable> myErrors = new ArrayList<>();

  public void addError(@NotNull Throwable error) {
    myErrors.add(error);
  }

  @Override
  public boolean isCorrectionAvailable() {
    return false;
  }

  @Override
  public void correct() { }

  @NotNull
  @Override
  public NotificationType getType() {
    return INVALID_EXPRESSION;
  }

  @Override
  public String toString() {
    return myErrors.stream().map(error -> "\t" + error.getMessage() + "\n\n").reduce("Found errors:\n", String::concat);
  }
}
