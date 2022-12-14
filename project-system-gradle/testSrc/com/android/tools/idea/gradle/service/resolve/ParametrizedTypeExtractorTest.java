/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.resolve;

import junit.framework.TestCase;

public class ParametrizedTypeExtractorTest extends TestCase {
  public void testActionParametersWildcardNdo() {
    AndroidDslContributor.ParametrizedTypeExtractor extractor = new AndroidDslContributor.ParametrizedTypeExtractor(
      "org.gradle.api.Action<? super org.gradle.api.NamedDomainObjectContainer<BuildType>>");

    assertTrue(extractor.isClosure());
    assertEquals("org.gradle.api.NamedDomainObjectContainer<BuildType>", extractor.getClosureType());

    assertTrue(extractor.hasNamedDomainObjectContainer());
    assertEquals("BuildType", extractor.getNamedDomainObject());
  }

  public void testActionParametersNdo() {
    AndroidDslContributor.ParametrizedTypeExtractor extractor =
      new AndroidDslContributor.ParametrizedTypeExtractor("org.gradle.api.Action<org.gradle.api.NamedDomainObjectContainer<Flavor>>");

    assertTrue(extractor.isClosure());
    assertEquals("org.gradle.api.NamedDomainObjectContainer<Flavor>", extractor.getClosureType());

    assertTrue(extractor.hasNamedDomainObjectContainer());
    assertEquals("Flavor", extractor.getNamedDomainObject());
  }

  public void testActionParametersPrimitiveClosure() {
    AndroidDslContributor.ParametrizedTypeExtractor extractor =
      new AndroidDslContributor.ParametrizedTypeExtractor("org.gradle.api.Action<String>");

    assertTrue(extractor.isClosure());
    assertEquals("String", extractor.getClosureType());

    assertFalse(extractor.hasNamedDomainObjectContainer());
    assertNull(extractor.getNamedDomainObject());
  }

  public void testFunction1ParametersWildcardNdo() {
    AndroidDslContributor.ParametrizedTypeExtractor extractor = new AndroidDslContributor.ParametrizedTypeExtractor(
      "kotlin.jvm.functions.Function1<? super org.gradle.api.NamedDomainObjectContainer<BuildType>,kotlin.Unit>");

    assertTrue(extractor.isClosure());
    assertEquals("org.gradle.api.NamedDomainObjectContainer<BuildType>", extractor.getClosureType());

    assertTrue(extractor.hasNamedDomainObjectContainer());
    assertEquals("BuildType", extractor.getNamedDomainObject());
  }

  public void testFunction1ParametersNdo() {
    AndroidDslContributor.ParametrizedTypeExtractor extractor =
      new AndroidDslContributor.ParametrizedTypeExtractor("kotlin.jvm.functions.Function1<org.gradle.api.NamedDomainObjectContainer<Flavor>,kotlin.Unit>");

    assertTrue(extractor.isClosure());
    assertEquals("org.gradle.api.NamedDomainObjectContainer<Flavor>", extractor.getClosureType());

    assertTrue(extractor.hasNamedDomainObjectContainer());
    assertEquals("Flavor", extractor.getNamedDomainObject());
  }

  public void testFunction1ParametersPrimitiveClosure() {
    AndroidDslContributor.ParametrizedTypeExtractor extractor =
      new AndroidDslContributor.ParametrizedTypeExtractor("kotlin.jvm.functions.Function1<String,kotlin.Unit>");

    assertTrue(extractor.isClosure());
    assertEquals("String", extractor.getClosureType());

    assertFalse(extractor.hasNamedDomainObjectContainer());
    assertNull(extractor.getNamedDomainObject());
  }

  public void testParametersNoClosure() {
    AndroidDslContributor.ParametrizedTypeExtractor extractor =
      new AndroidDslContributor.ParametrizedTypeExtractor("String");
    assertFalse(extractor.isClosure());

    assertFalse(extractor.hasNamedDomainObjectContainer());
    assertNull(extractor.getNamedDomainObject());
  }
}
