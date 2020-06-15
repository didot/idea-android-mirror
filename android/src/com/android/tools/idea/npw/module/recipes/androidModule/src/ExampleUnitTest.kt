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
package com.android.tools.idea.npw.module.recipes.androidModule.src

import com.android.tools.idea.wizard.template.escapeKotlinIdentifier

fun exampleUnitTestKt(packageName: String) = """
  package ${escapeKotlinIdentifier(packageName)}

  import org.junit.Test

  import org.junit.Assert.*

  /**
   * Example local unit test, which will execute on the development machine (host).
   *
   * See [testing documentation](http://d.android.com/tools/testing).
   */
  class ExampleUnitTest {
      @Test
      fun addition_isCorrect() {
          assertEquals(4, 2 + 2)
      }
  }
"""

fun exampleUnitTestJava(packageName: String) = """
  package ${packageName};

  import org.junit.Test;

  import static org.junit.Assert.*;

  /**
   * Example local unit test, which will execute on the development machine (host).
   *
   * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
   */
  public class ExampleUnitTest {
      @Test
      public void addition_isCorrect() {
          assertEquals(4, 2 + 2);
      }
  }
"""
