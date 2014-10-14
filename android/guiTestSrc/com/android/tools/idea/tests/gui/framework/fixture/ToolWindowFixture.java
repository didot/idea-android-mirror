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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.android.tools.idea.tests.gui.framework.GuiTests.SHORT_TIMEOUT;
import static org.fest.swing.timing.Pause.pause;

public abstract class ToolWindowFixture {
  @NotNull protected final ToolWindow myToolWindow;
  @NotNull protected final Robot myRobot;

  protected ToolWindowFixture(@NotNull final String toolWindowId, @NotNull final Project project, @NotNull Robot robot) {
    final Ref<ToolWindow> toolWindowRef = new Ref<ToolWindow>();
    Pause.pause(new Condition("Find tool window with ID '" + toolWindowId + "'") {
      @Override
      public boolean test() {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId);
        toolWindowRef.set(toolWindow);
        return toolWindow != null;
      }
    }, SHORT_TIMEOUT);
    myToolWindow = toolWindowRef.get();
    myRobot = robot;
  }

  @Nullable
  protected Content getContent(@NotNull final String displayName) {
    activate();
    waitUntilIsVisible();
    final AtomicReference<Content> contentRef = new AtomicReference<Content>();

    Pause.pause(new Condition("finding content '" + displayName + "'") {
      @Override
      public boolean test() {
        Content[] contents = myToolWindow.getContentManager().getContents();
        for (Content content : contents) {
          if (displayName.equals(content.getDisplayName())) {
            contentRef.set(content);
            return true;
          }
        }
        return false;
      }
    }, SHORT_TIMEOUT);
    return contentRef.get();
  }

  protected boolean isActive() {
    return GuiActionRunner.execute(new GuiQuery<Boolean>() {
      @Override
      protected Boolean executeInEDT() throws Throwable {
        return myToolWindow.isActive();
      }
    });
  }

  protected void activate() {
    if (isActive()) {
      return;
    }

    final Callback callback = new Callback();
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        myToolWindow.activate(callback);
      }
    });

    pause(new Condition("Wait for ToolWindow to be activated") {
      @Override
      public boolean test() {
        return callback.finished;
      }
    }, SHORT_TIMEOUT);
  }

  protected void waitUntilIsVisible() {
    pause(new Condition("Wait for ToolWindow to be visible") {
      @Override
      public boolean test() {
        if (!isActive()) {
          activate();
        }
        return isVisible();
      }
    });
  }

  private boolean isVisible() {
    return GuiActionRunner.execute(new GuiQuery<Boolean>() {
      @Override
      protected Boolean executeInEDT() throws Throwable {
        if (!myToolWindow.isVisible()) {
          return false;
        }
        JComponent component = myToolWindow.getComponent();
        return component.isVisible() && component.isShowing();
      }
    });
  }

  private static class Callback implements Runnable {
    volatile boolean finished;

    @Override
    public void run() {
      finished = true;
    }
  }
}
