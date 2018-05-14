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
package org.jetbrains.android.dom.motion;

import com.intellij.util.xml.DefinesXml;
import com.intellij.util.xml.NameStrategy;
import org.jetbrains.android.dom.Styleable;

import java.util.List;

@DefinesXml
@NameStrategy(PascalNameStrategy.class)
@Styleable(value = "KeyFrameSet", packageName = "android.support.constraint")
public interface KeyFrameSet extends MotionElement {
  List<KeyAttributeSet> getKeyAttributeSets();
  List<KeyCycle> getKeyCycles();
  List<KeyPositionPath> getKeyPositionPaths();
  List<KeyPositionCartesian> getKeyPositionCartesians();
  List<KeyPositionAbsolute> getKeyPositionAbsolutes();
}
