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
package com.android.tools.idea.wizard;

import com.android.sdklib.AndroidVersion;
import com.google.common.base.Function;
import com.intellij.openapi.Disposable;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;

import static com.android.tools.idea.wizard.ScopedStateStore.Key;

/**
 * Gallery of Android activity templates.
 */
public final class ActivityGalleryStep extends DynamicWizardStepWithHeaderAndDescription {
  public static final Key<TemplateEntry[]> KEY_TEMPLATES =
    ScopedStateStore.createKey("template.list", ScopedStateStore.Scope.STEP, TemplateEntry[].class);
  private final Key<TemplateEntry> myCurrentSelectionKey;
  private ASGallery<TemplateEntry> myGallery;

  public ActivityGalleryStep(Key<TemplateEntry> currentSelectionKey, @NotNull Disposable disposable) {
    super("Add an activity to Phone and Tablet", null,
          AndroidIcons.Wizards.FormFactorPhoneTablet, disposable);
    myCurrentSelectionKey = currentSelectionKey;
    setBodyComponent(createGallery());
  }

  private static String format(ScopedStateStore state, String formatString, Key<?>... keys) {
    Object[] arguments = new Object[keys.length];
    int i = 0;
    for (Key<?> key : keys) {
      arguments[i++] = state.get(key);
    }
    return String.format(formatString, arguments);
  }

  private JComponent createGallery() {
    myGallery = new ASGallery<TemplateEntry>();
    myGallery.setThumbnailSize(new Dimension(256, 256));
    myGallery.setLabelProvider(new Function<TemplateEntry, String>() {
      @Override
      public String apply(TemplateEntry template) {
        return template.getTitle();
      }
    });
    myGallery.setImageProvider(new Function<TemplateEntry, Image>() {
      @Override
      public Image apply(TemplateEntry input) {
        return input.getImage();
      }
    });
    myGallery.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        saveState(myGallery);
      }
    });
    return new JBScrollPane(myGallery);
  }

  @Override
  public boolean validate() {
    TemplateEntry template = myState.get(myCurrentSelectionKey);
    PageStatus status;
    if (template == null) {
      status = PageStatus.NOTHING_SELECTED;
    }
    else if (isIncompatibleMinSdk(template)) {
      status = PageStatus.INCOMPATIBLE_MAIN_SDK;
    }
    else if (isIncompatibleBuildApi(template)) {
      status = PageStatus.INCOMPATIBLE_BUILD_API;
    }
    else {
      status = PageStatus.OK;
    }
    setErrorHtml(status.formatMessage(myState));
    return status.isPageValid();
  }

  private boolean isIncompatibleBuildApi(TemplateEntry template) {
    Integer buildSdk = myState.get(AddAndroidActivityPath.KEY_BUILD_SDK);
    return buildSdk != null && buildSdk < template.getMinBuildApi();
  }

  private boolean isIncompatibleMinSdk(@NotNull TemplateEntry template) {
    AndroidVersion minSdk = myState.get(AddAndroidActivityPath.KEY_MIN_SDK);
    System.out.printf("Context: %s, Template: %d\n", minSdk, template.getMinSdk());
    return minSdk != null && minSdk.getApiLevel() < template.getMinSdk();
  }

  @Override
  public void init() {
    super.init();
    TemplateListProvider templateListProvider = new TemplateListProvider();
    TemplateEntry[] list = templateListProvider.deriveValue(myState, AddAndroidActivityPath.KEY_IS_LAUNCHER, null);
    myGallery.setModel(JBList.createDefaultListModel((Object[])list));
    myState.put(KEY_TEMPLATES, list);
    if (list.length > 0) {
      myState.put(myCurrentSelectionKey, list[0]);
    }
    register(myCurrentSelectionKey, myGallery, new ComponentBinding<TemplateEntry, ASGallery<TemplateEntry>>() {
      @Override
      public void setValue(TemplateEntry newValue, @NotNull ASGallery<TemplateEntry> component) {
        component.setSelectedElement(newValue);
      }

      @Override
      @Nullable
      public TemplateEntry getValue(@NotNull ASGallery<TemplateEntry> component) {
        return component.getSelectedElement();
      }
    });
    register(KEY_TEMPLATES, myGallery, new ComponentBinding<TemplateEntry[], ASGallery<TemplateEntry>>() {
      @Override
      public void setValue(TemplateEntry[] newValue, @NotNull ASGallery<TemplateEntry> component) {
        component.setModel(JBList.createDefaultListModel((Object[])newValue));
      }
    });
    registerValueDeriver(KEY_TEMPLATES, templateListProvider);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGallery;
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Activity Gallery";
  }

  private enum PageStatus {
    OK, INCOMPATIBLE_BUILD_API, INCOMPATIBLE_MAIN_SDK, NOTHING_SELECTED;

    public boolean isPageValid() {
      return this == OK;
    }

    @Nullable
    public String formatMessage(ScopedStateStore state) {
      switch (this) {
        case OK:
          return null;
        case INCOMPATIBLE_BUILD_API:
          return format(state, "Selected activity template has a minimum build API level of %d.", AddAndroidActivityPath.KEY_BUILD_SDK);
        case INCOMPATIBLE_MAIN_SDK:
          return format(state, "Selected activity template has a minimum SDK level of %s.", AddAndroidActivityPath.KEY_MIN_SDK);
        case NOTHING_SELECTED:
          return "No activity template was selected.";
        default:
          throw new IllegalArgumentException(name());
      }
    }

  }

}
