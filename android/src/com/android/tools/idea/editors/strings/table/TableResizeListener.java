/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.table;

import javax.swing.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class TableResizeListener extends ComponentAdapter {
  private final JTable myTable;

  public TableResizeListener(JTable table) {
    myTable = table;
  }

  @Override
  public void componentResized(ComponentEvent e) {
    // If necessary, grows the table to fill the viewport.
    // Does not trigger when the user drags a column to resize (taken care of by ColumnResizeListener).
    if (myTable.getTableHeader().getResizingColumn() == null) {
      StringResourceTableUtil.expandToViewportWidthIfNecessary(myTable, -1);
    }
  }
}
