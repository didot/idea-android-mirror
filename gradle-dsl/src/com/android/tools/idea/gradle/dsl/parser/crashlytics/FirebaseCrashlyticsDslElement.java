/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.crashlytics;

import static com.android.tools.idea.gradle.dsl.model.crashlytics.FirebaseCrashlyticsModelImpl.NATIVE_SYMBOL_UPLOAD_ENABLED;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class FirebaseCrashlyticsDslElement extends GradleDslBlockElement {
  public static final ExternalToModelMap ktsToModelMap = Stream.of(new Object[][] {
    {"nativeSymbolUploadEnabled", property, NATIVE_SYMBOL_UPLOAD_ENABLED, VAR}
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelMap = Stream.of(new Object[][] {
    {"nativeSymbolUploadEnabled", property, NATIVE_SYMBOL_UPLOAD_ENABLED, VAR},
    {"nativeSymbolUploadEnabled", exactly(1), NATIVE_SYMBOL_UPLOAD_ENABLED, SET}
  }).collect(toModelMap());

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelMap, ktsToModelMap);
  }

  public static final PropertiesElementDescription<FirebaseCrashlyticsDslElement> FIREBASE_CRASHLYTICS =
    new PropertiesElementDescription<>("firebaseCrashlytics", FirebaseCrashlyticsDslElement.class, FirebaseCrashlyticsDslElement::new);

  protected FirebaseCrashlyticsDslElement(@NotNull GradleDslElement element, @NotNull GradleNameElement name) {
    super(element, name);
  }
}
