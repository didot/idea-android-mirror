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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.LibraryProto

/**
 * Wrapper for [artifactAddress], common library type.
 *
 * It should never be implemented, use more specific types such as [AndroidLibrary] instead.
 */
interface Library {
  val artifactAddress: String
}

// Outside because of inheritance.
fun Library.toProto(): LibraryProto.Library = LibraryProto.Library.newBuilder()
  .setArtifactAddress(artifactAddress)
  .build()!!
