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

import static com.google.common.truth.Truth.assertThat;

import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;

public class LibraryFixture {
  @NotNull private final Library myLibrary;

  LibraryFixture(@NotNull Library library) {
    myLibrary = library;
  }

  @NotNull
  public LibraryFixture requireJavadocUrls(@NotNull String... urls) {
    String[] actualUrls = myLibrary.getUrls(JavadocOrderRootType.getInstance());
    assertThat(actualUrls).named("Javadoc URLs").asList().containsExactly(urls);
    return this;
  }
}
