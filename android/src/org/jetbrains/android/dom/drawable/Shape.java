/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.dom.drawable;

import com.intellij.util.xml.DefinesXml;
import java.util.List;
import org.jetbrains.android.dom.Styleable;

@DefinesXml
@Styleable("GradientDrawable")
public interface Shape extends DrawableDomElement {
  @Styleable("DrawableCorners")
  List<DrawableDomElement> getCornerses();

  @Styleable("GradientDrawableGradient")
  List<DrawableDomElement> getGradients();

  @Styleable("GradientDrawablePadding")
  List<DrawableDomElement> getPaddings();

  @Styleable("GradientDrawableSize")
  List<DrawableDomElement> getSizes();

  @Styleable("GradientDrawableSolid")
  List<DrawableDomElement> getSolids();

  @Styleable("GradientDrawableStroke")
  List<DrawableDomElement> getStrokes();
}
