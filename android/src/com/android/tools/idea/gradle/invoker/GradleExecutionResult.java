/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.invoker;

import com.android.tools.idea.gradle.output.GradleMessage;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class GradleExecutionResult {
  @NotNull private final List<String> myTasks;
  @NotNull private final Multimap<GradleMessage.Kind, GradleMessage> myCompilerMessagesByKind = ArrayListMultimap.create();
  private final int myErrorCount;

  GradleExecutionResult(@NotNull List<String> tasks, @NotNull List<GradleMessage> compilerMessages) {
    myTasks = tasks;
    for (GradleMessage msg : compilerMessages) {
      myCompilerMessagesByKind.put(msg.getKind(), msg);
    }
    myErrorCount = myCompilerMessagesByKind.get(GradleMessage.Kind.ERROR).size();
  }

  @NotNull
  public List<String> getTasks() {
    return myTasks;
  }

  @NotNull
  public Collection<GradleMessage> getCompilerMessages(@NotNull GradleMessage.Kind kind) {
    return myCompilerMessagesByKind.get(kind);
  }

  public int getErrorCount() {
    return myErrorCount;
  }
}
