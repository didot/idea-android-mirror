/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea;

import com.android.tools.idea.actions.AndroidNewProjectAction;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.actions.NewProjectAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/** Customize Android IDE specific experience. */
public class AndroidInitialConfigurator {
  @NonNls private static final String CONFIG_V1 = "AndroidConfig.V1";
  @NonNls private static final String TODO_TOOLWINDOW_ACTION_ID = "ActivateTODOToolWindow";
  @NonNls private static final String ANDROID_TOOLWINDOW_ACTION_ID = "ActivateAndroidToolWindow";

  public AndroidInitialConfigurator(MessageBus bus,
                                    final PropertiesComponent propertiesComponent,
                                    final FileTypeManager fileTypeManager) {
    customizeSettings(propertiesComponent);

    // change default key maps to add a activate Android ToolWindow shortcut
    setActivateAndroidToolWindowShortcut();

    // Fix New Project actions
    fixNewProjectActions();
  }

  private static void customizeSettings(PropertiesComponent propertiesComponent) {
    if (!propertiesComponent.getBoolean(CONFIG_V1, false)) {
      propertiesComponent.setValue(CONFIG_V1, "true");
      CodeInsightSettings.getInstance().AUTO_POPUP_JAVADOC_INFO = true;
      UISettings.getInstance().SCROLL_TAB_LAYOUT_IN_EDITOR = true;
      EditorSettingsExternalizable.getInstance().setVirtualSpace(false);
    }
  }

  private static void setActivateAndroidToolWindowShortcut() {
    for (Keymap keymap: KeymapManagerEx.getInstanceEx().getAllKeymaps()) {
      KeyboardShortcut shortcut = removeFirstKeyboardShortcut(keymap, TODO_TOOLWINDOW_ACTION_ID);
      if (shortcut != null) {
        keymap.addShortcut(ANDROID_TOOLWINDOW_ACTION_ID, shortcut);
      }
    }
  }

  @Nullable
  private static KeyboardShortcut removeFirstKeyboardShortcut(Keymap keymap, String actionId) {
    Shortcut[] shortcuts = keymap.getShortcuts(actionId);
    for (Shortcut each : shortcuts) {
      if (each instanceof KeyboardShortcut) {
        keymap.removeShortcut(actionId, each);
        return (KeyboardShortcut)each;
      }
    }

    return null;
  }

  private static void fixNewProjectActions() {
    // TODO: This is temporary code. We should build out our own menu set and welcome screen exactly how we want.

    // Register the New Project action manually since we need to unregister the platform one
    ActionManager am = ActionManager.getInstance();
    am.unregisterAction("NewProject");

    AnAction action = new AndroidNewProjectAction();
    am.registerAction("NewProject", action);

    DefaultActionGroup ag = (DefaultActionGroup)am.getAction("WelcomeScreen.QuickStart.IDEA");
    AnAction[] children = ag.getChildren(null);
    for (AnAction child : children) {
      if (child instanceof NewProjectAction) {
        ag.remove(child);
      }
    }
    ag.add(action, Constraints.FIRST);
  }
}
