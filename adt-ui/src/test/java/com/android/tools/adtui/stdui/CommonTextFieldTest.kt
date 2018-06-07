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
package com.android.tools.adtui.stdui

import com.android.tools.adtui.model.stdui.DefaultCommonTextFieldModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CommonTextFieldTest {
  private val model = DefaultCommonTextFieldModel("")
  private val field = CommonTextField(model)

  @Test
  fun testValuePropagatedToTextFieldFromModel() {
    model.value = "Hello"
    assertThat(field.text).isEqualTo("Hello")
  }

  @Test
  fun testTextPropagatedToModelFromTextField() {
    field.text = "World"
    assertThat(model.text).isEqualTo("World")
  }

  @Test
  fun testErrorStateIsSetAndResetOnTextField() {
    field.text = "Error"
    assertThat(field.getClientProperty(OUTLINE_PROPERTY)).isEqualTo(ERROR_VALUE)
    field.text = "Fixed"
    assertThat(field.getClientProperty(OUTLINE_PROPERTY)).isNull()
  }
}
