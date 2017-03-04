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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.common.ColumnTreeTestInfo;
import com.android.tools.profilers.*;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.memory.adapters.*;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.*;
import static com.android.tools.profilers.memory.MemoryProfilerTestBase.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.spy;

public class MemoryClassViewTest {
  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MEMORY_TEST_CHANNEL", new FakeMemoryService());

  private FakeIdeProfilerComponents myFakeIdeProfilerComponents;
  private MemoryProfilerStage myStage;
  private MemoryProfilerStageView myStageView;

  @Before
  public void before() {
    FakeIdeProfilerServices profilerServices = new FakeIdeProfilerServices();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), profilerServices);

    myFakeIdeProfilerComponents = new FakeIdeProfilerComponents();
    StudioProfilersView profilersView = new StudioProfilersView(profilers, myFakeIdeProfilerComponents);

    myStage = spy(new MemoryProfilerStage(new StudioProfilers(myGrpcChannel.getClient(), profilerServices)));
    myStageView = new MemoryProfilerStageView(profilersView, myStage);
  }

  /**
   * Tests that the component generates the classes JTree model accurately based on the package hierarchy
   * of a HeapObject.
   */
  @Test
  public void buildClassesTreeTest() {
    MemoryClassView classView = new MemoryClassView(myStage, myFakeIdeProfilerComponents);

    // Setup fake package hierarchy
    ClassObject mockClass1 = mockClassObjectAutogeneratedInstances("com.android.studio.Foo", 1, 2, 3, 3);
    ClassObject mockClass2 = mockClassObject("int", 1, 2, 3, Collections.emptyList());
    ClassObject mockClass3 = mockClassObjectAutogeneratedInstances("com.google.Bar", 1, 2, 7, 1);
    ClassObject mockClass4 = mockClassObjectAutogeneratedInstances("com.android.studio.Foo2", 1, 2, 12, 4);
    ClassObject mockClass5 = mockClassObject("java.lang.Object", 1, 2, 3, Collections.emptyList());
    ClassObject mockClass6 = mockClassObject("long", 1, 2, 3, Collections.emptyList());
    List<ClassObject> fakeClassObjects = Arrays.asList(mockClass1, mockClass2, mockClass3, mockClass4, mockClass5, mockClass6);
    HeapObject mockHeap = mockHeapObject("Test", fakeClassObjects);
    myStage.selectHeap(mockHeap);

    assertEquals(myStage.getConfiguration().getClassGrouping(), ARRANGE_BY_CLASS);
    assertNotNull(classView.getTree());
    JTree classTree = classView.getTree();
    Object root = classTree.getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof NamespaceObject);
    //noinspection unchecked
    classTree.setSelectionPath(new TreePath(new Object[]{root,
      ((MemoryObjectTreeNode<NamespaceObject>)classTree.getModel().getRoot()).getChildren().get(0)}));
    // Note - com.android.studio.Foo2 would be sorted to the top since sorting is done based on retained size.
    assertEquals(mockClass4, ((MemoryObjectTreeNode<ClassObject>)classTree.getSelectionPath().getLastPathComponent()).getAdapter());

    // Check if group by package is grouping as expected.
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);
    //noinspection unchecked
    assertEquals(6, countClassObjects((MemoryObjectTreeNode<NamespaceObject>)root)); // 6 is the number of mockClass*
    TreePath selectionPath = classTree.getSelectionPath();
    assertNotNull(selectionPath);
    assertTrue(selectionPath.getLastPathComponent() instanceof MemoryObjectTreeNode);
    //noinspection unchecked
    assertEquals(mockClass4, ((MemoryObjectTreeNode<ClassObject>)classTree.getSelectionPath().getLastPathComponent()).getAdapter());
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof NamespaceObject);
    //noinspection unchecked
    ImmutableList<MemoryObjectTreeNode<NamespaceObject>> children = ((MemoryObjectTreeNode<NamespaceObject>)root).getChildren();

    assertEquals(1, children.stream().filter((child) -> child.getAdapter() == mockClass2).count());
    assertEquals(1, children.stream().filter((child) -> child.getAdapter() == mockClass6).count());

    MemoryObjectTreeNode<NamespaceObject> javaLangPackage =
      getSingularInList(children, (child) -> child.getAdapter().getName().equals("java.lang"));
    assertEquals(1, javaLangPackage.getChildCount());
    assertEquals(0, javaLangPackage.getAdapter().getTotalCount());
    assertEquals(0, javaLangPackage.getAdapter().getHeapCount());
    assertEquals(3, javaLangPackage.getAdapter().getRetainedSize());
    assertEquals(mockClass5, javaLangPackage.getChildren().get(0).getAdapter());

    MemoryObjectTreeNode<NamespaceObject> comPackage = getSingularInList(children, (child) -> child.getAdapter().getName().equals("com"));
    assertEquals(2, comPackage.getChildCount());
    assertEquals(8, comPackage.getAdapter().getTotalCount());
    assertEquals(8, comPackage.getAdapter().getHeapCount());
    assertEquals(22, comPackage.getAdapter().getRetainedSize());

    MemoryObjectTreeNode<NamespaceObject> googlePackage =
      getSingularInList(comPackage.getChildren(), (child) -> child.getAdapter().getName().equals("google"));
    assertEquals(1, googlePackage.getAdapter().getTotalCount());
    assertEquals(1, googlePackage.getAdapter().getHeapCount());
    assertEquals(1, googlePackage.getChildCount());
    assertEquals(7, googlePackage.getAdapter().getRetainedSize());
    assertEquals(mockClass3, googlePackage.getChildren().get(0).getAdapter());

    MemoryObjectTreeNode<NamespaceObject> androidPackage =
      getSingularInList(comPackage.getChildren(), (child) -> child.getAdapter().getName().equals("android.studio"));
    assertEquals(7, androidPackage.getAdapter().getTotalCount());
    assertEquals(7, androidPackage.getAdapter().getHeapCount());
    assertEquals(15, androidPackage.getAdapter().getRetainedSize());
    assertEquals(2, androidPackage.getChildCount());
    assertEquals(mockClass4, androidPackage.getChildren().get(0).getAdapter());
    assertEquals(mockClass1, androidPackage.getChildren().get(1).getAdapter());

    // Check if flat list is correct.
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_CLASS);
    root = classTree.getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof NamespaceObject);
    //noinspection unchecked
    children = ((MemoryObjectTreeNode<NamespaceObject>)root).getChildren();
    for (int i = 0; i < children.size(); i++) {
      ClassObject fake = fakeClassObjects.get(i);
      List<MemoryObjectTreeNode<NamespaceObject>> filteredList =
        children.stream().filter((child) -> child.getAdapter() == fake).collect(Collectors.toList());
      assertEquals(1, filteredList.size());
    }
  }

  /**
   * Tests selection on the class tree. This makes sure that selecting class nodes results in an actual selection being registered, while
   * selecting package nodes should do nothing.
   */
  @Test
  public void classTreeSelectionTest() {
    MemoryClassView classView = new MemoryClassView(myStage, myFakeIdeProfilerComponents);

    // Setup fake package hierarchy
    ClassObject fake1 = mockClassObject("int", 1, 2, 3, Collections.emptyList());
    ClassObject fake2 = mockClassObject("com.Foo", 1, 2, 3, Collections.emptyList());
    List<ClassObject> fakeClassObjects = Arrays.asList(fake1, fake2);
    HeapObject mockHeap = mockHeapObject("Test", fakeClassObjects);
    myStage.selectHeap(mockHeap);

    assertEquals(myStage.getConfiguration().getClassGrouping(), ARRANGE_BY_CLASS);
    assertNull(myStage.getSelectedClass());
    assertNotNull(classView.getTree());

    JTree classTree = classView.getTree();
    Object root = classTree.getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof NamespaceObject);

    //noinspection unchecked
    ImmutableList<MemoryObjectTreeNode<NamespaceObject>> children = ((MemoryObjectTreeNode<NamespaceObject>)root).getChildren();
    assertEquals(2, children.size());
    classTree.setSelectionPath(new TreePath(new Object[]{root, children.get(0)}));

    MemoryObjectTreeNode<NamespaceObject> selectedClassNode = children.get(0);
    assertEquals(fake1, selectedClassNode.getAdapter());
    assertEquals(fake1, myStage.getSelectedClass());
    assertEquals(selectedClassNode, classTree.getSelectionPath().getLastPathComponent());

    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);
    // Check that after changing to ARRANGE_BY_PACKAGE, the originally selected item is reselected.
    TreePath selectionPath = classTree.getSelectionPath();
    assertNotNull(selectionPath);
    Object reselected = selectionPath.getLastPathComponent();
    assertNotNull(reselected);
    assertTrue(reselected instanceof MemoryObjectTreeNode && ((MemoryObjectTreeNode)reselected).getAdapter() instanceof ClassObject);
    assertEquals(selectedClassNode.getAdapter(), ((MemoryObjectTreeNode)reselected).getAdapter());

    // Try selecting a package -- this should not result in any changes to the state.
    MemoryObjectTreeNode<NamespaceObject> comPackage = getSingularInList(children, (child) -> child.getAdapter().getName().equals("com"));
    ClassObject selectedClass = myStage.getSelectedClass();
    classTree.setSelectionPath(new TreePath(new Object[]{root, comPackage}));
    assertEquals(selectedClass, myStage.getSelectedClass());
  }

  @Test
  public void stackExistenceTest() {
    MemoryClassView classView = new MemoryClassView(myStage, myFakeIdeProfilerComponents);

    CodeLocation codeLocation1 = new CodeLocation.Builder("Foo").setMethodName("fooMethod1").setLineNumber(5).build();
    CodeLocation codeLocation2 = new CodeLocation.Builder("Foo").setMethodName("fooMethod2").setLineNumber(10).build();
    CodeLocation codeLocation3 = new CodeLocation.Builder("Foo").setMethodName("fooMethod3").setLineNumber(15).build();
    CodeLocation codeLocation4 = new CodeLocation.Builder("Bar").setMethodName("barMethod1").setLineNumber(20).build();

    //noinspection ConstantConditions
    AllocationStack callstack1 = AllocationStack.newBuilder()
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation2.getClassName())
          .setMethodName(codeLocation2.getMethodName())
          .setLineNumber(codeLocation2.getLineNumber() + 1))
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation1.getClassName())
          .setMethodName(codeLocation1.getMethodName())
          .setLineNumber(codeLocation1.getLineNumber() + 1))
      .build();
    //noinspection ConstantConditions
    AllocationStack callstack2 = AllocationStack.newBuilder()
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation3.getClassName())
          .setMethodName(codeLocation3.getMethodName())
          .setLineNumber(codeLocation3.getLineNumber() + 1))
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation1.getClassName())
          .setMethodName(codeLocation1.getMethodName())
          .setLineNumber(codeLocation1.getLineNumber() + 1))
      .build();
    //noinspection ConstantConditions
    AllocationStack callstack3 = AllocationStack.newBuilder()
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation4.getClassName())
          .setMethodName(codeLocation4.getMethodName())
          .setLineNumber(codeLocation4.getLineNumber() + 1))
      .build();

    List<InstanceObject> mockClass1Instances = new ArrayList<>();
    List<InstanceObject> mockClass2Instances = new ArrayList<>();
    List<InstanceObject> mockClass3Instances = new ArrayList<>();

    ClassObject mockClass1 = mockClassObject("com.android.studio.Foo", 8, 2, 104, mockClass1Instances);
    ClassObject mockClass2 = mockClassObject("int[]", 1, 2, 3, mockClass2Instances);
    ClassObject mockClass3 = mockClassObject("com.google.Bar", 8, 2, 16, mockClass3Instances);
    HeapObject mockHeap = mockHeapObject("app heap", Arrays.asList(mockClass1, mockClass2, mockClass3));

    InstanceObject mockInstance1 =
      mockInstanceObject("com.android.studio.Foo", "instance1", "toString: instance1", callstack1, mockClass1, 2, 2, 2, 16);
    InstanceObject mockInstance2 =
      mockInstanceObject("com.android.studio.Foo", "instance2", "toString: instance2", callstack1, mockClass1, 2, 2, 2, 24);
    InstanceObject mockInstance3 =
      mockInstanceObject("com.android.studio.Foo", "instance3", "toString: instance3", callstack1, mockClass1, 2, 2, 2, 16);
    InstanceObject mockInstance4 =
      mockInstanceObject("com.android.studio.Foo", "instance4", "toString: instance4", callstack2, mockClass1, 2, 2, 2, 16);
    InstanceObject mockInstance5 =
      mockInstanceObject("com.android.studio.Foo", "instance5", "toString: instance5", callstack2, mockClass1, 2, 2, 2, 16);
    InstanceObject mockInstance6 =
      mockInstanceObject("com.android.studio.Foo", "instance6", "toString: instance6", callstack3, mockClass1, 2, 2, 2, 16);
    InstanceObject mockInstance7 =
      mockInstanceObject("com.google.Bar", "instance7", "toString: instance7", callstack3, mockClass3, 2, 2, 2, 16);
    InstanceObject mockInstance8 =
      mockInstanceObject("int[]", "int instance", "toString: int instance", null, mockClass2, 0, 2, 8, 8);

    mockClass1Instances.addAll(Arrays.asList(mockInstance1, mockInstance2, mockInstance3, mockInstance4, mockInstance5, mockInstance6));
    mockClass2Instances.add(mockInstance8);
    mockClass3Instances.add(mockInstance7);

    myStage.selectHeap(mockHeap);

    assertEquals(myStage.getConfiguration().getClassGrouping(), ARRANGE_BY_CLASS);
    assertNull(myStage.getSelectedClass());

    JTree classTree = classView.getTree();
    assertNotNull(classTree);
    Object rootObject = classTree.getModel().getRoot();
    assertTrue(rootObject instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)rootObject).getAdapter() instanceof NamespaceObject);
    //noinspection unchecked
    MemoryObjectTreeNode<NamespaceObject> root = (MemoryObjectTreeNode<NamespaceObject>)rootObject;
    assertEquals(3, root.getChildCount());

    MemoryObjectTreeNode<NamespaceObject> class1Node = getSingularInList(root.getChildren(), child -> child.getAdapter() == mockClass1);
    assertTrue(class1Node.getAdapter().hasStackInfo());
    MemoryObjectTreeNode<NamespaceObject> class2Node = getSingularInList(root.getChildren(), child -> child.getAdapter() == mockClass2);
    assertFalse(class2Node.getAdapter().hasStackInfo());
    MemoryObjectTreeNode<NamespaceObject> class3Node = getSingularInList(root.getChildren(), child -> child.getAdapter() == mockClass3);
    assertTrue(class3Node.getAdapter().hasStackInfo());

    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);
    assertEquals(2, root.getChildCount());
    class2Node = getSingularInList(root.getChildren(), child -> child.getAdapter() == mockClass2);
    assertFalse(class2Node.getAdapter().hasStackInfo());
    MemoryObjectTreeNode<NamespaceObject> comNode = getSingularInList(root.getChildren(),
                                                                      child -> (child.getAdapter() instanceof PackageObject) &&
                                                                               child.getAdapter().getName().equals("com"));
    assertTrue(comNode.getAdapter().hasStackInfo());
  }

  @Test
  public void groupByStackTraceTest() {
    MemoryClassView classView = new MemoryClassView(myStage, myFakeIdeProfilerComponents);

    CodeLocation codeLocation1 = new CodeLocation.Builder("Foo").setMethodName("fooMethod1").setLineNumber(5).build();
    CodeLocation codeLocation2 = new CodeLocation.Builder("Foo").setMethodName("fooMethod2").setLineNumber(10).build();
    CodeLocation codeLocation3 = new CodeLocation.Builder("Foo").setMethodName("fooMethod3").setLineNumber(15).build();
    CodeLocation codeLocation4 = new CodeLocation.Builder("Bar").setMethodName("barMethod1").setLineNumber(20).build();

    //noinspection ConstantConditions
    AllocationStack callstack1 = AllocationStack.newBuilder()
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation2.getClassName())
          .setMethodName(codeLocation2.getMethodName())
          .setLineNumber(codeLocation2.getLineNumber() + 1))
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation1.getClassName())
          .setMethodName(codeLocation1.getMethodName())
          .setLineNumber(codeLocation1.getLineNumber() + 1))
      .build();
    //noinspection ConstantConditions
    AllocationStack callstack2 = AllocationStack.newBuilder()
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation3.getClassName())
          .setMethodName(codeLocation3.getMethodName())
          .setLineNumber(codeLocation3.getLineNumber() + 1))
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation1.getClassName())
          .setMethodName(codeLocation1.getMethodName())
          .setLineNumber(codeLocation1.getLineNumber() + 1))
      .build();
    //noinspection ConstantConditions
    AllocationStack callstack3 = AllocationStack.newBuilder()
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation4.getClassName())
          .setMethodName(codeLocation4.getMethodName())
          .setLineNumber(codeLocation4.getLineNumber() + 1))
      .build();

    List<InstanceObject> mockClass1List = new ArrayList<>();
    List<InstanceObject> mockClass2List = new ArrayList<>();
    List<InstanceObject> mockClass3List = new ArrayList<>();

    ClassObject mockClass1 = mockClassObject("com.android.studio.Foo", 8, 2, 104, mockClass1List);
    ClassObject mockClass2 = mockClassObject("int[]", 1, 2, 3, mockClass2List);
    ClassObject mockClass3 = mockClassObject("com.google.Bar", 8, 2, 16, mockClass3List);
    HeapObject mockHeap = mockHeapObject("app heap", Arrays.asList(mockClass1, mockClass2, mockClass3));

    InstanceObject mockInstance1 =
      mockInstanceObject("com.android.studio.Foo", "instance1", "toString: instance1", callstack1, mockClass1, 2, 2, 2, 16);
    InstanceObject mockInstance2 =
      mockInstanceObject("com.android.studio.Foo", "instance2", "toString: instance2", callstack1, mockClass1, 2, 2, 2, 24);
    InstanceObject mockInstance3 =
      mockInstanceObject("com.android.studio.Foo", "instance3", "toString: instance3", callstack1, mockClass1, 2, 2, 2, 16);
    InstanceObject mockInstance4 =
      mockInstanceObject("com.android.studio.Foo", "instance4", "toString: instance4", callstack2, mockClass1, 2, 2, 2, 16);
    InstanceObject mockInstance5 =
      mockInstanceObject("com.android.studio.Foo", "instance5", "toString: instance5", callstack2, mockClass1, 2, 2, 2, 16);
    InstanceObject mockInstance6 =
      mockInstanceObject("com.android.studio.Foo", "instance6", "toString: instance6", callstack3, mockClass1, 2, 2, 2, 16);
    InstanceObject mockInstance7 =
      mockInstanceObject("com.android.studio.Foo", "instance7", "toString: instance7", null, mockClass1, 2, 2, 2, 16);
    InstanceObject mockInstance8 =
      mockInstanceObject("com.google.Bar", "Bar instance", "toString: Bar instance", callstack3, mockClass3, 2, 2, 2, 16);
    InstanceObject mockInstance9 =
      mockInstanceObject("int[]", "int instance", "toString: int instance", null, mockClass2, 0, 2, 8, 8);

    mockClass1List
      .addAll(Arrays.asList(mockInstance1, mockInstance2, mockInstance3, mockInstance4, mockInstance5, mockInstance6, mockInstance7));
    mockClass2List.add(mockInstance9);
    mockClass3List.add(mockInstance8);

    myStage.selectHeap(mockHeap);

    assertEquals(myStage.getConfiguration().getClassGrouping(), ARRANGE_BY_CLASS);
    assertNull(myStage.getSelectedClass());

    JTree classTree = classView.getTree();
    assertNotNull(classTree);
    Object rootObject = classTree.getModel().getRoot();
    assertTrue(rootObject instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)rootObject).getAdapter() instanceof NamespaceObject);
    //noinspection unchecked
    MemoryObjectTreeNode<NamespaceObject> root = (MemoryObjectTreeNode<NamespaceObject>)rootObject;
    assertEquals(3, root.getChildCount());

    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_CALLSTACK);
    assertEquals(root, classTree.getModel().getRoot());
    assertEquals(4, root.getChildCount());

    MemoryObjectTreeNode<NamespaceObject> codeLocation1Node =
      getSingularInList(root.getChildren(), child -> (child.getAdapter() instanceof MethodObject) &&
                                                     ((MethodObject)child.getAdapter()).getCodeLocation().equals(codeLocation1));
    assertEquals(2, codeLocation1Node.getChildCount());

    MemoryObjectTreeNode<NamespaceObject> codeLocation2Node =
      getSingularInList(codeLocation1Node.getChildren(),
                        child -> (child.getAdapter() instanceof MethodObject) &&
                                 ((MethodObject)child.getAdapter()).getCodeLocation().equals(codeLocation2));
    assertEquals(1, codeLocation2Node.getChildCount());
    assertTrue(codeLocation2Node.getChildren().get(0).getAdapter() instanceof ProxyClassObject);
    ProxyClassObject proxyClassObject = (ProxyClassObject)codeLocation2Node.getChildren().get(0).getAdapter();
    assertTrue(proxyClassObject.getInstances().contains(mockInstance1));
    assertTrue(proxyClassObject.getInstances().contains(mockInstance2));
    assertTrue(proxyClassObject.getInstances().contains(mockInstance3));

    MemoryObjectTreeNode<NamespaceObject> codeLocation3Node =
      getSingularInList(codeLocation1Node.getChildren(),
                        child -> (child.getAdapter() instanceof MethodObject) &&
                                 ((MethodObject)child.getAdapter()).getCodeLocation().equals(codeLocation3));
    assertEquals(1, codeLocation3Node.getChildCount());
    assertTrue(codeLocation3Node.getChildren().get(0).getAdapter() instanceof ProxyClassObject);
    proxyClassObject = (ProxyClassObject)codeLocation3Node.getChildren().get(0).getAdapter();
    assertTrue(proxyClassObject.getInstances().contains(mockInstance4));
    assertTrue(proxyClassObject.getInstances().contains(mockInstance5));

    MemoryObjectTreeNode<NamespaceObject> codeLocation4Node =
      getSingularInList(root.getChildren(), child -> (child.getAdapter() instanceof MethodObject) &&
                                                     ((MethodObject)child.getAdapter()).getCodeLocation().equals(codeLocation4));
    assertEquals(2, codeLocation4Node.getChildCount());

    MemoryObjectTreeNode<NamespaceObject> barNode =
      getSingularInList(codeLocation4Node.getChildren(),
                        child -> (child.getAdapter() instanceof ProxyClassObject) &&
                                 child.getAdapter().isInNamespace(new ProxyClassObject(mockClass3, mockInstance8)));
    classTree.setSelectionPath(new TreePath(new Object[]{rootObject, codeLocation4Node, barNode}));

    MemoryObjectTreeNode<NamespaceObject> noStackFoo =
      getSingularInList(root.getChildren(),
                        child -> (child.getAdapter() instanceof ProxyClassObject) &&
                                 child.getAdapter().isInNamespace(new ProxyClassObject(mockClass2, mockInstance7)));
    assertEquals(1, noStackFoo.getAdapter().getTotalCount());

    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_CLASS);
    assertEquals(3, root.getChildCount());
    TreePath selectionPath = classTree.getSelectionPath();
    assertNotNull(selectionPath);
    Object previouslySelectedObject = selectionPath.getLastPathComponent();
    assertTrue(previouslySelectedObject instanceof MemoryObjectTreeNode &&
               ((MemoryObjectTreeNode)previouslySelectedObject).getAdapter() instanceof ClassObject);
    assertEquals(mockClass3, ((MemoryObjectTreeNode)previouslySelectedObject).getAdapter());
  }

  @Test
  public void classTreeNodeComparatorTest() {
    MemoryObjectTreeNode<NamespaceObject> package1 = new MemoryObjectTreeNode<>(mockPackageObject("bar"));
    MemoryObjectTreeNode<NamespaceObject> package2 = new MemoryObjectTreeNode<>(mockPackageObject("foo"));
    MemoryObjectTreeNode<ClassObject> class1 =
      new MemoryObjectTreeNode<>(mockClassObject("abar", 1, 2, 3, Collections.emptyList()));
    MemoryObjectTreeNode<ClassObject> class2 =
      new MemoryObjectTreeNode<>(mockClassObject("zoo", 4, 5, 6, Collections.emptyList()));

    Comparator<MemoryObjectTreeNode> comparator =
      MemoryClassView.createTreeNodeComparator(Comparator.comparing(ClassObject::getClassName));
    assertTrue(comparator.compare(package1, package2) < 0);
    assertTrue(comparator.compare(class1, class2) < 0);
    assertTrue(comparator.compare(package1, class1) < 0);
    assertTrue(comparator.compare(class1, package1) > 0);
  }

  @Test
  public void navigationTest() {
    final String testClassName = "com.Foo";
    InstanceObject mockInstance = mockInstanceObject(testClassName, "instance", null, null, null, 0, 0, 0, 0);
    ClassObject mockClass = mockClassObject(testClassName, 1, 2, 3, Collections.singletonList(mockInstance));
    HeapObject mockHeap = mockHeapObject("Test", Collections.singletonList(mockClass));
    myStage.selectHeap(mockHeap);
    myStage.selectClass(mockClass);
    myStage.selectInstance(mockInstance);

    JTree classTree = myStageView.getClassView().getTree();
    assertNotNull(classTree);
    Supplier<CodeLocation> codeLocationSupplier = myFakeIdeProfilerComponents.getCodeLocationSupplier(classTree);

    assertNotNull(codeLocationSupplier);
    CodeLocation codeLocation = codeLocationSupplier.get();
    assertNotNull(codeLocation);
    String codeLocationClassName = codeLocation.getClassName();
    assertEquals(testClassName, codeLocationClassName);

    myStage.getStudioProfilers().getIdeServices().getCodeNavigator().navigate(codeLocation);
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
  }

  @Test
  public void testCorrectColumnsAndRendererContents() {
    ClassObject mockClass1 = mockClassObject("bar.def", 7, 8, 9, Collections.emptyList());
    ClassObject mockClass2 = mockClassObject("foo.abc", 4, 5, 6, Collections.emptyList());
    ClassObject mockClass3 = mockClassObject("ghi", 1, 2, 3, Collections.emptyList());
    List<ClassObject> fakeClassObjects = Arrays.asList(mockClass1, mockClass2, mockClass3);
    HeapObject mockHeap = mockHeapObject("Test", fakeClassObjects);

    MemoryClassView view = new MemoryClassView(myStage, myFakeIdeProfilerComponents);
    myStage.selectHeap(mockHeap);

    JTree tree = view.getTree();
    assertNotNull(tree);
    JScrollPane columnTreePane = (JScrollPane)view.getColumnTree();
    assertNotNull(columnTreePane);
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(tree, columnTreePane);
    treeInfo.verifyColumnHeaders("Class Name", "Total Count", "Heap Count", "Sizeof", "Shallow Size", "Retained Size");

    MemoryObjectTreeNode root = (MemoryObjectTreeNode)tree.getModel().getRoot();
    assertEquals(fakeClassObjects.size(), root.getChildCount());
    for (int i = 0; i < root.getChildCount(); i++) {
      ClassObject klass = fakeClassObjects.get(i);
      treeInfo.verifyRendererValues(root.getChildAt(i),
                                    new String[]{klass.getClassName(),
                                      klass.getPackageName().isEmpty() ? null : " (" + klass.getPackageName() + ")"},
                                    new String[]{Integer.toString(klass.getTotalCount())},
                                    new String[]{Integer.toString(klass.getHeapCount())},
                                    new String[]{Integer.toString(klass.getInstanceSize())},
                                    new String[]{Integer.toString(klass.getShallowSize())},
                                    new String[]{Long.toString(klass.getRetainedSize())});
    }
  }

  private static <T> T getSingularInList(@NotNull List<T> list, @NotNull Predicate<T> predicate) {
    List<T> reducedList = list.stream().filter(predicate).collect(Collectors.toList());
    assertEquals(1, reducedList.size());
    return reducedList.get(0);
  }

  private static int countClassObjects(@NotNull MemoryObjectTreeNode<NamespaceObject> node) {
    int classObjectCount = 0;
    for (MemoryObjectTreeNode<NamespaceObject> child : node.getChildren()) {
      if (child.getAdapter() instanceof ClassObject) {
        classObjectCount++;
      }
      else {
        classObjectCount += countClassObjects(child);
      }
    }
    return classObjectCount;
  }
}
