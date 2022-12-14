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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.intellij.openapi.ui.FixedSizeButton;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import org.junit.Test;

/**
 * Tests for {@link FluentMatcher}.
 */
public class FluentMatcherTest {
  @Test
  public void testCombinations() {
    JButton foo = new JButton("foo");
    foo.setName("fooName");
    assertTrue(Matchers.byName(JButton.class, "fooName").and(Matchers.byText(JButton.class, "foo")).matches(foo));
    assertFalse(Matchers.byName(JButton.class, "fooName").and(Matchers.byText(JButton.class, "bar")).matches(foo));
    assertFalse(Matchers.byName(JButton.class, "fooName").negate().matches(foo));
    assertFalse(Matchers.byName(JButton.class, "fooName").andIsShowing().matches(foo));

    assertFalse(Matchers.byType(AbstractButton.class).negate().matches(foo));
    assertTrue(Matchers.byType(FixedSizeButton.class).negate().matches(foo));
    assertTrue(Matchers.byType(FixedSizeButton.class).negate().and(Matchers.byType(JButton.class)).matches(foo));

    assertFalse(Matchers.byType(AbstractButton.class).negate(JButton.class).matches(foo));
    assertTrue(Matchers.byType(FixedSizeButton.class).negate(JButton.class).matches(foo));
    assertTrue(Matchers.byType(FixedSizeButton.class).negate(JButton.class).and(Matchers.byType(JButton.class)).matches(foo));
  }
}
