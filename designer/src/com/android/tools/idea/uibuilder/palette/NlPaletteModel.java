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

import com.android.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NlPaletteModel {
  private static final Logger LOG = Logger.getInstance(NlPaletteModel.class);

  private static final String METADATA = "palette.xml";

  private static final String ELEM_ITEM = "item";
  private static final String ELEM_PALETTE = "palette";
  private static final String ELEM_CREATION = "creation";

  private static final String ATTR_TAG = "tag";
  private static final String ATTR_NAME = "name";
  private static final String ATTR_TITLE = "title";
  private static final String ATTR_TOOLTIP = "tooltip";
  private static final String ATTR_ICON = "icon";

  @NotNull private final List<NlPaletteGroup> myGroups;
  @NotNull private final Map<String, Element> myTag2Model;

  public NlPaletteModel() {
    myGroups = new ArrayList<NlPaletteGroup>();
    myTag2Model = new HashMap<String, Element>();
  }

  public void loadPalette() {
    Document document = loadDocument(METADATA);
    if (document != null) {
      loadPalette(document);
    }
  }

  @NotNull
  public List<NlPaletteGroup> getGroups() {
    return myGroups;
  }

  @VisibleForTesting
  void loadPalette(@NotNull Document document) {
    loadModels(document);
    Element palette = document.getRootElement().getChild(ELEM_PALETTE);
    if (palette == null) {
      LOG.warn("Missing palette tag");
      return;
    }
    for (Element groupElement : palette.getChildren()) {
      NlPaletteGroup group = loadGroup(groupElement);
      if (group != null) {
        myGroups.add(group);
      }
    }
  }

  @Nullable
  private NlPaletteGroup loadGroup(@NotNull Element groupElement) {
    String name = groupElement.getAttributeValue(ATTR_NAME);
    if (name == null) {
      LOG.warn("Group element without a name");
      return null;
    }
    NlPaletteGroup group = new NlPaletteGroup(name);
    for (Element itemElement : groupElement.getChildren(ELEM_ITEM)) {
      String tag = itemElement.getAttributeValue(ATTR_TAG);
      if (tag == null) {
        LOG.warn(String.format("Item without a tag for group: %s", name));
        continue;
      }
      Element modelElement = myTag2Model.get(tag);
      if (modelElement == null) {
        LOG.warn(String.format("Model not found for group: %s with tag: %s", name, tag));
        continue;
      }
      Element paletteElement = modelElement.getChild(ELEM_PALETTE);
      if (paletteElement == null) {
        LOG.warn(String.format("Palette not found on model for group: %s with tag: %s", name, tag));
        continue;
      }
      NlPaletteItem item = loadItem(itemElement, modelElement);
      if (item == null) {
        continue;
      }
      group.add(item);
      for (Element subItemElement : itemElement.getChildren(ELEM_ITEM)) {
        NlPaletteItem subItem = loadItem(subItemElement, modelElement);
        if (subItem == null) {
          continue;
        }
        group.add(subItem);
      }
    }
    return group;
  }

  @Nullable
  private static NlPaletteItem loadItem(@NotNull Element itemElement, @NotNull Element modelElement) {
    String title = getAttributeValue(itemElement, modelElement, ATTR_TITLE);
    String tooltip = getAttributeValue(itemElement, modelElement, ATTR_TOOLTIP);
    String iconPath = getAttributeValue(itemElement, modelElement, ATTR_ICON);
    String creation = getElementValue(itemElement, modelElement, ELEM_CREATION);
    if (title.isEmpty()) {
      LOG.warn(String.format("No title found for item with tag: %s", modelElement.getAttributeValue(ATTR_TAG)));
      return null;
    }
    if (creation.isEmpty()) {
      LOG.warn(String.format("No creation found for item with tag: %s", modelElement.getAttributeValue(ATTR_TAG)));
      return null;
    }
    return new NlPaletteItem(title, iconPath, tooltip, creation);
  }

  @NotNull
  private static String getAttributeValue(@NotNull Element fromElement, @NotNull Element modelElement, @NotNull String attributeName) {
    String value = fromElement.getAttributeValue(attributeName);
    if (value != null) {
      return value;
    }
    Element paletteElement = modelElement.getChild(ELEM_PALETTE);
    assert paletteElement != null;
    value = paletteElement.getAttributeValue(attributeName);
    return value != null ? value : "";
  }

  @NotNull
  private static String getElementValue(@NotNull Element fromElement, @NotNull Element modelElement, @NotNull String tagName) {
    Element element = fromElement.getChild(tagName);
    if (element != null) {
      return element.getText();
    }
    element = modelElement.getChild(tagName);
    if (element == null) {
      return "";
    }
    return element.getText();
  }

  private void loadModels(@NotNull Document document) {
    for (Element element : document.getRootElement().getChildren()) {
      String tag = element.getAttributeValue(ATTR_TAG);
      if (tag != null) {
        myTag2Model.put(tag, element);
      }
    }
  }

  @Nullable
  private Document loadDocument(String metadata) {
    try {
      InputStream stream = getClass().getResourceAsStream(metadata);
      Document document = new SAXBuilder().build(stream);
      stream.close();
      return document;
    }
    catch (Throwable e) {
      LOG.error(e);
      return null;
    }
  }
}
