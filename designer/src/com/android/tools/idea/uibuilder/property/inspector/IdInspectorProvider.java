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
package com.android.tools.idea.uibuilder.property.inspector;

import com.android.tools.idea.uibuilder.handlers.constraint.WidgetConstraintPanel;
import com.android.tools.idea.uibuilder.handlers.constraint.WidgetNavigatorPanel;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import com.android.tools.idea.uibuilder.property.editors.NlEnumEditor;
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.SdkConstants.PreferenceTags.*;
import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;

public class IdInspectorProvider implements InspectorProvider {
  private IdInspectorComponent myComponent;

  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components,
                              @NotNull Map<String, NlProperty> properties,
                              @NotNull NlPropertiesManager propertiesManager) {
    for (NlComponent component : components) {
      switch (component.getTagName()) {
        case CHECK_BOX_PREFERENCE:
        case EDIT_TEXT_PREFERENCE:
        case LIST_PREFERENCE:
        case MULTI_SELECT_LIST_PREFERENCE:
        case PREFERENCE_CATEGORY:
        case PREFERENCE_SCREEN:
        case RINGTONE_PREFERENCE:
        case SWITCH_PREFERENCE:
        case TAG_GROUP:
        case TAG_ITEM:
        case TAG_MENU:
          return false;
      }
    }
    return true;
  }

  @NotNull
  @Override
  public InspectorComponent createCustomInspector(@NotNull List<NlComponent> components,
                                                  @NotNull Map<String, NlProperty> properties,
                                                  @NotNull NlPropertiesManager propertiesManager) {
    if (myComponent == null) {
      myComponent = new IdInspectorComponent(propertiesManager);
    }
    myComponent.updateProperties(components, properties, propertiesManager);
    return myComponent;
  }

  private static class IdInspectorComponent implements InspectorComponent {
    private final NlReferenceEditor myIdEditor;
    private final NlEnumEditor myWidthEditor;
    private final NlEnumEditor myHeightEditor;
    private final WidgetConstraintPanel myConstraintWidget;
    private final WidgetNavigatorPanel myWidgetNavigatorPanel;

    private NlProperty myIdAttr;
    private NlProperty myLayoutWidth;
    private NlProperty myLayoutHeight;

    public IdInspectorComponent(@NotNull NlPropertiesManager propertiesManager) {
      myIdEditor = NlReferenceEditor.createForInspector(propertiesManager.getProject(), DEFAULT_LISTENER);
      myWidthEditor = NlEnumEditor.createForInspector(DEFAULT_LISTENER);
      myHeightEditor = NlEnumEditor.createForInspector(DEFAULT_LISTENER);
      myConstraintWidget = new WidgetConstraintPanel(ImmutableList.of());
      myWidgetNavigatorPanel = new WidgetNavigatorPanel(propertiesManager.getDesignSurface());
    }

    @Override
    public void updateProperties(@NotNull List<NlComponent> components,
                                 @NotNull Map<String, NlProperty> properties,
                                 @NotNull NlPropertiesManager propertiesManager) {
      myIdAttr = properties.get(ATTR_ID);
      myLayoutWidth = properties.get(ATTR_LAYOUT_WIDTH);
      myLayoutHeight = properties.get(ATTR_LAYOUT_HEIGHT);
      myWidgetNavigatorPanel.updateComponents(components);
      myConstraintWidget.updateComponents(components);
      myConstraintWidget.setVisible(hasParentConstraintLayout(components));
    }

    @Override
    public int getMaxNumberOfRows() {
      return 5;
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector) {
      inspector.addPanel(myWidgetNavigatorPanel);
      myIdEditor.setLabel(inspector.addComponent("ID", null, myIdEditor.getComponent()));
      inspector.addPanel(myConstraintWidget);
      myWidthEditor.setLabel(inspector.addComponent(ATTR_LAYOUT_WIDTH, null, myWidthEditor.getComponent()));
      myHeightEditor.setLabel(inspector.addComponent(ATTR_LAYOUT_HEIGHT, null, myHeightEditor.getComponent()));
      refresh();
    }

    @Override
    public void refresh() {
      myIdEditor.setEnabled(myIdAttr != null);
      if (myIdAttr != null) {
        myIdEditor.setProperty(myIdAttr);
        setToolTip(myIdEditor, myIdAttr);
      }
      myWidthEditor.setEnabled(myLayoutWidth != null);
      if (myLayoutWidth != null) {
        myWidthEditor.setProperty(myLayoutWidth);
        setToolTip(myWidthEditor, myLayoutWidth);
      }
      myHeightEditor.setEnabled(myLayoutHeight != null);
      if (myLayoutHeight != null) {
        myHeightEditor.setProperty(myLayoutHeight);
        setToolTip(myHeightEditor, myLayoutHeight);
      }
      if (myIdAttr != null && !myIdAttr.getComponents().isEmpty()) {
        myConstraintWidget.setProperty(myIdAttr);
      }
    }

    @Nullable
    @Override
    public NlComponentEditor getEditorForProperty(@NotNull String propertyName) {
      switch (propertyName) {
        case ATTR_ID:
          return myIdEditor;
        case ATTR_LAYOUT_WIDTH:
          return myWidthEditor;
        case ATTR_LAYOUT_HEIGHT:
          return myHeightEditor;
        default:
          return null;
      }
    }

    private static void setToolTip(@NotNull NlComponentEditor editor, @NotNull NlProperty property) {
      JLabel label = editor.getLabel();
      if (label != null) {
        label.setToolTipText(property.getTooltipText());
      }
    }

    private static boolean hasParentConstraintLayout(@NotNull List<NlComponent> components) {
      if (components.isEmpty()) {
        return false;
      }
      NlComponent parent = components.get(0).getParent();
      return parent != null && parent.isOrHasSuperclass(CONSTRAINT_LAYOUT);
    }
  }
}
