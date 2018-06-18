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
package com.android.tools.adtui.ptable2.impl

import com.android.tools.adtui.ptable2.DefaultPTableCellRendererProvider
import com.android.tools.adtui.ptable2.item.*
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class PTableModelTest {

  @Test
  fun testBasics() {
    val model = PTableModelImpl(createModel(Item("item1"), Item("item2"), Item("item3")))
    assertThat(model.rowCount).isEqualTo(3)
    assertThat(model.getValueAt(0, 0).name).isEqualTo("item1")
  }

  @Test
  fun testExpandCollapse() {
    val model = PTableModelImpl(createModel(Item("item1"), Item("item2"), Group("item3", Item("child1"), Item("child2"))))
    val listener = addModelListener(model)

    // last node should be collapsed
    assertThat(model.rowCount).isEqualTo(3)

    // expand and check that 2 more nodes have been added
    model.expand(2)
    assertThat(model.rowCount).isEqualTo(5)
    verify(listener)?.tableChanged(ArgumentMatchers.any())

    // 2nd expand is a noop
    model.expand(2)
    assertThat(model.rowCount).isEqualTo(5)
    verify(listener)?.tableChanged(ArgumentMatchers.any())

    // collapse and check that 2 nodes have been removed
    model.collapse(2)
    assertThat(model.rowCount).isEqualTo(3)
    verify(listener, times(2))?.tableChanged(ArgumentMatchers.any())

    // 2nd collapse is a noop
    model.collapse(2)
    assertThat(model.rowCount).isEqualTo(3)
    verify(listener, times(2))?.tableChanged(ArgumentMatchers.any())
  }

  @Test
  fun testToggle() {
    val model = PTableModelImpl(createModel(Item("item1"), Item("item2"), Group("item3", Item("child1"), Item("child2"))))
    val listener = addModelListener(model)

    // last node should be collapsed
    assertThat(model.rowCount).isEqualTo(3)

    // toggle and check that 2 more nodes have been added
    model.toggle(2)
    assertThat(model.rowCount).isEqualTo(5)
    verify(listener)?.tableChanged(ArgumentMatchers.any())

    // toggle and check that 2 nodes have been removed
    model.collapse(2)
    assertThat(model.rowCount).isEqualTo(3)
    verify(listener, times(2))?.tableChanged(ArgumentMatchers.any())
  }

  @Test
  fun testUpdate() {
    val tableModel = createModel(Item("item1"), Item("item2"), Group("item3", Item("child1"), Item("child2")), Item("item4"))
    val model = PTableModelImpl(tableModel)
    model.expand(2)
    tableModel.updateTo(Item("item1"), Item("itemX"), Group("item3", Item("child1"), Item("child2")), Item("item4"), Item("item5"))

    assertThat(model.rowCount).isEqualTo(7)
    assertThat(model.getValueAt(0, 0).name).isEqualTo("item1")
    assertThat(model.getValueAt(1, 0).name).isEqualTo("itemX")
    assertThat(model.getValueAt(2, 0).name).isEqualTo("item3")
    assertThat(model.getValueAt(3, 0).name).isEqualTo("child1")
    assertThat(model.getValueAt(4, 0).name).isEqualTo("child2")
    assertThat(model.getValueAt(5, 0).name).isEqualTo("item4")
    assertThat(model.getValueAt(6, 0).name).isEqualTo("item5")
  }

  @Test
  fun testUpdateWithEditingRestore() {
    val tableModel = createModel(Item("item1"), Item("item2"), Group("item3", Item("child1"), Item("child2")), Item("item4"))
    val table = PTableImpl(tableModel, null, DefaultPTableCellRendererProvider(), DummyPTableCellEditorProvider())
    table.startEditing(3, 1)
    tableModel.updateTo(Item("item1"), Item("itemX"), Group("item3", Item("child1"), Item("child2")), Item("item5"), Item("item4"))

    assertThat(table.editingRow).isEqualTo(4)
    assertThat(table.editingColumn).isEqualTo(1)
  }
}
