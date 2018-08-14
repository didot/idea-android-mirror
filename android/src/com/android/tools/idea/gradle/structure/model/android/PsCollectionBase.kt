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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.structure.model.ChangeDispatcher
import com.android.tools.idea.gradle.structure.model.PsModel
import com.android.tools.idea.gradle.structure.model.PsModelCollection
import com.intellij.openapi.Disposable

abstract class PsCollectionBase<TModel : PsModel, TKey, TParent : PsModel>
protected constructor(val parent: TParent) :
  PsModelCollection<TModel> {
  private val changedDispatcher = ChangeDispatcher()

  protected abstract fun getKeys(from: TParent): Set<TKey>
  protected abstract fun create(key: TKey): TModel
  protected abstract fun update(key: TKey, model: TModel)

  var entries: Map<TKey, TModel> = mapOf(); protected set

  override fun forEach(consumer: (TModel) -> Unit) = entries.values.forEach(consumer)

  override val items: Collection<TModel> get() = entries.values

  fun findElement(key: TKey): TModel? = entries[key]

  fun refresh() {
    entries = getKeys(parent).map { key -> key to (entries[key] ?: create(key)) }.toMap()
    entries.forEach { key, value -> update(key, value) }
    notifyChanged()
  }

  override fun onChange(disposable: Disposable, listener: () -> Unit) = changedDispatcher.add(disposable, listener)

  protected fun notifyChanged() = changedDispatcher.changed()
}

abstract class PsMutableCollectionBase<TModel : PsModel, TKey, TParent : PsModel> protected constructor(parent: TParent)
  : PsCollectionBase<TModel, TKey, TParent>(parent) {

  protected abstract fun instantiateNew(key: TKey)
  protected abstract fun removeExisting(key: TKey)

  fun addNew(key: TKey): TModel {
    if (entries.containsKey(key)) throw IllegalArgumentException("Duplicate key: $key")
    instantiateNew(key)
    val model = create(key).also { update(key, it) }
    entries += (key to model)
    parent.isModified = true
    notifyChanged()
    return model
  }

  fun remove(key: TKey) {
    if (!entries.containsKey(key)) throw IllegalArgumentException("Key not found: $key")
    removeExisting(key)
    entries -= key
    parent.isModified = true
    notifyChanged()
  }

  // TODO(b/111739005): support renames
}
