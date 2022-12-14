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
package com.android.tools.idea.gradle.dsl.parser.semantics;

public enum MethodSemanticsDescription implements SemanticsDescription {
  /**
   * A zero-argument getter method.
   */
  GET,
  /**
   * A one-argument setter method.
   */
  SET,
  /**
   * The method's varargs argument list is the new value of the property.
   */
  ADD_AS_LIST,
  /**
   * The method's single argument (atom or singleton list) or varargs argument list is used to augment the current value of the
   * property -- despite its name, this can apply to (mutable) Sets as well as (mutable) Lists.
   */
  AUGMENT_LIST,
  /**
   * The property is cleared, and then the method's argument list is used to augment the (now-empty) collection property, as with
   * AUGMENT_LIST.
   */
  CLEAR_AND_AUGMENT_LIST,
  /**
   * The method's single map argument is used to augment the current value of the property.
   */
  AUGMENT_MAP,
  /**
   * Resets a collection property to its initial state
   */
  RESET,
  /**
   * Anything else (semantics currently implemented explicitly in DslElement classes).
   */
  OTHER
}
