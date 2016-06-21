/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.preference;

import com.android.resources.ResourceType;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.XmlBuilder;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.google.common.collect.ImmutableList;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import static com.android.SdkConstants.ATTR_TITLE;
import static com.android.SdkConstants.PreferenceAttributes.*;
import static com.android.SdkConstants.PreferenceTags.MULTI_SELECT_LIST_PREFERENCE;

public final class MultiSelectListPreferenceHandler extends PreferenceHandler {
  @Language("XML")
  @NotNull
  @Override
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    return new XmlBuilder()
      .startTag(tagName)
      .androidAttribute(ATTR_TITLE, "Multi select list preference")
      .endTag(tagName)
      .toString();
  }

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_DEFAULT_VALUE,
      ATTR_ENTRIES,
      ATTR_ENTRY_VALUES,
      ATTR_KEY,
      ATTR_TITLE,
      ATTR_SUMMARY,
      ATTR_DEPENDENCY,
      ATTR_ICON,
      ATTR_DIALOG_ICON);
  }

  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType type) {
    if (!super.onCreate(editor, parent, newChild, type)) {
      return false;
    }

    if (type.equals(InsertType.CREATE)) {
      EnumSet<ResourceType> array = EnumSet.of(ResourceType.ARRAY);
      String defaultValue = editor.displayResourceInput("Choose defaultValue Resource", array);

      if (defaultValue == null) {
        return false;
      }

      String entries = editor.displayResourceInput("Choose entries Resource", array);

      if (entries == null) {
        return false;
      }

      String entryValues = editor.displayResourceInput("Choose entryValues Resource", array);

      if (entryValues == null) {
        return false;
      }

      newChild.setAndroidAttribute(ATTR_DEFAULT_VALUE, defaultValue);
      newChild.setAndroidAttribute(ATTR_ENTRIES, entries);
      newChild.setAndroidAttribute(ATTR_ENTRY_VALUES, entryValues);
      newChild.setAndroidAttribute(ATTR_KEY, generateKey(newChild, MULTI_SELECT_LIST_PREFERENCE, "multi_select_list_preference_"));
    }

    return true;
  }
}
