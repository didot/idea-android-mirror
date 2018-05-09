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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.details

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.ui.properties.simplePropertyEditor
import com.android.tools.idea.gradle.structure.model.PsDeclaredLibraryDependency
import com.android.tools.idea.gradle.structure.model.meta.PropertyUiModel
import com.android.tools.idea.gradle.structure.model.meta.PropertyUiModelImpl

object DeclaredLibraryDependencyUiProperties {
  fun makeVersionUiProperty(context: PsContext, dependency: PsDeclaredLibraryDependency): PropertyUiModel<Unit, *> =
    PropertyUiModelImpl(dependency.versionProperty, ::simplePropertyEditor,context.getArtifactRepositorySearchServiceFor(dependency.parent))
}