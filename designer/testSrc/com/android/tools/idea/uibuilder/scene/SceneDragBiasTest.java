/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene;

import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

/**
 * Test dragging a widget connected bottom-right
 */
public class SceneDragBiasTest extends SceneTest {

  @Override
  @NotNull
  public ModelBuilder createModel() {
    ModelBuilder builder = model("constraint.xml",
                                 component(CONSTRAINT_LAYOUT)
                                   .id("@id/root")
                                   .withBounds(0, 0, 1000, 1000)
                                   .width("1000dp")
                                   .height("1000dp")
                                   .withAttribute("android:padding", "20dp")
                                   .children(
                                     component(TEXT_VIEW)
                                       .id("@id/button")
                                       .withBounds(450, 490, 100, 20)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("app:layout_constraintLeft_toLeftOf", "parent")
                                       .withAttribute("app:layout_constraintRight_toRightOf", "parent")
                                       .withAttribute("app:layout_constraintTop_toTopOf", "parent")
                                       .withAttribute("app:layout_constraintBottom_toBottomOf", "parent")
                                   ));
    return builder;
  }

  public void testDragLeft() {
    myInteraction.mouseDown("button");
    myInteraction.mouseRelease(100, 500);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    app:layout_constraintLeft_toLeftOf=\"parent\"\n" +
                 "    app:layout_constraintRight_toRightOf=\"parent\"\n" +
                 "    app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "    app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "      app:layout_constraintHorizontal_bias=\"0.055\"\n" +
                 "      app:layout_constraintVertical_bias=\"0.5\" />");
  }

  public void testDragTop() {
    myInteraction.mouseDown("button");
    myInteraction.mouseRelease(500, 100);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    app:layout_constraintLeft_toLeftOf=\"parent\"\n" +
                 "    app:layout_constraintRight_toRightOf=\"parent\"\n" +
                 "    app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "    app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "      app:layout_constraintHorizontal_bias=\"0.5\"\n" +
                 "      app:layout_constraintVertical_bias=\"0.091\" />");
  }

  public void testDragTopLeft() {
    myInteraction.mouseDown("button");
    myInteraction.mouseRelease(200, 200);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    app:layout_constraintLeft_toLeftOf=\"parent\"\n" +
                 "    app:layout_constraintRight_toRightOf=\"parent\"\n" +
                 "    app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "    app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "      app:layout_constraintHorizontal_bias=\"0.166\"\n" +
                 "      app:layout_constraintVertical_bias=\"0.193\" />");
  }
}
