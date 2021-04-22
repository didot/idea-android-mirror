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
package com.android.tools.idea.gradle.model.impl.ndk.v2

import com.android.tools.idea.gradle.model.ndk.v2.IdeNativeModule
import com.android.tools.idea.gradle.model.ndk.v2.IdeNativeVariant
import com.android.tools.idea.gradle.model.ndk.v2.NativeBuildSystem
import java.io.File
import java.io.Serializable

data class IdeNativeModuleImpl(
  override val name: String,
  override val variants: List<IdeNativeVariant>,
  override val nativeBuildSystem: NativeBuildSystem,
  override val ndkVersion: String,
  override val defaultNdkVersion: String,
  override val externalNativeBuildFile: File
) : IdeNativeModule, Serializable
