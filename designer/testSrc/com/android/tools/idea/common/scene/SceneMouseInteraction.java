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
package com.android.tools.idea.common.scene;

import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.ActionGroupTarget;
import com.android.tools.idea.common.scene.target.ActionTarget;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Encapsulates basic mouse interaction on a Scene
 */
public class SceneMouseInteraction {
  private final Scene myScene;
  float myLastX;
  float myLastY;
  DisplayList myDisplayList = new DisplayList();

  public SceneMouseInteraction(Scene scene) {
    myScene = scene;
    repaint();
  }

  public float getLastX() { return myLastX; }
  public float getLastY() { return myLastY; }

  @NotNull
  private Optional<Target> findTarget(@Nullable SceneComponent component, @NotNull Predicate<Target> selector) {
    if (component == null) {
      return Optional.empty();
    }

    return component.getTargets()
             .stream()
             .filter(selector)
             .findFirst();
  }

  /**
   * Simulate a click on a given resize handle of the {@link SceneComponent} component
   *
   * @param component   the component we want to click on
   * @param selector    predicate used to find which target to click
   */
  public void mouseDown(@Nullable SceneComponent component, @NotNull Predicate<Target> selector) {
    findTarget(component, selector).ifPresent(target -> mouseDown(target.getCenterX(), target.getCenterY()));
  }

  /**
   * Simulate a click on a given resize handle of the component with componentId
   *
   * @param componentId the id of the component we want to click on
   * @param selector    predicate used to find which target to click
   */
  public void mouseDown(String componentId, @NotNull Predicate<Target> selector) {
    mouseDown(myScene.getSceneComponent(componentId), selector);
  }

  /**
   * Simulate a click on a given resize handle of the {@link SceneComponent} component
   *
   * @param component   the component we want to click on
   * @param targetClass the class of target we want to click on
   * @param pos         which target to click on
   */
  public void mouseDown(SceneComponent component, Class targetClass, int pos) {
    if (component != null) {
      List<Target> targets = component.getTargets();
      int n = 0;
      for (Target target : targets) {
        if (targetClass.isInstance(target)) {
          if (pos == n) {
            mouseDown(target.getCenterX(), target.getCenterY());
            return;
          }
          n++;
        }
      }
    }
  }

  /**
   * Simulate a click on a given resize handle of the component with componentId
   *
   * @param componentId the id of the component we want to click on
   * @param targetClass the class of target we want to click on
   * @param pos         which target to click on
   */
  public void mouseDown(String componentId, Class targetClass, int pos) {
    mouseDown(myScene.getSceneComponent(componentId), targetClass, pos);
  }

  /**
   * Simulate a click on a given resize handle of the component with componentId
   *
   * @param componentId the id of the component we want to click on
   * @param type        the type of resize handle we want to click on
   */
  public void mouseDown(String componentId, ResizeBaseTarget.Type type) {
    SceneComponent component = myScene.getSceneComponent(componentId);
    if (component != null) {
      ResizeBaseTarget target = component.getResizeTarget(type);
      mouseDown(target.getCenterX(), target.getCenterY());
    }
  }

  /**
   * Simulate a click on a given {@link AnchorTarget} of the {@link SceneComponent} component
   *
   * @param component   the component we want to click on
   * @param type        the type of anchor we want to click on
   */
  public void mouseDown(SceneComponent component, AnchorTarget.Type type) {
    if (component != null) {
      AnchorTarget target = AnchorTarget.findAnchorTarget(component, type);
      mouseDown(target.getCenterX(), target.getCenterY());
    }
  }

  /**
   * Simulate a click on a given {@link AnchorTarget} of the component with componentId
   *
   * @param componentId the id of the component we want to click on
   * @param type        the type of anchor we want to click on
   */
  public void mouseDown(String componentId, AnchorTarget.Type type) {
    mouseDown(myScene.getSceneComponent(componentId), type);
  }

  /**
   * Simulate a click on the center of component with componentId
   *
   * @param componentId the id of the component we want to click on
   */
  public void mouseDown(String componentId) {
    mouseDown(componentId, 0, 0);
  }

  /**
   * Simulate a click on the component with componentId
   *
   * @param componentId the id of the component we want to click on
   * @param offsetX     x offset from the center of the component
   * @param offsetY     y offset from the center of the component
   */
  public void mouseDown(String componentId, float offsetX, float offsetY) {
    SceneComponent component = myScene.getSceneComponent(componentId);
    if (component != null) {
      mouseDown(component.getCenterX() + offsetX, component.getCenterY() + offsetY);
    }
  }

  public void mouseDown(float x, float y) {
    myLastX = x;
    myLastY = y;
    SceneContext transform = SceneContext.get();
    myScene.mouseDown(transform, (int)myLastX, (int)myLastY);
    repaint();
  }

  /**
   * Simulate dragging the mouse to the coordinates (x, y). A series of drag events will be simulated.
   *
   * @param x target coordinate for dragging to
   * @param y target coordinate for dragging to
   */
  public void mouseDrag(float x, float y) {
    // drag first
    int steps = 10;
    float dx = x - myLastX;
    float dy = y - myLastY;
    float deltaX = dx / (float)steps;
    float deltaY = dy / (float)steps;
    dx = myLastX;
    dy = myLastY;
    SceneContext transform = SceneContext.get();
    if (deltaX != 0 || deltaY != 0) {
      for (int i = 0; i < steps; i++) {
        myScene.mouseDrag(transform, (int)dx, (int)dy);
        myScene.buildDisplayList(myDisplayList, System.currentTimeMillis());
        dx += deltaX;
        dy += deltaY;
      }
      myScene.mouseDrag(transform, (int)x, (int)y);
    }
    myLastX = x;
    myLastY = y;
    repaint();
  }

  /**
   * Simulate releasing the mouse above the given anchor of the {@link SceneComponent} component
   *
   * @param component   the component we want to click on
   * @param selector    predicate used to find which target to click
   */
  public void mouseRelease(@Nullable SceneComponent component, @NotNull Predicate<Target> selector) {
    findTarget(component, selector).ifPresent(target -> mouseRelease(target.getCenterX(), target.getCenterY()));
  }

  /**
   * Simulate releasing the mouse above the given anchor of the component
   * with the given componentId
   *
   * @param componentId the id of the component we will release the mouse above
   * @param selector    predicate used to find which target to click
   */
  public void mouseRelease(String componentId, @NotNull Predicate<Target> selector) {
    mouseRelease(myScene.getSceneComponent(componentId), selector);
  }

  /**
   * Simulate releasing the mouse above the given anchor of the {@link SceneComponent} component
   *
   * @param component   the component we will release the mouse above
   * @param targetClass the class of target we want to click on
   * @param pos         which target to click on
   */
  public void mouseRelease(SceneComponent component, Class targetClass, int pos) {
    if (component != null) {
      List<Target> targets = component.getTargets();
      int n = 0;
      for (Target target : targets) {
        if (targetClass.isInstance(target)) {
          if (pos == n) {
            mouseRelease(target.getCenterX(), target.getCenterY());
            return;
          }
          n++;
        }
      }
    }
  }

  /**
   * Simulate releasing the mouse above the given anchor of the component
   * with the given componentId
   *
   * @param componentId the id of the component we will release the mouse above
   * @param targetClass the class of target we want to click on
   * @param pos         which target to click on
   */
  public void mouseRelease(String componentId, Class targetClass, int pos) {
    mouseRelease(myScene.getSceneComponent(componentId), targetClass, pos);
  }

  /**
   * Simulate releasing the mouse above the given {@link AnchorTarget} of the component
   * with the given componentId
   *
   * @param componentId the id of the component we will release the mouse above
   * @param type        the type of anchor we need to be above
   */
  public void mouseRelease(String componentId, AnchorTarget.Type type) {
    SceneComponent component = myScene.getSceneComponent(componentId);
    if (component != null) {
      AnchorTarget target = AnchorTarget.findAnchorTarget(component, type);
      float x = target.getCenterX();
      float y = target.getCenterY();
      mouseRelease(x, y);
    }
  }

  /**
   * Simulate releasing the mouse above the component
   * with the given componentId
   *
   * @param componentId the id of the component we will release the mouse above
   */
  public void mouseRelease(String componentId) {
    mouseRelease(componentId, 0, 0);
  }

  /**
   * Simulate releasing the mouse above the component
   * with the given componentId
   *
   * @param componentId the id of the component we will release the mouse above
   * @param offsetX     x offset from the center of the component
   * @param offsetY     y offset from the center of the component
   */
  public void mouseRelease(String componentId, float offsetX, float offsetY) {
    SceneComponent component = myScene.getSceneComponent(componentId);
    if (component != null) {
      float x = component.getCenterX() + offsetX;
      float y = component.getCenterY() + offsetY;
      mouseRelease(x, y);
    }
  }

  /**
   * Simulate releasing the mouse at the coordinates (x, y).
   * Before doing the release, a serie of drag events will be simulated
   *
   * @param x coordinate on release
   * @param y coordinate on release
   */
  public void mouseRelease(float x, float y) {
    // drag first
    int steps = 10;
    float dx = x - myLastX;
    float dy = y - myLastY;
    float deltaX = dx / (float)steps;
    float deltaY = dy / (float)steps;
    dx = myLastX;
    dy = myLastY;
    SceneContext transform = SceneContext.get();
    if (deltaX != 0 || deltaY != 0) {
      for (int i = 0; i < steps; i++) {
        myScene.mouseDrag(transform, (int)dx, (int)dy);
        myScene.buildDisplayList(myDisplayList, System.currentTimeMillis());
        dx += deltaX;
        dy += deltaY;
      }
      myScene.mouseDrag(transform, (int)x, (int)y);
    }
    myScene.mouseRelease(transform, (int)x, (int)y);
    repaint();
  }

  /**
   * Simulate dragging mouse to coordinate (x, y) then cancel the interaction.
   *
   * @param x coordinate on cancel
   * @param y coordinate on cancel
   */
  public void mouseCancel(float x, float y) {
    // drag first
    int steps = 10;
    float dx = x - myLastX;
    float dy = y - myLastY;
    float deltaX = dx / (float)steps;
    float deltaY = dy / (float)steps;
    dx = myLastX;
    dy = myLastY;
    SceneContext transform = SceneContext.get();
    if (deltaX != 0 || deltaY != 0) {
      for (int i = 0; i < steps; i++) {
        myScene.mouseDrag(transform, (int)dx, (int)dy);
        myScene.buildDisplayList(myDisplayList, System.currentTimeMillis());
        dx += deltaX;
        dy += deltaY;
      }
      myScene.mouseDrag(transform, (int)x, (int)y);
    }
    myScene.mouseCancel();
    repaint();
  }

  public DisplayList getDisplayList() {
    return myDisplayList;
  }

  /**
   * Select the widget corresponding to the {@link SceneComponent} component
   */
  public void select(SceneComponent component, boolean selected) {
    if (component != null) {
      if (selected) {
        myScene.select(Collections.singletonList(component));
      } else {
        myScene.select(Collections.emptyList());
      }
      repaint();
    }
  }

  public void select(SceneComponent... components) {
    myScene.select(Arrays.stream(components).filter(it -> it != null).collect(Collectors.toList()));
    repaint();
  }

  public void clickAction(@NotNull String componentId, @NotNull Predicate<Target> selector) {
    clickAction(myScene.getSceneComponent(componentId), selector);
  }

  public void clickAction(@NotNull SceneComponent component, @NotNull Predicate<Target> selector) {
    ActionTarget actionTarget =
      component.getTargets().stream()
               .filter(target -> target instanceof ActionGroupTarget)
               .map(target -> (ActionGroupTarget)target)
               .flatMap(target -> target.getActionTargets().stream())
               .filter(selector::test)
               .findFirst().orElseThrow(() -> new NullPointerException("No action matching predicate for " + component));
    mouseDown(actionTarget.getCenterX(), actionTarget.getCenterY());
    mouseRelease(actionTarget.getCenterX(), actionTarget.getCenterY());
  }

  /**
   * Select the widget corresponding to the componentId
   */
  public void select(String componentId, boolean selected) {
    select(myScene.getSceneComponent(componentId), selected);
  }

  /**
   * Regenerate the display list
   */
  public void repaint() {
    myDisplayList.clear();
    myScene.buildDisplayList(myDisplayList, System.currentTimeMillis());
  }
}
