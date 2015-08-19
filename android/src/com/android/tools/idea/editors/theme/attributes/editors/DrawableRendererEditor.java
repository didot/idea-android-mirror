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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ThemeEditorConstants;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.preview.AndroidThemePreviewPanel;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.swing.ui.SwatchComponent;
import com.intellij.openapi.module.Module;
import com.intellij.ui.ColorUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class that implements a {@link javax.swing.JTable} renderer and editor for drawable attributes.
 */
public class DrawableRendererEditor extends GraphicalResourceRendererEditor {
  @Nullable
  private final RenderTask myRenderTask;

  public DrawableRendererEditor(@NotNull ThemeEditorContext context, @NotNull AndroidThemePreviewPanel previewPanel, boolean isEditor) {
    super(context, previewPanel, isEditor);

    myRenderTask = configureRenderTask(context.getCurrentContextModule(), context.getConfiguration());
  }

  @Nullable
  public static RenderTask configureRenderTask(@NotNull final Module module, @NotNull final Configuration configuration) {
    RenderTask result = null;
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      final RenderService service = RenderService.get(facet);
      result = service.createTask(null, configuration, new RenderLogger("ThemeEditorLogger", module), null);
    }

    return result;
  }

  @Override
  protected void updateComponent(@NotNull ThemeEditorContext context, @NotNull ResourceComponent component, @NotNull EditedStyleItem item) {
    assert context.getResourceResolver() != null;

    if (myRenderTask != null) {
      component.setSwatchIcons(SwatchComponent.imageListOf(myRenderTask.renderDrawableAllStates(item.getSelectedValue())));
    }
    String nameText = String
      .format(ThemeEditorConstants.ATTRIBUTE_LABEL_TEMPLATE, ColorUtil.toHex(ThemeEditorConstants.RESOURCE_ITEM_COLOR),
              ThemeEditorUtils.getDisplayHtml(item));
    component.setNameText(nameText);
    component.setValueText(item.getValue());
  }

  @NotNull
  @Override
  protected ResourceType[] getAllowedResourceTypes() {
    return GraphicalResourceRendererEditor.DRAWABLES_ONLY;
  }
}
