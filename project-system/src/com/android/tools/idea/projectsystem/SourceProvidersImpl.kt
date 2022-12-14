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
package com.android.tools.idea.projectsystem

class SourceProvidersImpl(
  override val mainIdeaSourceProvider: NamedIdeaSourceProvider,
  override val currentSourceProviders: List<NamedIdeaSourceProvider>,
  override val currentUnitTestSourceProviders: List<NamedIdeaSourceProvider>,
  override val currentAndroidTestSourceProviders: List<NamedIdeaSourceProvider>,
  override val currentTestFixturesSourceProviders: List<NamedIdeaSourceProvider>,
  override val currentAndSomeFrequentlyUsedInactiveSourceProviders: List<NamedIdeaSourceProvider>,

  @Suppress("OverridingDeprecatedMember")
  override val mainAndFlavorSourceProviders: List<NamedIdeaSourceProvider>,
  override val generatedSources: IdeaSourceProvider,
  override val generatedUnitTestSources: IdeaSourceProvider,
  override val generatedAndroidTestSources: IdeaSourceProvider,
  override val generatedTestFixturesSources: IdeaSourceProvider
) : SourceProviders {
  override val sources: IdeaSourceProvider =
    createMergedSourceProvider(ScopeType.MAIN, currentSourceProviders)
  override val unitTestSources: IdeaSourceProvider =
    createMergedSourceProvider(ScopeType.UNIT_TEST, currentUnitTestSourceProviders)
  override val androidTestSources: IdeaSourceProvider =
    createMergedSourceProvider(ScopeType.ANDROID_TEST, currentAndroidTestSourceProviders)
  override val testFixturesSources: IdeaSourceProvider =
    createMergedSourceProvider(ScopeType.TEST_FIXTURES, currentTestFixturesSourceProviders)
}
