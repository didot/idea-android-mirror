/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.relative

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.util.NlTreeDumper

import com.android.SdkConstants.*
import com.android.tools.idea.uibuilder.LayoutTestUtilities.mockViewWithBaseline
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder
import com.android.tools.idea.uibuilder.scene.SceneTest
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget

class RelativeLayoutHandlerKtTest : SceneTest() {

  override fun setUp() {
    // Trigger this flag to use RelativeLayoutHandlerKt
    StudioFlags.NELE_TARGET_RELATIVE.override(true)

    super.setUp()
  }

  override fun tearDown() {
    super.tearDown()

    StudioFlags.NELE_TARGET_RELATIVE.clearOverride()
  }

  fun testDragBottomRight() {
    myInteraction.select("checkbox", true)
    myInteraction.mouseDown("checkbox", ResizeBaseTarget.Type.RIGHT_BOTTOM)
    myInteraction.mouseRelease(220f, 230f)
    myScreen.get("@id/checkbox")
        .expectXml("<CheckBox\n" +
            "    android:id=\"@id/checkbox\"\n" +
            "    android:layout_width=\"70dp\"\n" +
            "    android:layout_height=\"80dp\"\n" +
            "    android:layout_below=\"@id/button\"\n" +
            "    android:layout_toRightOf=\"@id/button\"\n" +
            "    android:layout_marginLeft=\"100dp\"\n" +
            "    android:layout_marginTop=\"100dp\"/>")
  }

  override fun createModel(): ModelBuilder {
    val builder = model("relative_kt.xml",
        component(RELATIVE_LAYOUT)
            .withBounds(0, 0, 1000, 1000)
            .matchParentWidth()
            .matchParentHeight()
            .children(
                component(BUTTON)
                    .withBounds(100, 100, 100, 100)
                    .id("@id/button")
                    .width("100dp")
                    .height("100dp")
                    .withAttribute("android:layout_alignParentTop", "true")
                    .withAttribute("android:layout_alignParentLeft", "true")
                    .withAttribute("android:layout_alignParentStart", "true")
                    .withAttribute("android:layout_marginTop", "100dp")
                    .withAttribute("android:layout_marginLeft", "100dp")
                    .withAttribute("android:layout_marginStart", "100dp"),

                component(CHECK_BOX)
                    .withBounds(300, 300, 20, 20)
                    .viewObject(mockViewWithBaseline(17))
                    .id("@id/checkbox")
                    .width("20dp")
                    .height("20dp")
                    .withAttribute("android:layout_below", "@id/button")
                    .withAttribute("android:layout_toRightOf", "@id/button")
                    .withAttribute("android:layout_marginLeft", "100dp")
                    .withAttribute("android:layout_marginTop", "100dp"),

                component(TEXT_VIEW)
                    .withBounds(400, 400, 100, 100)
                    .viewObject(mockViewWithBaseline(70))
                    .id("@id/textView")
                    .width("100dp")
                    .height("100dp")
                    .withAttribute("android:layout_below", "@id/checkbox")
                    .withAttribute("android:layout_toRightOf", "@id/checkbox")
                    .withAttribute("android:layout_marginLeft", "80dp")
                    .withAttribute("android:layout_marginTop", "80dp")
            ))
    val model = builder.build()
    assertEquals(1, model.components.size)
    assertEquals("NlComponent{tag=<RelativeLayout>, bounds=[0,0:1000x1000}\n" +
        "    NlComponent{tag=<Button>, bounds=[100,100:100x100}\n" +
        "    NlComponent{tag=<CheckBox>, bounds=[300,300:20x20}\n" +
        "    NlComponent{tag=<TextView>, bounds=[400,400:100x100}",
        NlTreeDumper.dumpTree(model.components))

    format(model.file)
    assertEquals("<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
        "    android:layout_width=\"match_parent\"\n" +
        "    android:layout_height=\"match_parent\">\n" +
        "\n" +
        "    <Button\n" +
        "        android:id=\"@id/button\"\n" +
        "        android:layout_width=\"100dp\"\n" +
        "        android:layout_height=\"100dp\"\n" +
        "        android:layout_alignParentTop=\"true\"\n" +
        "        android:layout_alignParentLeft=\"true\"\n" +
        "        android:layout_alignParentStart=\"true\"\n" +
        "        android:layout_marginTop=\"100dp\"\n" +
        "        android:layout_marginLeft=\"100dp\"\n" +
        "        android:layout_marginStart=\"100dp\" />\n" +
        "\n" +
        "    <CheckBox\n" +
        "        android:id=\"@id/checkbox\"\n" +
        "        android:layout_width=\"20dp\"\n" +
        "        android:layout_height=\"20dp\"\n" +
        "        android:layout_below=\"@id/button\"\n" +
        "        android:layout_toRightOf=\"@id/button\"\n" +
        "        android:layout_marginLeft=\"100dp\"\n" +
        "        android:layout_marginTop=\"100dp\" />\n" +
        "\n" +
        "    <TextView\n" +
        "        android:id=\"@id/textView\"\n" +
        "        android:layout_width=\"100dp\"\n" +
        "        android:layout_height=\"100dp\"\n" +
        "        android:layout_below=\"@id/checkbox\"\n" +
        "        android:layout_toRightOf=\"@id/checkbox\"\n" +
        "        android:layout_marginLeft=\"80dp\"\n" +
        "        android:layout_marginTop=\"80dp\" />\n" +
        "\n" +
        "</RelativeLayout>\n", model.file.text)
    return builder
  }
}
