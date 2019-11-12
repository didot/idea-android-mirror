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
package com.android.tools.idea.gradle.dsl.parser.android;

import static com.android.tools.idea.gradle.dsl.model.android.KotlinOptionsModelImpl.JVM_TARGET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.semantics.SemanticsDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class KotlinOptionsDslElement extends GradleDslBlockElement {
  @NonNls public static final String KOTLIN_OPTIONS_BLOCK_NAME = "kotlinOptions";
  @NonNls public static final String JVM_TARGET_ATTRIBUTE_NAME = "jvmTarget";

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, Pair<String, SemanticsDescription>> modelNameMap = Stream.of(new Object[][]{
    {JVM_TARGET_ATTRIBUTE_NAME, property, JVM_TARGET, VAR},
  }).collect(toImmutableMap(data -> new Pair<>((String) data[0], (Integer) data[1]),
                            data -> new Pair<>((String) data[2], (SemanticsDescription) data[3])));

  @Override
  @NotNull
  public ImmutableMap<Pair<String,Integer>, Pair<String,SemanticsDescription>> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    if (converter instanceof KotlinDslNameConverter || converter instanceof GroovyDslNameConverter) {
      return modelNameMap;
    }
    else {
      return super.getExternalToModelMap(converter);
    }
  }

  public KotlinOptionsDslElement(@NotNull GradleDslElement parent) {
    super(parent, GradleNameElement.create(KOTLIN_OPTIONS_BLOCK_NAME));
  }
}
