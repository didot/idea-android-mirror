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
package org.jetbrains.android.dom.lint;

import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.DefinesXml;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import java.util.List;

@DefinesXml
public interface LintDomElement extends DomElement {
  List<IssueDomElement> getIssues();

  // See LintXmlConfiguration#readConfig

  @Attribute("lintJar") GenericAttributeValue<String> getLintJar();
  @Attribute("lintJars") GenericAttributeValue<String> getLintJars();
  @Attribute("baseline") GenericAttributeValue<String> getBaseline();
  @Attribute("checkAllWarnings") GenericAttributeValue<Boolean> getCheckAllWarnings();
  @Attribute("ignoreWarnings") GenericAttributeValue<Boolean> getIgnoreWarnings();
  @Attribute("warningsAsErrors") GenericAttributeValue<Boolean> getWarningsAsErrors();
  @Attribute("fatalOnly") GenericAttributeValue<Boolean> getFatalOnly();
  @Attribute("checkTestSources") GenericAttributeValue<Boolean> getCheckTestSources();
  @Attribute("ignoreTestSources") GenericAttributeValue<Boolean> getIgnoreTestSources();
  @Attribute("checkGeneratedSources") GenericAttributeValue<Boolean> getCheckGeneratedSources();
  @Attribute("checkDependencies") GenericAttributeValue<Boolean> getCheckDependencies();
  @Attribute("explainIssues") GenericAttributeValue<Boolean> getExplainIssues();
  @Attribute("removeFixedBaselineIssues") GenericAttributeValue<Boolean> getRemoveFixedBaselineIssues();
  @Attribute("abortOnError") GenericAttributeValue<Boolean> getAbortOnError();
}
