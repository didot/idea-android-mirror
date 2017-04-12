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
package com.android.tools.idea.fonts;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Font;
import java.util.List;

/**
 * A {@link DownloadableFontCacheService} provides a cache of downloadable fonts and system fonts.
 * The cache is kept in the users SDK folder. If no SDK is setup a temporary folder is used until a proper SDK is created.
 * Currently there is one known font provider: Google fonts. Support for multiple providers may be added at a later time.
 * This service maintain a sorted list of fonts {@link #getFontFamilies} and holds methods for getting individual fonts.
 */
public interface DownloadableFontCacheService {

  @NotNull
  static DownloadableFontCacheService getInstance() {
    return ServiceManager.getService(DownloadableFontCacheService.class);
  }

  /**
   * Returns a list of downloadable fonts sorted by name.
   * The returned list can be modified without affecting the font cache.
   */
  @NotNull
  List<FontFamily> getFontFamilies();

  /**
   * Returns a list of system fonts sorted by name.
   * The returned list can be modified without affecting the font cache.
   */
  @NotNull
  List<FontFamily> getSystemFontFamilies();

  /**
   * Returns a {@link FontFamily} for a named system font or <code>null</code>
   * if no font with the specified name exists.
   */
  @Nullable
  FontFamily getSystemFont(@NotNull String name);

  /**
   * Return a {@link FontFamily} for the default system font which is "sans serif".
   */
  @NotNull
  FontFamily getDefaultSystemFont();

  /**
   * Loads a {@link Font} for displaying the name of the font.
   * The font returned may only contain the glyphs for the font name and may not be able to display other characters.
   */
  @Nullable
  Font loadMenuFont(@NotNull FontFamily fontFamily);

  /**
   * Loads a {@link Font} for general use.
   * The supported character set is dependent on the font which may or may not include latin characters.
   */
  @Nullable
  Font loadDetailFont(@NotNull FontDetail fontDetail);

  /**
   * Will start a download of the most recent downloadable font directory.
   * @param success optional callback after a successful download of a new font directory.
   * @param failure optional callback after a failed download of a font directory.
   */
  void refresh(@Nullable Runnable success, @Nullable Runnable failure);
}
