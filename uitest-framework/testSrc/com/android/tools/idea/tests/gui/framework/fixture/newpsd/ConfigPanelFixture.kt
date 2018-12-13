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
package com.android.tools.idea.tests.gui.framework.fixture.newpsd

import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.IdeFrameContainerFixture
import com.android.tools.idea.tests.gui.framework.find
import com.android.tools.idea.tests.gui.framework.findByType
import com.android.tools.idea.tests.gui.framework.robot
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.ui.components.JBList
import com.intellij.ui.treeStructure.Tree
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.edt.GuiQuery
import org.fest.swing.fixture.JTreeFixture

abstract class ConfigPanelFixture protected constructor(
) : IdeFrameContainerFixture {

  protected fun clickToolButton(titlePrefix: String) {
    val button = robot().finder().find<ActionButton>(container) { it.toolTipText?.startsWith(titlePrefix) ?: false }
    robot().click(button)
  }
}

/**
 * Returns the text of all the items in the tree. Nested items are prefixed with '-'.
 */
fun ConfigPanelFixture.items(): List<String> =
  JTreeFixture(robot(), robot().finder().findByType<Tree>(container)).items()

private fun JTreeFixture.items(): List<String> {
  val treeModel = target().model

  return GuiQuery.get {
    fun getItems(node: Any, indexBase: Int = 0, prefix: String = ""): List<String> =
      (0 until treeModel.getChildCount(node))
        .flatMap {
          listOf(prefix + valueAt(indexBase + it).orEmpty()) +
          getItems(treeModel.getChild(node, it), indexBase = indexBase + 1 + it, prefix = "$prefix-")
        }
    getItems(treeModel.root)
  }!!
}

/**
 * Returns the popup list being displayed, assuming there is one and it is the only one.
 */
internal fun ConfigPanelFixture.getList(): JBList<*> {
  return GuiTests.waitUntilShowingAndEnabled<JBList<*>>(robot(), null, object : GenericTypeMatcher<JBList<*>>(JBList::class.java) {
    override fun isMatching(list: JBList<*>): Boolean {
      return list.javaClass.name == "com.intellij.ui.popup.list.ListPopupImpl\$MyList"
    }
  })
}
