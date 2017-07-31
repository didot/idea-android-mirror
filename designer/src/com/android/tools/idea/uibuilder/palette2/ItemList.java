/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette2;

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.android.tools.idea.common.util.WhiteIconGenerator;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ExpandableItemsHandler;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * A list control for displaying palette items.
 *
 *      +------------------------+
 *      | ⌸ Button               |
 *      | ⍄ ToggleButton         |
 *      | ⍓ FloatingActionB... ↓ |
 *      |   ...                  |
 *      +------------------------+
 *      Example of an item list.
 *
 * The item names each have an icon to the left that represent the item.
 * An item may also have a download icon to the right of the name indicating
 * that this item requires a project dependency to be added.
 * If an item name is wider than the alotted width, it will be truncated and
 * shown with ellipsis.
 */
public class ItemList extends ListWithMargin<Palette.Item> {
  private final DependencyManager myDependencyManager;

  public ItemList(@NotNull DependencyManager dependencyManager) {
    myDependencyManager = dependencyManager;
    setCellRenderer(new ItemCellRenderer());
  }

  @Override
  protected int getRightMarginWidth() {
    return StudioIcons.LayoutEditor.Extras.PALETTE_DOWNLOAD.getIconWidth();
  }

  private boolean displayFittedTextIfNecessary(int index) {
    return !UIUtil.isClientPropertyTrue(this, ExpandableItemsHandler.EXPANDED_RENDERER) &&
           !getExpandableItemsHandler().getExpandedItems().contains(index);
  }

  private boolean displayDownloadIcon(@NotNull Palette.Item item, int index) {
    return myDependencyManager.needsLibraryLoad(item) &&
           displayFittedTextIfNecessary(index);
  }

  private static class ItemCellRenderer implements ListCellRenderer<Palette.Item> {
    private final JPanel myPanel;
    private final JBLabel myDownloadIcon;
    private final TextCellRenderer myTextRenderer;

    private ItemCellRenderer() {
      myPanel = new JPanel(new BorderLayout());
      myDownloadIcon = new JBLabel();
      myTextRenderer = new TextCellRenderer();
      myPanel.add(myTextRenderer, BorderLayout.CENTER);
      myPanel.add(myDownloadIcon, BorderLayout.EAST);
    }

    @NotNull
    @Override
    public Component getListCellRendererComponent(@NotNull JList<? extends Palette.Item> list,
                                                  @NotNull Palette.Item item,
                                                  int index,
                                                  boolean selected,
                                                  boolean hasFocus) {
      myTextRenderer.getListCellRendererComponent(list, item, index, selected, hasFocus);
      myPanel.setBackground(selected ? UIUtil.getTreeSelectionBackground(hasFocus) : null);
      myPanel.setForeground(UIUtil.getTreeForeground(selected, hasFocus));
      myPanel.setBorder(JBUI.Borders.empty(0, 3));

      ItemList itemList = (ItemList)list;
      myDownloadIcon.setVisible(itemList.displayDownloadIcon(item, index));
      myDownloadIcon.setIcon(selected ? StudioIcons.LayoutEditor.Extras.PALETTE_DOWNLOAD_SELECTED
                                      : StudioIcons.LayoutEditor.Extras.PALETTE_DOWNLOAD);
      return myPanel;
    }
  }

  private static class TextCellRenderer extends ColoredListCellRenderer<Palette.Item> {

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends Palette.Item> list,
                                         @NotNull Palette.Item item,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      ItemList itemList = (ItemList)list;
      Icon icon = item.getIcon();
      String text = item.getTitle();

      if (itemList.displayFittedTextIfNecessary(index)) {
        int leftMargin = icon.getIconWidth() + myIconTextGap + getIpad().right + getIpad().left;
        int rightMargin = StudioIcons.LayoutEditor.Extras.PALETTE_DOWNLOAD.getIconWidth();
        text = AdtUiUtils.getFittedString(list.getFontMetrics(list.getFont()), text, list.getWidth() - leftMargin - rightMargin, 1);
      }

      setBackground(selected ? UIUtil.getTreeSelectionBackground(hasFocus) : null);
      mySelectionForeground = UIUtil.getTreeForeground(selected, hasFocus);
      setIcon(hasFocus ? WhiteIconGenerator.INSTANCE.generateWhiteIcon(icon) : icon);
      append(text);
    }
  }
}
