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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslInfixExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;

public class PluginModelImpl implements PluginModel {
  @NonNls private static final String APPLY = "apply";
  @NonNls private static final String ID = "id";
  @NonNls private static final String PLUGIN = "plugin";
  @NonNls private static final String VERSION = "version";

  @NotNull
  private final GradleDslElement myCompleteElement;
  @NotNull
  private final GradleDslSimpleExpression myDslElement;
  @Nullable
  private final GradleDslSimpleExpression myVersionElement;
  @Nullable
  private final GradleDslSimpleExpression myApplyElement;

  @NotNull
  public static List<PluginModelImpl> create(@NotNull GradlePropertiesDslElement dslElement) {
    List<GradleDslElement> elements = dslElement.getAllPropertyElements();
    List<PluginModelImpl> results = new ArrayList<>();

    for (GradleDslElement e : elements) {
      if (e instanceof GradleDslSimpleExpression) {
        if (e instanceof GradleDslMethodCall) {
          GradleDslMethodCall element = (GradleDslMethodCall)e;
          GradleDslExpressionList elementArguments = element.getArgumentsElement();
          for(GradleDslSimpleExpression item : elementArguments.getSimpleExpressions()) {
            results.add(new PluginModelImpl(e, item));
          }
        }
        else {
          results.add(new PluginModelImpl(e, (GradleDslSimpleExpression)e));
        }
      }
      else if (e instanceof GradleDslExpressionMap) {
        GradleDslElement element = ((GradleDslExpressionMap)e).getElement(PLUGIN);
        if (element instanceof GradleDslSimpleExpression) {
          results.add(new PluginModelImpl(e, (GradleDslSimpleExpression)element));
        }
      }
      else if (e instanceof GradleDslExpressionList) {
        GradleDslExpressionList element = (GradleDslExpressionList)e;
        for (GradleDslSimpleExpression item : element.getSimpleExpressions()) {
          results.add(new PluginModelImpl(item, item));
        }
      }
      else if (e instanceof GradleDslInfixExpression) {
        GradleDslInfixExpression infixExpression = (GradleDslInfixExpression) e;
        // FIXME: these casts, nullability checks, etc.
        GradleDslSimpleExpression pluginElement = (GradleDslSimpleExpression)infixExpression.getElement(ID);
        GradleDslSimpleExpression applyElement = (GradleDslSimpleExpression)infixExpression.getElement(APPLY);
        GradleDslSimpleExpression versionElement = (GradleDslSimpleExpression)infixExpression.getElement(VERSION);
        results.add(new PluginModelImpl(infixExpression, pluginElement, versionElement, applyElement));
      }
    }

    return results;
  }

  public static Map<String, PluginModelImpl> deduplicatePlugins(@NotNull List<PluginModelImpl> models) {
    Map<String, PluginModelImpl> modelMap = new LinkedHashMap<>();
    for (PluginModelImpl model : models) {
      ResolvedPropertyModel propertyModel = model.name();
      if (propertyModel.getValueType() == STRING) {
        modelMap.put(propertyModel.forceString(), model);
      }
    }
    return modelMap;
  }

  public static void removePlugins(@NotNull List<PluginModelImpl> models, @NotNull String name) {
    for (PluginModelImpl model : models) {
      if (name.equals(model.name().toString())) {
        model.remove();
      }
    }
  }

  public PluginModelImpl(@NotNull GradleDslElement completeElement, @NotNull GradleDslSimpleExpression element) {
    myDslElement = element;
    myCompleteElement = completeElement;
    // FIXME(xof)
    myVersionElement = null;
    myApplyElement = null;

  }

  public PluginModelImpl(
    @NotNull GradleDslInfixExpression completeElement,
    @NotNull GradleDslSimpleExpression element,
    @Nullable GradleDslSimpleExpression versionElement,
    @Nullable GradleDslSimpleExpression applyElement
  ) {
    myDslElement = element;
    myCompleteElement = completeElement;
    myVersionElement = versionElement;
    myApplyElement = applyElement;
  }

  @NotNull
  @Override
  public ResolvedPropertyModel name() {
    return GradlePropertyModelBuilder.create(myDslElement).buildResolved();
  }

  @NotNull
  @Override
  public ResolvedPropertyModel version() {
    // FIXME(xof): this needs transforms
    if (myCompleteElement instanceof GradleDslInfixExpression) {
      return GradlePropertyModelBuilder.create((GradleDslInfixExpression) myCompleteElement, VERSION).buildResolved();
    }
    return GradlePropertyModelBuilder.create(myVersionElement).buildResolved();
  }

  @NotNull
  @Override
  public ResolvedPropertyModel apply() {
    // FIXME(xof): so does this
    if (myCompleteElement instanceof GradleDslInfixExpression) {
      return GradlePropertyModelBuilder.create((GradleDslInfixExpression) myCompleteElement, APPLY).buildResolved();
    }
    return GradlePropertyModelBuilder.create(myApplyElement).buildResolved();
  }

  @Override
  public void remove() {
    PropertyUtil.removeElement(myCompleteElement);
  }

  @Nullable
  @Override
  public PsiElement getPsiElement() {
    return myCompleteElement.getPsiElement();
  }

}
