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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.AndroidBundle;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.AndroidBundleStub;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import static com.android.tools.idea.gradle.project.model.ide.android.CopyVerification.assertEqualsOrSimilar;

/**
 * Tests for {@link IdeAndroidBundle}.
 */
public class IdeAndroidBundleTest {
  @Test
  public void constructor() throws Throwable {
    AndroidBundle original = new AndroidBundleStub();
    assertEqualsOrSimilar(original, new IdeAndroidBundle(original, new ModelCache()) {});
  }

  @Test
  public void equalsAndHashCode() {
    EqualsVerifier.forClass(IdeAndroidBundle.class).withRedefinedSuperclass().withRedefinedSubclass(IdeAndroidAtom.class).verify();
    EqualsVerifier.forClass(IdeAndroidBundle.class).withRedefinedSuperclass().withRedefinedSubclass(IdeAndroidLibrary.class).verify();
  }
}