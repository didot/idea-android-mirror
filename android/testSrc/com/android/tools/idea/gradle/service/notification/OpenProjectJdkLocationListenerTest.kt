/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification

import com.android.tools.idea.projectsystem.AndroidProjectSettingsService
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class OpenProjectJdkLocationListenerTest {
  @Test
  fun `opens PSD project location when used`() {
    val mockService = mock(AndroidProjectSettingsService::class.java)

    val listener = OpenProjectJdkLocationListener(mockService)
    listener.followLink()
    verify(mockService).chooseJdkLocation()
  }
}