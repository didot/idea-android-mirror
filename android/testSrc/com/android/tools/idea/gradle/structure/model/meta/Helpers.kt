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
package com.android.tools.idea.gradle.structure.model.meta

internal fun <T : Any> ModelPropertyCore<T>.testValue() = (getParsedValue().value as? ParsedValue.Set.Parsed<T>)?.value
internal fun <T : Any> ModelPropertyCore<T>.testSetValue(value: T?) =
  setParsedValue(if (value != null) ParsedValue.Set.Parsed(value, DslText.Literal) else ParsedValue.NotSet)

internal fun <T : Any> ModelPropertyCore<T>.testSetReference(value: String) =
  setParsedValue(ParsedValue.Set.Parsed(dslText = DslText.Reference(value), value = null))

internal fun <T : Any> ModelPropertyCore<T>.testSetInterpolatedString(value: String) =
  setParsedValue(ParsedValue.Set.Parsed(dslText = DslText.InterpolatedString(value), value = null))

