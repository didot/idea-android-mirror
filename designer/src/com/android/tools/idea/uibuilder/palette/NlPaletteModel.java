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
package com.android.tools.idea.uibuilder.palette;

import com.android.tools.idea.uibuilder.model.NlLayoutType;
import org.jetbrains.annotations.NotNull;
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;

import javax.xml.bind.JAXBException;
import java.io.*;
import java.util.EnumMap;
import java.util.Map;

public class NlPaletteModel {
  public static final String ANDROID_PALETTE = "android-palette";
  public static final String PALETTE_VERSION = "v1";

  private final Map<NlLayoutType, Palette> myTypeToPalette;
  private final Project myProject;

  public static NlPaletteModel get(@NotNull Project project) {
    return project.getComponent(NlPaletteModel.class);
  }

  private NlPaletteModel(@NotNull Project project) {
    myTypeToPalette = new EnumMap<>(NlLayoutType.class);
    myProject = project;
  }

  @NotNull
  Palette getPalette(@NotNull NlLayoutType type) {
    Palette palette = myTypeToPalette.get(type);

    if (palette == null) {
      loadPalette(type);
      return myTypeToPalette.get(type);
    }
    else {
      return palette;
    }
  }

  private void loadPalette(@NotNull NlLayoutType type) {
    try {
      String name = type.getPaletteFileName();
      File file = new File(PathManager.getSystemPath(), ANDROID_PALETTE + File.separatorChar + PALETTE_VERSION + File.separatorChar + name);

      if (!file.exists()) {
        copyPredefinedPalette(file, name);
      }

      Reader reader = new InputStreamReader(new FileInputStream(file));
      try {
        loadPalette(reader, type);
      }
      finally {
        reader.close();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    catch (JAXBException e) {
      throw new RuntimeException(e);
    }
  }

  @VisibleForTesting
  void loadPalette(@NotNull Reader reader, @NotNull NlLayoutType type) throws JAXBException {
    Palette palette = Palette.parse(reader, ViewHandlerManager.get(myProject));
    myTypeToPalette.put(type, palette);
  }

  private static void copyPredefinedPalette(@NotNull File paletteFile, @NotNull String metadata) throws IOException {
    InputStream stream = NlPaletteModel.class.getResourceAsStream(metadata);
    File folder = paletteFile.getParentFile();
    if (!folder.isDirectory() && !folder.mkdirs()) {
      throw new IOException("Could not create directory: " + folder);
    }
    FileOutputStream output = new FileOutputStream(paletteFile);
    try {
      FileUtil.copy(stream, output);
    }
    finally {
      stream.close();
      output.close();
    }
  }
}
