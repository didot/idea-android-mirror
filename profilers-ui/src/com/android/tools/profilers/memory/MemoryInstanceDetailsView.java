/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.memory.adapters.*;
import com.android.tools.profilers.memory.adapters.CaptureObject.InstanceAttribute;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.android.tools.profilers.stacktrace.StackTraceView;
import com.android.tools.profilers.stacktrace.TabsPanel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.android.tools.profilers.memory.adapters.MemoryObject.INVALID_VALUE;

/**
 * A view object that is responsible for displaying the callstack + references of an {@link InstanceObject} based on whether the
 * information is available. If no detailed information can be obtained from the InstanceObject, this UI is responsible
 * for automatically hiding itself.
 */
final class MemoryInstanceDetailsView extends AspectObserver {
  private static final String TITLE_TAB_REFERENCES = "References";
  private static final String TITLE_TAB_CALLSTACK = "Call Stack";
  private static final int LABEL_COLUMN_WIDTH = 500;
  private static final int DEFAULT_COLUMN_WIDTH = 80;

  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private final IdeProfilerComponents myIdeProfilerComponents;

  @NotNull private final TabsPanel myTabsPanel;

  @NotNull private final StackTraceView myStackTraceView;

  @Nullable private JComponent myReferenceColumnTree;

  @Nullable private JTree myReferenceTree;

  @NotNull private final Map<InstanceAttribute, AttributeColumn> myAttributeColumns = new HashMap<>();

  public MemoryInstanceDetailsView(@NotNull MemoryProfilerStage stage, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myStage = stage;
    myStage.getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_INSTANCE, this::instanceChanged);
    myIdeProfilerComponents = ideProfilerComponents;

    myTabsPanel = ideProfilerComponents.createTabsPanel();
    myTabsPanel.setOnSelectionChange(this::trackActiveTab);
    myStackTraceView = ideProfilerComponents.createStackView(stage.getStackTraceModel());

    myAttributeColumns.put(
      InstanceAttribute.LABEL,
      new AttributeColumn<ValueObject>(
        "Reference",
        () -> new SimpleColumnRenderer<ValueObject>(
          node -> {
            StringBuilder builder = new StringBuilder();
            if (node.getAdapter() instanceof ReferenceObject) {
              builder.append(node.getAdapter().getName());
              builder.append(" in ");
            }
            builder.append(node.getAdapter().getValueText());
            return builder.toString();
          },
          value -> MemoryProfilerStageView.getValueObjectIcon(value.getAdapter()),
          SwingConstants.LEFT),
        SwingConstants.LEFT,
        LABEL_COLUMN_WIDTH,
        Comparator.comparing(o -> (o.getAdapter()).getName())));
    myAttributeColumns.put(
      InstanceAttribute.DEPTH,
      new AttributeColumn<ValueObject>(
        "Depth",
        () -> new SimpleColumnRenderer<ValueObject>(value -> {
          int depth = value.getAdapter().getDepth();
          if (depth >= 0 && depth < Integer.MAX_VALUE) {
            return Integer.toString(depth);
          }
          return "";
        }, value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        Comparator.comparingInt(o -> o.getAdapter().getDepth())));
    myAttributeColumns.put(
      InstanceAttribute.SHALLOW_SIZE,
      new AttributeColumn<ValueObject>(
        "Shallow Size",
        () -> new SimpleColumnRenderer<ValueObject>(
          value -> value.getAdapter().getShallowSize() != INVALID_VALUE ? Integer.toString(value.getAdapter().getShallowSize()) : "",
          value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        Comparator.comparingInt(o -> o.getAdapter().getShallowSize())));
    myAttributeColumns.put(
      InstanceAttribute.RETAINED_SIZE,
      new AttributeColumn<ValueObject>(
        "Retained Size",
        () -> new SimpleColumnRenderer<ValueObject>(
          value -> value.getAdapter().getShallowSize() != INVALID_VALUE ? Long.toString(value.getAdapter().getRetainedSize()) : "",
          value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        (o1, o2) -> {
          long diff = o1.getAdapter().getRetainedSize() - o2.getAdapter().getRetainedSize();
          return diff > 0 ? 1 : (diff < 0 ? -1 : 0);
        }));

    // Fires the handler once at the beginning to ensure we are sync'd with the latest selection state in the MemoryProfilerStage.
    instanceChanged();
  }

  private void trackActiveTab() {
    FeatureTracker featureTracker = myStage.getStudioProfilers().getIdeServices().getFeatureTracker();
    String selectedTab = myTabsPanel.getSelectedTab();
    if (selectedTab == null) {
      return;
    }
    switch (selectedTab) {
      case TITLE_TAB_REFERENCES:
        featureTracker.trackSelectMemoryReferences();
        break;
      case TITLE_TAB_CALLSTACK:
        featureTracker.trackSelectMemoryStack();
        break;
      default:
        // Intentional no-op
        break;
    }
  }

  @NotNull
  JComponent getComponent() {
    return myTabsPanel.getComponent();
  }

  @VisibleForTesting
  @Nullable
  JTree getReferenceTree() {
    return myReferenceTree;
  }

  @VisibleForTesting
  @Nullable
  JComponent getReferenceColumnTree() {
    return myReferenceColumnTree;
  }

  private void instanceChanged() {
    CaptureObject capture = myStage.getSelectedCapture();
    InstanceObject instance = myStage.getSelectedInstanceObject();
    if (capture == null || instance == null) {
      myReferenceTree = null;
      myReferenceColumnTree = null;
      myTabsPanel.getComponent().setVisible(false);
      return;
    }

    myTabsPanel.removeAll();
    boolean hasContent = false;

    // Populate references
    myReferenceColumnTree = buildReferenceColumnTree(capture, instance);
    if (myReferenceColumnTree != null) {
      myTabsPanel.addTab(TITLE_TAB_REFERENCES, myReferenceColumnTree);
      hasContent = true;
    }

    // Populate Callstacks
    AllocationStack callStack = instance.getCallStack();
    if (callStack != null && !callStack.getStackFramesList().isEmpty()) {
      List<CodeLocation> stackFrames = callStack.getStackFramesList().stream()
        .map(AllocationStackConverter::getCodeLocation)
        .collect(Collectors.toList());
      myStackTraceView.getModel().setStackFrames(instance.getAllocationThreadId(), stackFrames);
      myTabsPanel.addTab(TITLE_TAB_CALLSTACK, myStackTraceView.getComponent());
      hasContent = true;
    }

    myTabsPanel.getComponent().setVisible(hasContent);
  }

  @Nullable
  private JComponent buildReferenceColumnTree(@NotNull CaptureObject captureObject, @NotNull InstanceObject instance) {
    if (instance.getReferences().isEmpty()) {
      myReferenceTree = null;
      return null;
    }

    myReferenceTree = buildTree(instance);
    ColumnTreeBuilder builder = new ColumnTreeBuilder(myReferenceTree);
    for (InstanceAttribute attribute : captureObject.getInstanceAttributes()) {
      ColumnTreeBuilder.ColumnBuilder column = myAttributeColumns.get(attribute).getBuilder();
      if (attribute == InstanceAttribute.DEPTH) {
        column.setInitialOrder(attribute.getSortOrder());
      }
      builder.addColumn(column);
    }
    builder.setTreeSorter((Comparator<MemoryObjectTreeNode<MemoryObject>> comparator, SortOrder sortOrder) -> {
      assert myReferenceTree.getModel() instanceof DefaultTreeModel;
      DefaultTreeModel treeModel = (DefaultTreeModel)myReferenceTree.getModel();
      assert treeModel.getRoot() instanceof MemoryObjectTreeNode;
      //noinspection unchecked
      MemoryObjectTreeNode<MemoryObject> root = (MemoryObjectTreeNode<MemoryObject>)treeModel.getRoot();
      root.sort(comparator);
      treeModel.nodeStructureChanged(root);
    });

    builder.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    return builder.build();
  }

  @VisibleForTesting
  @NotNull
  JTree buildTree(@NotNull InstanceObject instance) {
    Comparator<MemoryObjectTreeNode<ValueObject>> comparator = null;
    if (myReferenceTree != null && myReferenceTree.getModel() != null && myReferenceTree.getModel().getRoot() != null) {
      Object root = myReferenceTree.getModel().getRoot();
      if (root instanceof ReferenceTreeNode) {
        comparator = ((ReferenceTreeNode)root).getComparator();
      }
    }

    final ReferenceTreeNode treeRoot = new ReferenceTreeNode(instance);
    treeRoot.expandNode();

    if (comparator != null) {
      treeRoot.sort(comparator);
    }

    final DefaultTreeModel treeModel = new DefaultTreeModel(treeRoot);
    final JTree tree = new Tree(treeModel);
    tree.setRootVisible(true);
    tree.setShowsRootHandles(true);

    // Not all nodes have been populated during buildReferenceColumnTree. Here we capture the TreeExpansionEvent to check whether any children
    // under the expanded node need to be populated.
    tree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        TreePath path = event.getPath();

        assert path.getLastPathComponent() instanceof MemoryObjectTreeNode;
        ReferenceTreeNode treeNode = (ReferenceTreeNode)path.getLastPathComponent();
        treeNode.expandNode();
        treeModel.nodeStructureChanged(treeNode);
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
      }
    });

    myIdeProfilerComponents.installNavigationContextMenu(tree, myStage.getStudioProfilers().getIdeServices().getCodeNavigator(), () -> {
      TreePath selection = tree.getSelectionPath();
      if (selection == null) {
        return null;
      }

      MemoryObject memoryObject = ((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter();
      if (memoryObject instanceof InstanceObject) {
        return new CodeLocation.Builder(((InstanceObject)memoryObject).getClassEntry().getClassName()).build();
      }
      else {
        assert memoryObject instanceof ReferenceObject;
        return new CodeLocation.Builder(((ReferenceObject)memoryObject).getReferenceInstance().getClassEntry().getClassName()).build();
      }
    });

    myIdeProfilerComponents.installContextMenu(tree, new ContextMenuItem() {
      @NotNull
      @Override
      public String getText() {
        return "Go to Instance";
      }

      @Nullable
      @Override
      public Icon getIcon() {
        return null;
      }

      @Override
      public boolean isEnabled() {
        return tree.getSelectionPath() != null;
      }

      @Override
      public void run() {
        CaptureObject captureObject = myStage.getSelectedCapture();
        TreePath selection = tree.getSelectionPath();
        assert captureObject != null && selection != null;
        MemoryObject memoryObject = ((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter();
        if (memoryObject instanceof InstanceObject) {
          assert memoryObject == myStage.getSelectedInstanceObject();
          // don't do anything because the only instance object in the tree is the one already selected
        }
        else {
          assert memoryObject instanceof ReferenceObject;
          InstanceObject targetInstance = ((ReferenceObject)memoryObject).getReferenceInstance();
          HeapSet heapSet = captureObject.getHeapSet(targetInstance.getHeapId());
          assert heapSet != null;
          myStage.selectHeapSet(heapSet);
          ClassifierSet classifierSet = heapSet.findContainingClassifierSet(targetInstance);
          assert classifierSet != null && classifierSet instanceof ClassSet;
          myStage.selectClassSet((ClassSet)classifierSet);
          myStage.selectInstanceObject(targetInstance);
        }
      }
    });

    return tree;
  }

  private static class ReferenceTreeNode extends LazyMemoryObjectTreeNode<ValueObject> {
    @Nullable
    private List<ReferenceObject> myReferenceObjects = null;

    private ReferenceTreeNode(@NotNull ValueObject valueObject) {
      super(valueObject, false);
    }

    @Override
    public int computeChildrenCount() {
      if (myReferenceObjects == null) {
        if (getAdapter() instanceof InstanceObject) {
          myReferenceObjects = ((InstanceObject)getAdapter()).getReferences();
        }
        else if (getAdapter() instanceof ReferenceObject) {
          myReferenceObjects = ((ReferenceObject)getAdapter()).getReferenceInstance().getReferences();
        }
        else {
          myReferenceObjects = Collections.emptyList();
        }
      }

      return myReferenceObjects.size();
    }

    @Override
    public void expandNode() {
      getChildCount(); // ensure we grab all the references
      assert myReferenceObjects != null;
      if (myMemoizedChildrenCount != myChildren.size()) {
        myReferenceObjects.forEach(reference -> {
          ReferenceTreeNode node = new ReferenceTreeNode(reference);
          node.setTreeModel(getTreeModel());
          add(node);
        });
      }
    }
  }
}
