/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.hprof.tables.heaptable;

import com.android.tools.idea.editors.hprof.tables.HprofTable;
import com.android.tools.idea.editors.hprof.tables.instancestable.InstancesTreeTable;
import com.android.tools.perflib.heap.ClassObj;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class HeapTable extends HprofTable {
  @NotNull private InstancesTreeTable myInstancesTreeTable;

  public HeapTable(@NotNull HeapTableModel model, @NotNull InstancesTreeTable instancesTreeTable) {
    super(model);
    myInstancesTreeTable = instancesTreeTable;
    setShowGrid(false);

    getColumnModel().getColumn(0).setCellRenderer(new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        if (value instanceof ClassObj) {
          ClassObj clazz = (ClassObj)value;
          String name = clazz.getClassName();
          String pkg = null;
          int i = name.lastIndexOf(".");
          if (i != -1) {
            pkg = name.substring(0, i);
            name = name.substring(i + 1);
          }
          append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
          if (pkg != null) {
            append(" (" + pkg + ")", new SimpleTextAttributes(Font.PLAIN, JBColor.GRAY));
          }
          setIcon(PlatformIcons.CLASS_ICON);
          // TODO reformat anonymous classes (ANONYMOUS_CLASS_ICON) to match IJ.
        }
      }
    });
  }

  @Override
  public void notifyDominatorsComputed() {
    super.notifyDominatorsComputed();
    myInstancesTreeTable.notifyDominatorsComputed();
  }
}
