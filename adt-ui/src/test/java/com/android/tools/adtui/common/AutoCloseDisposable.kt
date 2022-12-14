/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.common

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

/**
 * A {@link Disposable} that can be used in try-with-resources to limit the lifetime of disposable children.
 */
class AutoCloseDisposable : Disposable, AutoCloseable {
  override fun dispose() {}

  override fun close() {
    Disposer.dispose(this)
  }
}
