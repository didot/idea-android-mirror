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

import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeArtifact
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeFile
import java.io.File
import java.io.Serializable

data class IdeNativeArtifactImpl(
  override val name: String,
  override val toolChain: String,
  override val groupName: String,
  override val sourceFiles: Collection<IdeNativeFile>,
  override val exportedHeaders: Collection<File>,
  override val outputFile: File?,
  override val abi: String,
  override val targetName: String

) : IdeNativeArtifact, Serializable
