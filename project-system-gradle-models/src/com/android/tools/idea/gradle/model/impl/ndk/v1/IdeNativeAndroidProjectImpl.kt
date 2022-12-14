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
package com.android.tools.idea.gradle.model.impl.ndk.v1

import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeAndroidProject
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeArtifact
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeSettings
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeToolchain
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeVariantInfo
import java.io.File
import java.io.Serializable

data class IdeNativeAndroidProjectImpl(
  override val modelVersion: String,
  override val name: String,
  override val buildFiles: Collection<File>,
  override val variantInfos: Map<String, IdeNativeVariantInfo>,
  override val artifacts: Collection<IdeNativeArtifact>,
  override val toolChains: Collection<IdeNativeToolchain>,
  override val settings: Collection<IdeNativeSettings>,
  override val fileExtensions: Map<String, String>,
  override val buildSystems: Collection<String>,
  override val defaultNdkVersion: String,
  override val ndkVersion: String,
  override val apiVersion: Int
) : IdeNativeAndroidProject, Serializable
