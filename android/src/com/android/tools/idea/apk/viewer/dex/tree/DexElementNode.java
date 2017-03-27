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
package com.android.tools.idea.apk.viewer.dex.tree;

import com.android.tools.proguard.ProguardMap;
import com.android.tools.proguard.ProguardSeedsMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jf.dexlib2.iface.reference.Reference;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.Collections;
import java.util.Comparator;

public abstract class DexElementNode extends DefaultMutableTreeNode {

  @NotNull private final String myName;
  @Nullable private final Reference myReference;

  protected int myMethodReferencesCount = 0;
  protected int myDefinedMethodsCount = 0;
  protected boolean removed = true;
  protected boolean hasClassDefinition;

  DexElementNode(@NotNull String name, boolean allowsChildren) {
    this(name, allowsChildren, null);
  }

  DexElementNode(@NotNull String name, boolean allowsChildren, @Nullable Reference reference) {
    super(null, allowsChildren);
    myName = name;
    myReference = reference;
  }

  public abstract Icon getIcon();

  @NotNull
  public String getName(){
    return myName;
  }

  @Nullable
  public Reference getReference() {
    return myReference;
  }

  @Override
  public DexElementNode getChildAt(int i) {
    return (DexElementNode)super.getChildAt(i);
  }

  public void sort(Comparator<DexElementNode> comparator) {
    for (int i = 0; i < getChildCount(); i++) {
      DexElementNode node = getChildAt(i);
      node.sort(comparator);
    }
    if (children != null) {
      Collections.sort(children, comparator);
    }
    
  }

  @Nullable protected <T extends DexElementNode> T getChildByType(@NotNull String name, Class<T> type) {
    for (int i = 0; i < getChildCount(); i++) {
      DexElementNode node = getChildAt(i);
      if (name.equals(node.getName()) && type.equals(node.getClass())) {
        return (T)node;
      }
    }

    return null;
  }

  public int getMethodRefCount() {
    return myMethodReferencesCount;
  }

  public int getDefinedMethodsCount() {
    return myDefinedMethodsCount;
  }

  public boolean isRemoved() {
    return removed;
  }

  public boolean hasClassDefinition() {
    return hasClassDefinition;
  }

  //TODO: does this belong here?
  //Or should it be ProguardSeedsMap::isSeed(Reference reference, ProguardMap map)
  public boolean isSeed(@Nullable ProguardSeedsMap seedsMap, @Nullable ProguardMap map) {
    if (seedsMap != null) {
      for (int i = 0, n = getChildCount(); i < n; i++) {
        DexElementNode node = getChildAt(i);
        if (node.isSeed(seedsMap, map)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public DexElementNode getParent() {
    return (DexElementNode) super.getParent();
  }
}
