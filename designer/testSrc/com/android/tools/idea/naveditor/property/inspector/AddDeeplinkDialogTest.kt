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
package com.android.tools.idea.naveditor.property.inspector

import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase

class AddDeeplinkDialogTest : NavTestCase() {

  fun testValidation() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }
    val dialog = AddDeeplinkDialog(model.find("fragment1"))
    dialog.myUriField.text = "http://example.com/foo"
    assertNull(dialog.doValidate())

    dialog.myUriField.text = "http://!@#$"
    assertNull(dialog.doValidate())

    dialog.myUriField.text = "http://example.com/{blah}"
    assertNull(dialog.doValidate())

    dialog.myUriField.text = "http://example.com/{blah"
    assertNotNull(dialog.doValidate())
    dialog.close(0)
  }
}