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
package com.android.tools.idea.templates;

import com.android.ide.common.repository.GradleCoordinate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Utilities shared between {@link GradleFileSimpleMerger} and {@link GradleFilePsiMerger}.
 */
public class GradleFileMergers {
  /**
   * Name of the dependencies DSL block.
   */
  static final String DEPENDENCIES = "dependencies";

  private static final ImmutableList<String> KNOWN_CONFIGURATIONS_IN_ORDER =
    ImmutableList.of("feature", "api", "implementation", "compile", "testImplementation", "testCompile",
                     "androidTestImplementation", "androidTestCompile");

  /**
   * Defined an ordering on gradle configuration names.
   */
  static final Ordering<String> CONFIGURATION_ORDERING =
    Ordering
      .natural()
      .onResultOf((@NotNull String input) -> {
        int result = KNOWN_CONFIGURATIONS_IN_ORDER.indexOf(input);
        return result != -1 ? result : KNOWN_CONFIGURATIONS_IN_ORDER.size();
      })
      .compound(Ordering.natural());

  /**
   * Perform an in-place removal of entries from {@code newDependencies} that are also in {@code existingDependencies}
   */
  public static void removeExistingDependencies(@NotNull Map<String, Multimap<String, GradleCoordinate>> newDependencies,
                                                @NotNull Map<String, Multimap<String, GradleCoordinate>> existingDependencies) {
    for (String configuration : newDependencies.keySet()) {
      if (existingDependencies.containsKey(configuration)) {
        for (String coordinateId: existingDependencies.get(configuration).keySet()) {
          newDependencies.get(configuration).removeAll(coordinateId);
        }
      }
    }
  }

  private GradleFileMergers() {}
}
