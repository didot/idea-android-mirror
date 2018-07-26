/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.sketchImporter.structure;

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.ColorDeserializer;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.PointDeserializer;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.SketchLayerDeserializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

public class SketchParser {
  public static @Nullable
  SketchPage open(@NotNull String path) {

    try (Reader reader = new FileReader(path)) {
      Gson gson = new GsonBuilder()
        .registerTypeAdapter(SketchLayer.class, new SketchLayerDeserializer())
        .registerTypeAdapter(Color.class, new ColorDeserializer())
        .registerTypeAdapter(Point2D.Double.class, new PointDeserializer())
        .registerTypeAdapter(SketchPoint2D.class, new PointDeserializer())
        .create();
      return gson.fromJson(reader, SketchPage.class);
    }
    catch (IOException e) {
      Logger.getInstance(SketchParser.class).warn("Sketch file not found.", e);
    }

    return null;
  }
}
