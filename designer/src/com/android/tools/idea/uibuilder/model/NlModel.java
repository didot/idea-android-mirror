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
package com.android.tools.idea.uibuilder.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.MergeCookie;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.rendering.ResourceNotificationManager.ResourceChangeListener;
import com.android.tools.idea.rendering.ResourceNotificationManager.ResourceVersion;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.android.designer.model.layout.actions.ToggleRenderModeAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Alarm;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Model for an XML file
 */
public class NlModel implements Disposable, ResourceChangeListener, ModificationTracker {
  @AndroidCoordinate public static final int EMPTY_COMPONENT_SIZE = 5;
  @AndroidCoordinate public static final int VISUAL_EMPTY_COMPONENT_SIZE = 14;

  private final AndroidFacet myFacet;
  private final XmlFile myFile;
  private RenderResult myRenderResult;
  private Configuration myConfiguration;
  private final List<ChangeListener> myListeners = Lists.newArrayList();
  private List<NlComponent> myComponents = Lists.newArrayList();
  private final SelectionModel mySelectionModel;
  private Disposable myParent;
  private boolean myActive;
  private ResourceVersion myRenderedVersion;
  private long myModificationCount;

  @NonNull
  public static NlModel create(@Nullable Disposable parent, @NonNull AndroidFacet facet, @NonNull XmlFile file) {
    return new NlModel(parent, facet, file);
  }

  @VisibleForTesting
  protected NlModel(@Nullable Disposable parent, @NonNull AndroidFacet facet, @NonNull XmlFile file) {
    myParent = parent;
    myFacet = facet;
    myFile = file;
    myConfiguration = facet.getConfigurationManager().getConfiguration(myFile.getVirtualFile());
    mySelectionModel = new SelectionModel();
  }

  public void setParentDisposable(Disposable parent) {
    synchronized (myRenderingQueueLock) {
      myParent = parent;
    }
  }

  /** Notify model that it's active. A model is active by default. */
  public void activate() {
    if (!myActive) {
      myActive = true;

      ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myFile.getProject());
      ResourceVersion version = manager.addListener(this, myFacet, myFile, myConfiguration);
      if (!version.equals(myRenderedVersion)) {
        requestRender();
      }
    }
  }

  /** Notify model that it's not active. This means it can stop watching for events etc. It may be activated again in the future. */
  public void deactivate() {
    if (myActive) {
      ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myFile.getProject());
      manager.removeListener(this, myFacet, myFile, myConfiguration);
      myActive = false;
    }
  }

  public XmlFile getFile() {
    return myFile;
  }

  @NonNull
  public SelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  public boolean requestRender() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    getRenderingQueue().queue(new Update("render") {
      @Override
      public void run() {
        DumbService.getInstance(myFacet.getModule().getProject()).waitForSmartMode();
        try {
          doRender();
        }
        catch (Throwable e) {
          Logger.getInstance(NlModel.class).error(e);
        }
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
    return true;
  }

  @NotNull
  private MergingUpdateQueue getRenderingQueue() {
    synchronized (myRenderingQueueLock) {
      if (myRenderingQueue == null) {
        myRenderingQueue = new MergingUpdateQueue("android.layout.rendering", 10, true, null, myParent, null,
                                                  Alarm.ThreadToUse.OWN_THREAD);
      }
      return myRenderingQueue;
    }
  }

  private final Object myRenderingQueueLock = new Object();
  private MergingUpdateQueue myRenderingQueue;
  private static final Object RENDERING_LOCK = new Object();


  private void doRender() {
    if (myFacet.getModule().getProject().isDisposed()) {
      return;
    }

    Configuration configuration = myConfiguration;
    if (configuration == null) {
      return;
    }

    // Record the current version we're rendering from; we'll use that in #activate to make sure we're picking up any
    // external changes
    ResourceNotificationManager resourceNotificationManager = ResourceNotificationManager.getInstance(myFile.getProject());
    myRenderedVersion = resourceNotificationManager.getCurrentVersion(myFacet, myFile, myConfiguration);

    // Some types of files must be saved to disk first, because layoutlib doesn't
    // delegate XML parsers for non-layout files (meaning layoutlib will read the
    // disk contents, so we have to push any edits to disk before rendering)
    LayoutPullParserFactory.saveFileIfNecessary(myFile);

    RenderResult result = null;
    synchronized (RENDERING_LOCK) {
      RenderService renderService = RenderService.get(myFacet);
      RenderLogger logger = renderService.createLogger();
      final RenderTask task = renderService.createTask(myFile, configuration, logger, null);
      if (task != null) {
        if (!ToggleRenderModeAction.isRenderViewPort()) {
          task.useDesignMode(myFile);
        }
        result = task.render();
        task.dispose();
      }
      if (result == null) {
        result = RenderResult.createBlank(myFile, logger);
      }
    }

    if (!getRenderingQueue().isEmpty()) {
      return;
    }

    myRenderResult = result;
    updateHierarchy(result);
    notifyListeners();
  }

  public void addListener(@NonNull ChangeListener listener) {
    synchronized (myListeners) {
      myListeners.remove(listener); // prevent duplicate registration
      myListeners.add(listener);
    }
  }

  public void removeListener(@NonNull ChangeListener listener) {
    synchronized (myListeners) {
      myListeners.remove(listener);
    }
  }

  private void notifyListeners() {
    synchronized (myListeners) {
      for (ChangeListener listener : myListeners) {
        listener.stateChanged(new ChangeEvent(this));
      }
    }
  }

  @Nullable
  public RenderResult getRenderResult() {
    return myRenderResult;
  }

  @NonNull
  public AndroidFacet getFacet() {
    return myFacet;
  }

  @NonNull
  public Module getModule() {
    return myFacet.getModule();
  }

  @NonNull
  public Project getProject() {
    return getModule().getProject();
  }

  @NonNull
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  @NonNull
  public List<NlComponent> getComponents() {
    return myComponents;
  }

  private final Map<XmlTag,NlComponent> myTagToComponentMap = Maps.newIdentityHashMap();
  private final Map<XmlTag,NlComponent> myMergeComponentMap = Maps.newHashMap();

  private void updateHierarchy(@Nullable RenderResult result) {
    if (result == null || result.getSession() == null || !result.getSession().getResult().isSuccess()) {
      myComponents.clear();
      return;
    }
    updateHierarchy(result.getSession().getRootViews());
  }

  @VisibleForTesting
  public void updateHierarchy(@Nullable List<ViewInfo> rootViews) {
    for (NlComponent component : myComponents) {
      initTagMap(component);
      component.children = null;
    }

    final List<NlComponent> newRoots = Lists.newArrayList();
    if (rootViews != null) {
      for (ViewInfo info : rootViews) {
        NlComponent newRoot = updateHierarchy(null, info, 0, 0);
        if (newRoot != null) {
          newRoots.add(newRoot);
        }
      }
    }

    // TODO: Use result from rendering instead, if available!
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        for (NlComponent root : newRoots) {
          TagSnapshot snapshot = TagSnapshot.createTagSnapshot(root.getTag());
          updateSnapshot(root, snapshot);
        }
      }
    });

    myModificationCount++;
    myComponents = newRoots;
  }

  private static void updateSnapshot(@NonNull NlComponent component, @NonNull TagSnapshot snapshot) {
    assert component.getTag() == snapshot.tag;
    component.setSnapshot(snapshot);
    if (!snapshot.children.isEmpty()) {
      assert snapshot.children.size() == component.getChildCount();
      for (int i = 0, n = component.getChildCount(); i < n; i++) {
        NlComponent child = component.getChild(i);
        assert child != null;
        updateSnapshot(child, snapshot.children.get(i));
      }
    } else {
      assert component.getChildCount() == 0;
    }
  }

  protected void initTagMap(@NonNull NlComponent root) {
    myTagToComponentMap.clear();
    for (NlComponent component : root.getChildren()) {
      gatherTags(myTagToComponentMap, component);
    }
  }

  private static void gatherTags(Map<XmlTag, NlComponent> map, NlComponent component) {
    XmlTag tag = component.getTag();
    map.put(tag, component);

    for (NlComponent child : component.getChildren()) {
      gatherTags(map, child);
    }
  }

  @Nullable
  private NlComponent updateHierarchy(@Nullable NlComponent parent, ViewInfo view,
                                      @AndroidCoordinate int parentX, @AndroidCoordinate int parentY) {
    Object cookie = view.getCookie();
    NlComponent component = null;

    XmlTag tag = null;
    boolean isMerge = false;
    if (cookie instanceof XmlTag) {
      tag = (XmlTag)cookie;
    } else if (cookie instanceof MergeCookie) {
      isMerge = true;
      cookie = ((MergeCookie)cookie).getCookie();
      if (cookie instanceof XmlTag) {
        tag = (XmlTag)cookie;
        NlComponent mergedComponent = myMergeComponentMap.get(tag);
        if (mergedComponent == null && parent != null && tag == parent.getTag()) {
          // NumberPicker will render its children with merge cookies pointing to the root
          // component (which was not a <merge>)
          mergedComponent = parent;
        }
        if (mergedComponent != null) {
          // Just expand the bounds
          int left = parentX + view.getLeft();
          int top = parentY + view.getTop();
          int width = view.getRight() - view.getLeft();
          int height = view.getBottom() - view.getTop();
          Rectangle rectanglePrev = new Rectangle(mergedComponent.x, mergedComponent.y,
                                                  mergedComponent.w, mergedComponent.h);
          Rectangle rectangle = new Rectangle(left, top, width, height);
          rectangle.add(rectanglePrev);
          mergedComponent.setBounds(rectanglePrev.x, rectanglePrev.y, rectanglePrev.width, rectanglePrev.height);
          return null;
        }
      }
    }
    if (tag != null) {
      component = myTagToComponentMap.get(tag);
      if (component != null) {
        // TODO: Clear out component is the tag is not valid
        //if (!tag.isValid()) {
        //  component = null;
        //}
      }
      if (component == null) {
        component = new NlComponent(this, tag);
      } else {
        component.children = null;
        myTagToComponentMap.remove(tag);
      }

      component.viewInfo = view;

      int left = parentX + view.getLeft();
      int top = parentY + view.getTop();
      int width = view.getRight() - view.getLeft();
      int height = view.getBottom() - view.getTop();

      component.setBounds(left, top, Math.max(width, VISUAL_EMPTY_COMPONENT_SIZE), Math.max(height, VISUAL_EMPTY_COMPONENT_SIZE));

      if (parent != null && parent != component) {
        parent.addChild(component);
        if (isMerge) {
          myMergeComponentMap.put(tag, component);
        }
      }
    }

    if (component != null) {
      parent = component;
    }

    parentX += view.getLeft();
    parentY += view.getTop();

    for (ViewInfo child : view.getChildren()) {
      updateHierarchy(parent, child, parentX, parentY);
    }

    return component;
  }

  @Nullable
  public List<NlComponent> findByOffset(int offset) {
    XmlTag tag = PsiTreeUtil.findElementOfClassAtOffset(myFile, offset, XmlTag.class, false);
    return (tag != null) ? findViewsByTag(tag) : null;
  }

  /**
   * Looks up the point at the given pixel coordinates in the Android screen coordinate system, and
   * finds the leaf component there and returns it, if any. If the point is outside the screen bounds,
   * it will either return null, or the root view if {@code useRootOutsideBounds} is set and there is
   * precisely one parent.
   *
   * @param x                    the x pixel coordinate
   * @param y                    the y pixel coordinate
   * @param useRootOutsideBounds if true, return the root component when pointing outside the screen, otherwise null
   * @return the leaf component at the coordinate
   */
  @Nullable
  public NlComponent findLeafAt(@AndroidCoordinate int x, @AndroidCoordinate int y, boolean useRootOutsideBounds) {
    // Search BACKWARDS such that if the children are painted on top of each
    // other (as is the case in a FrameLayout) I pick the last one which will
    // be topmost!
    for (int i = myComponents.size() - 1; i >= 0; i--) {
      NlComponent component = myComponents.get(i);
      NlComponent leaf = component.findLeafAt(x, y);
      if (leaf != null) {
        return leaf;
      }
    }

    if (useRootOutsideBounds) {
      // If dragging outside of the screen, associate it with the
      // root widget (if there is one, and at most one (e.g. not a <merge> tag)
      List<NlComponent> components = myComponents;
      if (components.size() == 1) {
        return components.get(0);
      } else {
        return null;
      }
    }

    return null;
  }

  @Nullable
  public NlComponent findViewByTag(@NonNull XmlTag tag) {
    // TODO: Consider using lookup map
    for (NlComponent component : myComponents) {
      NlComponent match = component.findViewByTag(tag);
      if (match != null) {
        return match;
      }
    }

    return null;
  }

  @Nullable
  public List<NlComponent> findViewsByTag(@NonNull XmlTag tag) {
    List<NlComponent> result = null;
    for (NlComponent view : myComponents) {
      List<NlComponent> matches = view.findViewsByTag(tag);
      if (matches != null) {
        if (result != null) {
          result.addAll(matches);
        } else {
          result = matches;
        }
      }
    }

    return result;
  }

  /**
   * Finds any components that overlap the given rectangle.
   *
   * @param x      The top left x corner defining the selection rectangle.
   * @param y      The top left y corner defining the selection rectangle.
   * @param width  The w of the selection rectangle
   * @param height The h of the selection rectangle
   */
  public List<NlComponent> findWithin(@AndroidCoordinate int x,
                                      @AndroidCoordinate int y,
                                      @AndroidCoordinate int width,
                                      @AndroidCoordinate int height) {
    List<NlComponent> within = Lists.newArrayList();
    for (NlComponent component : myComponents) {
      addWithin(within, component, x, y, width, height);
    }
    return within;
  }

  private static boolean addWithin(@NonNull List<NlComponent> result,
                                   @NonNull NlComponent component,
                                   @AndroidCoordinate int x,
                                   @AndroidCoordinate int y,
                                   @AndroidCoordinate int width,
                                   @AndroidCoordinate int height) {
    if (component.x + component.w <= x ||
        x + width <= component.x ||
        component.y + component.h <= y ||
        y + height <= component.y) {
      return false;
    }

    boolean found = false;
    for (NlComponent child : component.getChildren()) {
      found |= addWithin(result, child, x, y, width, height);
    }
    if (!found) {
      result.add(component);
    }
    return true;
  }

  public void delete() {
  }


  public void delete(final Collection<NlComponent> components) {
    // Group by parent and ask each one to participate
    WriteCommandAction<Void> action = new WriteCommandAction<Void>(myFacet.getModule().getProject(), "Delete Component", myFile) {
      @Override
      protected void run(@NotNull Result<Void> result) throws Throwable {
        handleDeletion(components);
      }
    };
    action.execute();
  }

  private void handleDeletion(@NotNull Collection<NlComponent> components) throws Exception {
    // Segment the deleted components into lists of siblings
    Map<NlComponent, List<NlComponent>> siblingLists = groupSiblings(components);

    ViewHandlerManager viewHandlerManager = ViewHandlerManager.get(myFacet);


    // Notify parent components about children getting deleted
    for (Map.Entry<NlComponent, List<NlComponent>> entry : siblingLists.entrySet()) {
      NlComponent parent = entry.getKey();
      if (parent == null) {
        continue;
      }
      List<NlComponent> children = entry.getValue();
      boolean finished = false;

      ViewHandler handler = viewHandlerManager.getHandler(parent);
      if (handler instanceof ViewGroupHandler) {
        finished = ((ViewGroupHandler)handler).deleteChildren(parent, children);
      }

      if (!finished) {
        for (NlComponent component : children) {
          NlComponent p = component.getParent();
          if (p != null) {
            p.removeChild(component);
          }
          component.getTag().delete();
        }
      }
    }
  }

  /**
   * Partitions the given list of components into a map where each value is a list of siblings,
   * in the same order as in the original list, and where the keys are the parents (or null
   * for the components that do not have a parent).
   * <p/>
   * The value lists will never be empty. The parent key will be null for components without parents.
   *
   * @param components the components to be grouped
   * @return a map from parents (or null) to a list of components with the corresponding parent
   */
  @NotNull
  public static Map<NlComponent, List<NlComponent>> groupSiblings(@NotNull Collection<? extends NlComponent> components) {
    Map<NlComponent, List<NlComponent>> siblingLists = new HashMap<NlComponent, List<NlComponent>>();

    if (components.isEmpty()) {
      return siblingLists;
    }
    if (components.size() == 1) {
      NlComponent component = components.iterator().next();
      siblingLists.put(component.getParent(), Collections.singletonList(component));
      return siblingLists;
    }

    for (NlComponent component : components) {
      NlComponent parent = component.getParent();
      List<NlComponent> children = siblingLists.get(parent);
      if (children == null) {
        children = new ArrayList<NlComponent>();
        siblingLists.put(parent, children);
      }
      children.add(component);
    }

    return siblingLists;
  }

  /**
   * Creates a new component of the given type. It will optionally insert it as a child of the given parent (and optionally
   * right before the given sibling or null to append at the end.)
   * <p/>
   * Note: This operation can only be called when the caller is already holding a write lock. This will be the
   * case from {@link ViewHandler} callbacks such as {@link ViewHandler#onCreate(ViewEditor, NlComponent, NlComponent, InsertType)}
   * and {@link com.android.tools.idea.uibuilder.api.DragHandler#commit(int, int, int)}.
   *
   * @param screenView The target screen, if known. Used to handle pixel to dp computations in view handlers, etc.
   * @param fqcn       The fully qualified name of the widget to insert, such as {@code android.widget.LinearLayout}.
   *                   You can also pass XML tags here (this is typically the same as the fully qualified class name
   *                   of the custom view, but for Android framework views in the android.view or android.widget packages,
   *                   you can omit the package.)
   * @param parent     The optional parent to add this component to
   * @param before     The sibling to insert immediately before, or null to append
   * @param insertType The type of insertion
   */
  public NlComponent createComponent(@Nullable ScreenView screenView,
                                     @NonNull String fqcn,
                                     @Nullable NlComponent parent,
                                     @Nullable NlComponent before,
                                     @NonNull InsertType insertType) {
    String tagName =  NlComponent.viewClassToTag(fqcn);

    XmlTag tag;
    if (parent != null) {
      // Creating a component intended to be inserted into an existing layout
      tag = parent.getTag().createChildTag(tagName, null, null, false);
    } else {
      // Creating a component not yet inserted into a layout. Typically done when trying to perform
      // a drag from palette, etc.
      XmlElementFactory elementFactory = XmlElementFactory.getInstance(getProject());
      String text = "<" + fqcn + " xmlns:android=\"http://schemas.android.com/apk/res/android\"/>"; // SIZES?
      tag = elementFactory.createTagFromText(text);
    }

    return createComponent(screenView, tag, parent, before, insertType);
  }

  public NlComponent createComponent(@Nullable ScreenView screenView,
                                     @NonNull XmlTag tag,
                                     @Nullable NlComponent parent,
                                     @Nullable NlComponent before,
                                     @NonNull InsertType insertType) {
    if (parent != null) {
      // Creating a component intended to be inserted into an existing layout
      XmlTag parentTag = parent.getTag();
      if (before != null) {
        tag = (XmlTag) parentTag.addBefore(tag, before.getTag());
      } else {
        tag = parentTag.addSubTag(tag, false);
      }

      // Required for all views; drop handlers can adjust as necessary
      tag.setAttribute(ATTR_LAYOUT_WIDTH, ANDROID_URI, VALUE_WRAP_CONTENT);
      tag.setAttribute(ATTR_LAYOUT_HEIGHT, ANDROID_URI, VALUE_WRAP_CONTENT);
    } else {
      // No namespace yet: use the default prefix instead
      tag.setAttribute(ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
      tag.setAttribute(ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
    }

    NlComponent child = new NlComponent(this, tag);

    if (parent != null) {
      parent.addChild(child, before);
    }

    // Notify view handlers
    ViewHandlerManager viewHandlerManager = ViewHandlerManager.get(getProject());
    ViewHandler childHandler = viewHandlerManager.getHandler(child);
    if (childHandler != null && screenView != null) {
      ViewEditor editor = new ViewEditorImpl(screenView);
      boolean ok = childHandler.onCreate(editor, parent, child, insertType);
      if (!ok) {
        if (parent != null) {
          parent.removeChild(child);
        }
        tag.delete();
        return null;
      }
    }
    if (parent != null) {
      ViewHandler parentHandler = viewHandlerManager.getHandler(parent);
      if (parentHandler instanceof ViewGroupHandler) {
        ((ViewGroupHandler)parentHandler).onChildInserted(parent, child, insertType);
      }
    }

    return child;
  }

  @Override
  public void dispose() {
    deactivate(); // ensure listeners are unregistered if necessary
  }

  @Override
  public String toString() {
    return NlModel.class.getSimpleName() + " for " + myFile;
  }

  // ---- Implements ResourceNotificationManager.ResourceChangeListener ----

  @Override
  public void resourcesChanged(@NotNull Set<ResourceNotificationManager.Reason> reason) {
    requestRender();
  }

  // ---- Implements ModificationTracker ----

  @Override
  public long getModificationCount() {
    return myModificationCount;
  }

  public void notifyModified() {
    myModificationCount++;
  }
}
