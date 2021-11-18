// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class WhatsNewAssistantIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, WhatsNewAssistantIcons.class.getClassLoader(), cacheKey, flags);
  }

  public static final class Preview {
    /** 30x30 */ public static final @NotNull Icon Whats_new_icon = load("preview/whats_new_icon.png", 0, 1);
  }

  public static final class Stable {
    /** 30x30 */ public static final @NotNull Icon Whats_new_icon = load("stable/whats_new_icon.png", 0, 1);
  }
}
