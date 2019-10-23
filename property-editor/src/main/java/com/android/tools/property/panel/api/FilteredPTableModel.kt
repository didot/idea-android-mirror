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
package com.android.tools.property.panel.api

import com.android.tools.property.ptable2.PTableItem
import com.android.tools.property.ptable2.PTableModel
import com.android.tools.property.panel.impl.model.FilteredPTableModelImpl
import com.android.tools.property.ptable2.PTableColumn

/**
 * A [PTableModel] for variable length property tables.
 *
 * There are methods for adding and removing items from the table.
 */
interface FilteredPTableModel<P : PropertyItem> : PTableModel {

  /**
   * Adds a [PropertyItem] to the table model.
   *
   * The added item will be placed in its natural order among the
   * existing items. If the added item implements [NewPropertyItem]
   * it will be placed at the bottom the table.
   * Adding an already existing item is a noop.
   *
   * The returned item is the current item after the operation.
   */
  fun addNewItem(item: P): P

  /**
   * Remove an [item] from the table model.
   *
   * If the deleteItem operation requires an XML update, that update
   * must be specified with the [delete] lambda.
   */
  fun deleteItem(item: P, delete: (P) -> Unit)

  /**
   * Remove an [item] from the table model.
   *
   * Same as the above [deleteItem] method where the delete lambda
   * will set the value of the item to null.
   *
   * The method is overloaded for Java interoperability.
   */
  fun deleteItem(item: P) {
    deleteItem(item) { it.value = null }
  }

  companion object PTableModelFactory {

    /**
     * Comparator that is sorting [PTableItem] in alphabetical sorting order.
     */
    val alphabeticalSortOrder: Comparator<PTableItem> = Comparator.comparing(PTableItem::name)

    /**
     * Create an implementation of this interface.
     *
     * The [model] specifies where the items are retrieved from. Only
     * the items satisfying the [itemFilter] are included in the table.
     *
     * The [refresh] method will repopulate the table with items from
     * the available properties from the [model] applying the [itemFilter].
     * If the model includes an item implementing [NewPropertyItem] that
     * item will be excluded if a corresponding matching item is found
     * in the [model] except if [keepNewAfterFlyAway] is true, then the
     * item will be included at the end of the table after setting its
     * name to null i.e. the new item line will be ready for the user
     * to add another item to the table.
     * The [groups] specifies which item are grouped under a specified
     * group name. The items are sorted using [itemComparator].
     */
    fun <P : PropertyItem> create(
      model: PropertiesModel<P>,
      itemFilter: (P) -> Boolean,
      itemComparator: Comparator<PTableItem> = alphabeticalSortOrder,
      groups: List<GroupSpec<P>> = emptyList(),
      keepNewAfterFlyAway: Boolean = true,
      allowEditing: Boolean = true
    ): FilteredPTableModel<P> {
      return FilteredPTableModelImpl(model, itemFilter, itemComparator, groups, keepNewAfterFlyAway, allowEditing)
    }
  }
}
