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
package com.android.tools.idea.editors.theme;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceUrl;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods for style resolution.
 */
public class ResolutionUtils {
  @SuppressWarnings("ConstantNamingConvention") private static final Logger LOG = Logger.getInstance(ResolutionUtils.class);

  // Utility methods class isn't meant to be constructed, all methods are static.
  private ResolutionUtils() { }

  /**
   * Returns the style name, including the appropriate namespace.
   */
  @NotNull
  public static String getQualifiedStyleName(@NotNull StyleResourceValue style) {
    return (style.isFramework() ? SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX : SdkConstants.STYLE_RESOURCE_PREFIX) + style.getName();
  }

  /**
   * Returns the item name, including the appropriate namespace.
   */
  @NotNull
  public static String getQualifiedItemName(@NotNull ItemResourceValue item) {
    return (item.isFrameworkAttr() ? SdkConstants.PREFIX_ANDROID : "") + item.getName();
  }

  /**
   * Returns item value, maybe with "android:" qualifier,
   * If item is inside of the framework style, "android:" qualifier will be added
   * For example: For a value "@color/black" which is inside the "Theme.Holo.Light.DarkActionBar" style,
   * will be returned as "@android:color/black"
   */
  @NotNull
  public static String getQualifiedValue(@NotNull ItemResourceValue item) {
    ResourceUrl url = ResourceUrl.parse(item.getRawXmlValue(), item.isFramework());
    return url == null ? item.getRawXmlValue() : url.toString();
  }

  @Nullable
  private static StyleResourceValue getStyleResourceValue(@NotNull Configuration configuration, @NotNull String qualifiedStyleName) {
    String styleName;
    boolean isFrameworkStyle;

    if (qualifiedStyleName.startsWith(SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX)) {
      styleName = qualifiedStyleName.substring(SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX.length());
      isFrameworkStyle = true;
    } else {
      styleName = qualifiedStyleName;
      if (styleName.startsWith(SdkConstants.STYLE_RESOURCE_PREFIX)) {
        styleName = styleName.substring(SdkConstants.STYLE_RESOURCE_PREFIX.length());
      }
      isFrameworkStyle = false;
    }

    ResourceResolver resolver = configuration.getResourceResolver();
    assert resolver != null;
    return resolver.getStyle(styleName, isFrameworkStyle);
  }

  /**
   * Construct a ThemeEditorStyle instance for a theme with given name and source module, resolved in a given configuration.
   *
   * @param module source module of a theme, should be null only for framework or libraries themes.
   *               If you don't know source module of a theme, you should use {@link ThemeResolver#getTheme(String)} instead
   * @return returns ThemeEditorStyle for a qualifiedStyleName, if style doesn't exist will return null
   */
  @Nullable
  public static ThemeEditorStyle getStyle(@NotNull Configuration configuration, @NotNull final String qualifiedStyleName, @Nullable Module module) {
    final StyleResourceValue style = getStyleResourceValue(configuration, qualifiedStyleName);
    return style == null ? null : new ThemeEditorStyle(configuration, style, module);
  }

  @Nullable
  public static AttributeDefinition getAttributeDefinition(@NotNull Configuration configuration, @NotNull ItemResourceValue itemResValue) {
    AttributeDefinitions definitions;
    Module module = configuration.getModule();

    if (itemResValue.isFrameworkAttr()) {
      IAndroidTarget target = configuration.getTarget();
      assert target != null;

      AndroidTargetData androidTargetData = AndroidTargetData.getTargetData(target, module);
      assert androidTargetData != null;

      definitions = androidTargetData.getAllAttrDefs(module.getProject());
    }
    else {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null : String.format("Module %s is not an Android module", module.getName());

      definitions = facet.getLocalResourceManager().getAttributeDefinitions();
    }
    if (definitions == null) {
      return null;
    }
    return definitions.getAttrDefByName(itemResValue.getName());
  }
}
