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
package com.android.tools.idea.uibuilder.structure;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;

/**
 * Finds at after which row of a JTree the component being dragged will be inserted.
 */
class NlDropInsertionPicker {

  private final JTree myTree;
  private List<NlComponent> myDragged;

  /**
   * Construct a new {@link NlDropInsertionPicker} that will pick the insertion point from
   * the provided tree
   * @param tree The tree used to find the insertion point
   */
  public NlDropInsertionPicker(@NotNull NlComponentTree tree) {
    this((JTree)tree);
  }

  @VisibleForTesting
  NlDropInsertionPicker(@NotNull JTree tree) {
    myTree = tree;
  }

  /**
   * Find the {@link NlComponent} that will receive the dragged components and
   * the component before which the dragged component will be inserted
   *
   * @param location Coordinate where to find the insertion point in the {@link JTree}
   * @param dragged  The component being dragged
   * @return an array of two ints used to know where to display the insertion point or null if
   * the components can't be inserted at the location of the event.
   * <p>The first int represents the row number of the path in the Tree after
   * which the dragged component will be inserted.</p>
   * <p> The second int represent the depth relative the path represented by the first int.
   * <ul>
   * <li>if the depth == 0, the insertion point will be inside the parent of the selected row</li>
   * <li>if the depth > 0, the insertion point will be inside the component on the selected row.</li>
   * <li>if the depth < 0, the insertion point will in one of the parent's ancestor. The level of the ancestor is defined by depth.</li>
   * <ul>
   * <li>-1 is the grand-parent path</li>
   * <li>-2 is the grand-grand-parent path</li>
   * <li>etc...</li>
   * </ul>
   * </li>
   * </ul>
   * </p>
   */
  @Nullable
  public Result findInsertionPointAt(@NotNull Point location,
                                     @NotNull List<NlComponent> dragged) {
    myDragged = dragged;
    Result result = new Result();
    result.receiver = null;
    result.nextComponent = null;
    TreePath referencePath = myTree.getClosestPathForLocation(location.x, location.y);
    result.row = myTree.getRowForPath(referencePath);

    if (referencePath == null) {
      return null;
    }

    result.depth = 1;
    Object last = referencePath.getLastPathComponent();
    if (!(last instanceof NlComponent)) {
      return null;
    }
    NlComponent receiverComponent = (NlComponent)last;

    if (canChangeInsertionDepth(referencePath, receiverComponent)) {
      TreePath parentPath;
      Rectangle referenceBounds;

      // This allows the user to select the previous ancestor by moving
      // the cursor to the left only if there is some ambiguity to define where
      // the insertion should be.
      // There is an ambiguity if either the user tries to insert the component after the last row
      // in between a component deeper than the one on the next row:
      // shall we insert at the component after the last leaf or after the last leaf acestor
      // -- root
      //   |-- parent
      //       |-- child1
      //       |-- child2
      // ..................
      //       |-- potential Insertion point 1 <---
      //   |-- potential Insertion point 2     <---
      //
      while ((parentPath = referencePath.getParentPath()) != null
             && (referenceBounds = myTree.getPathBounds(referencePath)) != null
             && canSelectLowerDepth(result.row, result.depth)
             && location.x < referenceBounds.x) {
        result.depth--;
        referencePath = parentPath;
      }
      receiverComponent = (NlComponent)referencePath.getLastPathComponent();
    }

    if (canAddComponent(receiverComponent.getModel(), receiverComponent)) {
      // The receiver is a ViewGroup and can accept component
      result.receiver = receiverComponent;
      if (receiverComponent.getChildCount() != 0) {
        TreePath nextPath = myTree.getPathForRow(result.row + 1);
        result.nextComponent = nextPath == null ? null : (NlComponent)nextPath.getLastPathComponent();
      }
    }
    else {
      // The receiver is not a ViewGroup or so we need to insert in the parent
      NlComponent parent = receiverComponent.getParent();
      result.depth--;
      if (parent == null) {
        result.receiver = receiverComponent;
      }
      else {
        result.receiver = parent;
        result.nextComponent = receiverComponent.getNextSibling();
      }

      // Now that we should be able to add the component, we do a final check.
      // It should almost never fall in this case.
      if (!canAddComponent(result.receiver.getModel(), result.receiver)) {
        result.receiver = null;
        result.nextComponent = null;
        return null;
      }
    }
    return result;
  }

  /**
   * If the path is the last of its parent's child or if the path is collapsed,
   * we can allow to change the depth of the insertion
   *
   * @param path      The current path under the mouse
   * @param component The component hold by the path
   * @return true if the insertion depth can be modified
   */
  private boolean canChangeInsertionDepth(@NotNull TreePath path, @NotNull NlComponent component) {
    return component.getNextSibling() == null && myTree.getExpandedDescendants(path) == null;
  }

  protected boolean canAddComponent(@NotNull NlModel model, @NotNull NlComponent receiver) {
    return model.canAddComponents(myDragged, receiver, receiver.getChild(0))
           || (NlModel.isMorphableToViewGroup(receiver)
               && !NlModel.isDescendant(receiver, myDragged));
  }

  private boolean canSelectLowerDepth(int row, int relativeDepth) {
    return row == myTree.getRowCount() - 1 || relativeDepth > -1;
  }

  public static class Result {
    NlComponent receiver;
    NlComponent nextComponent;
    int depth;
    int row;
  }
}
