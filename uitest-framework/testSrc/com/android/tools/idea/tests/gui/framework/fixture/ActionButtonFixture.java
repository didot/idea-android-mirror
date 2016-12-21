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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.util.Ref;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collection;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class ActionButtonFixture extends JComponentFixture<ActionButtonFixture, ActionButton> {
  @NotNull
  public static ActionButtonFixture findByActionId(@NotNull final String actionId,
                                                   @NotNull final Robot robot,
                                                   @NotNull final Container container) {
    final Ref<ActionButton> actionButtonRef = new Ref<>();
    Wait.seconds(1).expecting("ActionButton with ID '" + actionId + "' to be visible")
      .until(() -> {
        Collection<ActionButton> found = robot.finder().findAll(container, new GenericTypeMatcher<ActionButton>(ActionButton.class) {
          @Override
          protected boolean isMatching(@NotNull ActionButton button) {
            if (button.isVisible()) {
              AnAction action = button.getAction();
              if (action != null) {
                String id = ActionManager.getInstance().getId(action);
                return actionId.equals(id);
              }
            }
            return false;
          }
        });
        if (found.size() == 1) {
          actionButtonRef.set(getFirstItem(found));
          return true;
        }
        return false;
      });

    ActionButton button = actionButtonRef.get();
    if (button == null) {
      throw new ComponentLookupException("Failed to find ActionButton with ID '" + actionId + "'");
    }
    return new ActionButtonFixture(robot, button);
  }

  @NotNull
  public ActionButtonFixture waitUntilEnabledAndShowing() {
    Wait.seconds(1).expecting("action to be enabled and showing").until(() -> GuiQuery.getNonNull(
      () -> target().getAction().getTemplatePresentation().isEnabledAndVisible()
            && target().isShowing() && target().isVisible() && target().isEnabled()));
    return this;
  }

  @NotNull
  public static ActionButtonFixture findByText(@NotNull final String text, @NotNull Robot robot, @NotNull Container container) {
    final ActionButton button = robot.finder().find(container, Matchers.byText(ActionButton.class, text));
    return new ActionButtonFixture(robot, button);
  }

  public ActionButtonFixture(@NotNull Robot robot, @NotNull ActionButton target) {
    super(ActionButtonFixture.class, robot, target);
  }
}
