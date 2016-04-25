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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import org.fest.reflect.exception.ReflectionError;
import org.fest.reflect.reference.TypeRef;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.ref.WeakReference;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickCancelButton;
import static junit.framework.Assert.assertNotNull;
import static org.fest.reflect.core.Reflection.field;

public abstract class IdeaDialogFixture<T extends DialogWrapper> extends ComponentFixture<IdeaDialogFixture, JDialog>
  implements ContainerFixture<JDialog> {
  @NotNull private final T myDialogWrapper;

  @Nullable
  protected static <T extends DialogWrapper> T getDialogWrapperFrom(@NotNull JDialog dialog, Class<T> dialogWrapperType) {
    try {
      WeakReference<DialogWrapper> dialogWrapperRef = field("myDialogWrapper").ofType(new TypeRef<WeakReference<DialogWrapper>>() {})
                                                                              .in(dialog)
                                                                              .get();
      assertNotNull(dialogWrapperRef);
      DialogWrapper wrapper = dialogWrapperRef.get();
      if (dialogWrapperType.isInstance(wrapper)) {
        return dialogWrapperType.cast(wrapper);
      }
    }
    catch (ReflectionError ignored) {
    }
    return null;
  }

  public static class DialogAndWrapper<T extends DialogWrapper> {
    public final JDialog dialog;
    public final T wrapper;

    public DialogAndWrapper(@NotNull JDialog dialog, @NotNull T wrapper) {
      this.dialog = dialog;
      this.wrapper = wrapper;
    }
  }

  @NotNull
  public static <T extends DialogWrapper> DialogAndWrapper<T> find(@NotNull Robot robot, @NotNull final Class<T> clz) {
    return find(robot, clz, GuiTests.matcherForType(JDialog.class));
  }

  @NotNull
  public static <T extends DialogWrapper> DialogAndWrapper<T> find(@NotNull Robot robot, @NotNull final Class<T> clz,
                                                                   @NotNull final GenericTypeMatcher<JDialog> matcher) {
    final Ref<T> wrapperRef = new Ref<>();
    JDialog dialog = GuiTests.waitUntilShowing(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        if (matcher.matches(dialog)) {
          T wrapper = getDialogWrapperFrom(dialog, clz);
          if (wrapper != null) {
            wrapperRef.set(wrapper);
            return true;
          }
        }
        return false;
      }
    });
    return new DialogAndWrapper<>(dialog, wrapperRef.get());
  }

  protected IdeaDialogFixture(@NotNull Robot robot, @NotNull JDialog target, @NotNull T dialogWrapper) {
    super(IdeaDialogFixture.class, robot, target);
    myDialogWrapper = dialogWrapper;
  }

  protected IdeaDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<T> dialogAndWrapper) {
    this(robot, dialogAndWrapper.dialog, dialogAndWrapper.wrapper);
  }

  @NotNull
  protected T getDialogWrapper() {
    return myDialogWrapper;
  }

  public void clickCancel() {
    findAndClickCancelButton(this);
    waitUntilNotShowing(); // Mac dialogs have an animation, wait until it hides
  }

  public void close() {
    robot().close(target());
  }

  public void waitUntilNotShowing() {
    Wait.seconds(15).expecting(target().getTitle() + " dialog to disappear").until(() -> !target().isShowing());
  }
}
