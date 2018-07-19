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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.stubs

import com.android.builder.model.AndroidLibrary
import com.android.builder.model.Dependencies
import com.android.builder.model.JavaLibrary

data class DependenciesStub(
  private val libraries: Collection<AndroidLibrary> = listOf(),
  private val javaLibraries: Collection<JavaLibrary> = listOf(),
  private val projects: Collection<String> = listOf("project1", "project2"),
  private val javaModules: Collection<Dependencies.ProjectIdentifier> = listOf()
) : Dependencies {
  override fun getLibraries(): Collection<AndroidLibrary> = libraries
  override fun getJavaLibraries(): Collection<JavaLibrary> = javaLibraries
  @Deprecated("superseded by java modules", ReplaceWith("getJavaModules()"))
  override fun getProjects(): Collection<String> = projects
  override fun getJavaModules(): Collection<Dependencies.ProjectIdentifier> = javaModules
}
