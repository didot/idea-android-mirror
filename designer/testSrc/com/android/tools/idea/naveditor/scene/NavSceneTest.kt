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
package com.android.tools.idea.naveditor.scene

import com.android.tools.idea.avdmanager.DeviceManagerConnection
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.surface.InteractionManager
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.TestNlEditor
import com.android.tools.idea.naveditor.scene.targets.ScreenDragTarget
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavView
import com.google.common.collect.ImmutableList
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.psi.PsiDocumentManager
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * Tests for the nav editor Scene.
 */
class NavSceneTest : NavTestCase() {
  fun testDisplayList() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main") {
          action("action1", destination = "subnav")
          action("action2", destination = "activity")
        }
        navigation("subnav") {
          fragment("fragment2", layout = "activity_main2") {
            action("action3", destination = "activity")
          }
        }
        activity("activity")
      }
    }
    val scene = model.surface.scene!!

    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("subnav")!!, 380, 20)
    moveComponentTo(scene.getSceneComponent("activity")!!, 20, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,1050,928\n" +
      "DrawRectangle,1,490.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,491.0x401.0x74.5x126.0\n" +
      "DrawAction,REGULAR,490.0x400.0x76.5x128.0,580.0x389.0x70.0x30.0,NORMAL\n" +
      "DrawArrow,2,UP,612.0x423.0x6.0x5.0,b2a7a7a7\n" +
      "DrawAction,REGULAR,490.0x400.0x76.5x128.0,400.0x389.0x76.5x139.0,NORMAL\n" +
      "DrawArrow,2,UP,435.25x532.0x6.0x5.0,b2a7a7a7\n" +
      "DrawIcon,490.0x389.0x7.0x7.0,START_DESTINATION\n" +
      "DrawTruncatedText,3,fragment1,498.0x390.0x68.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawFilledRoundRectangle,1,580.0x400.0x70.0x19.0x6.0x6.0,fffafafa\n" +
      "DrawRoundRectangle,1,580.0x400.0x70.0x19.0x6.0x6.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,Nested Graph,580.0x400.0x70.0x19.0,ffa7a7a7,Default:1:9,true\n" +
      "DrawAction,EXIT_DESTINATION,580.0x400.0x70.0x19.0,400.0x389.0x76.5x139.0,NORMAL\n" +
      "DrawArrow,2,DOWN,435.25x380.0x6.0x5.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,subnav,580.0x390.0x70.0x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawFilledRoundRectangle,1,400.0x400.0x76.5x128.0x6.0x6.0,fffafafa\n" +
      "DrawRoundRectangle,1,400.0x400.0x76.5x128.0x6.0x6.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,404.0x404.0x68.5x111.0\n" +
      "DrawRectangle,5,404.0x404.0x68.5x111.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,Activity,400.0x515.0x76.5x13.0,ffa7a7a7,Default:1:9,true\n" +
      "DrawTruncatedText,3,activity,400.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testInclude() {
    val model = model("nav2.xml") {
      navigation("root") {
        fragment("fragment1") {
          action("action1", destination = "nav")
        }
        include("navigation")
      }
    }
    val scene = model.surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 140, 20)
    moveComponentTo(scene.getSceneComponent("nav")!!, 320, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "DrawAction,REGULAR,400.0x400.0x76.5x128.0,490.0x389.0x70.0x30.0,NORMAL\n" +
      "DrawArrow,2,UP,522.0x423.0x6.0x5.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,400.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawFilledRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,fffafafa\n" +
      "DrawRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,navigation.xml,490.0x400.0x70.0x19.0,ffa7a7a7,Default:1:9,true\n" +
      "DrawTruncatedText,3,nav,490.0x390.0x70.0x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testNegativePositions() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main")
        fragment("fragment2", layout = "activity_main")
        fragment("fragment3", layout = "activity_main")
      }
    }

    val scene = model.surface.scene!!
    val sceneManager = scene.sceneManager as NavSceneManager
    val component1 = scene.getSceneComponent("fragment1")!!
    component1.setPosition(-100, -200)
    val component2 = scene.getSceneComponent("fragment2")!!
    component2.setPosition(-300, 0)
    val component3 = scene.getSceneComponent("fragment3")!!
    component3.setPosition(200, 200)
    sceneManager.save(listOf(component1, component2, component3))

    val list = DisplayList()
    model.surface.sceneManager!!.update()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,1126,1128\n" +
      "DrawRectangle,1,500.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,501.0x401.0x74.5x126.0\n" +
      "DrawIcon,500.0x389.0x7.0x7.0,START_DESTINATION\n" +
      "DrawTruncatedText,3,fragment1,508.0x390.0x68.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,400.0x500.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,401.0x501.0x74.5x126.0\n" +
      "DrawTruncatedText,3,fragment2,400.0x490.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,650.0x600.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,651.0x601.0x74.5x126.0\n" +
      "DrawTruncatedText,3,fragment3,650.0x590.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testVeryPositivePositions() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main")
        fragment("fragment2", layout = "activity_main")
        fragment("fragment3", layout = "activity_main")
      }
    }

    val scene = model.surface.scene!!
    val sceneManager: NavSceneManager = scene.sceneManager as NavSceneManager
    val component1 = scene.getSceneComponent("fragment1")!!
    component1.setPosition(1900, 1800)
    val component2 = scene.getSceneComponent("fragment2")!!
    component2.setPosition(1700, 2000)
    val component3 = scene.getSceneComponent("fragment3")!!
    component3.setPosition(2200, 2200)
    sceneManager.save(listOf(component1, component2, component3))

    val list = DisplayList()
    model.surface.sceneManager!!.update()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,1126,1128\n" +
      "DrawRectangle,1,500.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,501.0x401.0x74.5x126.0\n" +
      "DrawIcon,500.0x389.0x7.0x7.0,START_DESTINATION\n" +
      "DrawTruncatedText,3,fragment1,508.0x390.0x68.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,400.0x500.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,401.0x501.0x74.5x126.0\n" +
      "DrawTruncatedText,3,fragment2,400.0x490.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,650.0x600.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,651.0x601.0x74.5x126.0\n" +
      "DrawTruncatedText,3,fragment3,650.0x590.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testAddComponent() {
    lateinit var root: NavModelBuilderUtil.NavigationComponentDescriptor

    val modelBuilder = modelBuilder("nav.xml") {
      navigation("root", startDestination = "fragment2") {
        fragment("fragment1", layout = "activity_main") {
          action("action1", destination = "fragment2")
        }
        fragment("fragment2", layout = "activity_main2")
      }.also { root = it }
    }
    val model = modelBuilder.build()

    val scene = model.surface.scene!!

    val list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))

    root.fragment("fragment3")
    modelBuilder.updateModel(model)
    model.notifyModified(NlModel.ChangeType.EDIT)
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment2")!!, 20, 20)
    moveComponentTo(scene.getSceneComponent("fragment3")!!, 380, 20)
    scene.sceneManager.layout(false)

    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,1056,928\n" +
      "DrawRectangle,1,490.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,491.0x401.0x74.5x126.0\n" +
      "DrawAction,REGULAR,490.0x400.0x76.5x128.0,400.0x389.0x78.5x139.0,NORMAL\n" +
      "DrawArrow,2,UP,436.25x532.0x6.0x5.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,490.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "DrawIcon,400.0x389.0x7.0x7.0,START_DESTINATION\n" +
      "DrawTruncatedText,3,fragment2,408.0x390.0x68.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,580.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,581.0x401.0x74.5x126.0\n" +
      "DrawTruncatedText,3,fragment3,580.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testRemoveComponent() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment2") {
        fragment("fragment1", layout = "activity_main") {
          action("action1", destination = "fragment2")
        }
        fragment("fragment2", layout = "activity_main2")
      }
    }
    val editor = TestNlEditor(model.virtualFile, project)

    val scene = model.surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment2")!!, 20, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    model.delete(listOf(model.find("fragment2")!!))

    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    list.clear()
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,876,928\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "DrawTruncatedText,3,fragment1,400.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawLine,2,477.5x464.0,484.5x464.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,484.5x461.0x5.0x6.0,b2a7a7a7\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )

    val undoManager = UndoManager.getInstance(project)
    undoManager.undo(editor)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    model.notifyModified(NlModel.ChangeType.EDIT)
    model.surface.sceneManager!!.update()
    list.clear()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,966,928\n" +
      "DrawRectangle,1,490.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,491.0x401.0x74.5x126.0\n" +
      "DrawAction,REGULAR,490.0x400.0x76.5x128.0,400.0x389.0x78.5x139.0,NORMAL\n" +
      "DrawArrow,2,UP,436.25x532.0x6.0x5.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,490.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "DrawIcon,400.0x389.0x7.0x7.0,START_DESTINATION\n" +
      "DrawTruncatedText,3,fragment2,408.0x390.0x68.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testSubflow() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment2") {
        fragment("fragment1") {
          action("action1", destination = "fragment2")
        }
        fragment("fragment2", layout = "activity_main2") {
          action("action2", destination = "fragment3")
        }
        navigation("subnav") {
          fragment("fragment3") {
            action("action3", destination = "fragment4")
          }
          fragment("fragment4") {
            action("action4", destination = "fragment1")
          }
        }
      }
    }

    val scene = model.surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment2")!!, 380, 20)
    moveComponentTo(scene.getSceneComponent("subnav")!!, 20, 20)
    scene.sceneManager.layout(false)

    val surface = model.surface as NavDesignSurface

    val view = NavView(surface, scene.sceneManager)
    scene.layout(0, SceneContext.get(view))

    val list = DisplayList()
    scene.buildDisplayList(list, 0, view)

    assertEquals(
      "Clip,0,0,1056,928\n" +
      "DrawRectangle,1,490.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,491.0x401.0x74.5x126.0\n" +
      "DrawAction,REGULAR,490.0x400.0x76.5x128.0,580.0x389.0x78.5x139.0,NORMAL\n" +
      "DrawArrow,2,RIGHT,571.0x455.5x5.0x6.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,490.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,580.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,581.0x401.0x74.5x126.0\n" +
      "DrawIcon,580.0x389.0x7.0x7.0,START_DESTINATION\n" +
      "DrawTruncatedText,3,fragment2,588.0x390.0x68.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawLine,2,657.5x464.0,664.5x464.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,664.5x461.0x5.0x6.0,b2a7a7a7\n" +
      "\n" +
      "DrawFilledRoundRectangle,1,400.0x400.0x70.0x19.0x6.0x6.0,fffafafa\n" +
      "DrawRoundRectangle,1,400.0x400.0x70.0x19.0x6.0x6.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,Nested Graph,400.0x400.0x70.0x19.0,ffa7a7a7,Default:1:9,true\n" +
      "DrawAction,EXIT_DESTINATION,400.0x400.0x70.0x19.0,490.0x389.0x78.5x139.0,NORMAL\n" +
      "DrawArrow,2,RIGHT,481.0x455.5x5.0x6.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,subnav,400.0x390.0x70.0x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )

    list.clear()

    `when`<NlComponent>(surface.currentNavigation).then { model.find("subnav")!! }
    scene.sceneManager.update()
    moveComponentTo(scene.getSceneComponent("fragment3")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment4")!!, 20, 20)
    scene.sceneManager.layout(false)


    scene.layout(0, SceneContext.get(view))
    scene.buildDisplayList(list, 0, view)
    assertEquals(
      "Clip,0,0,966,928\n" +
      "DrawRectangle,1,490.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,491.0x401.0x74.5x126.0\n" +
      "DrawAction,REGULAR,490.0x400.0x76.5x128.0,400.0x389.0x78.5x139.0,NORMAL\n" +
      "DrawArrow,2,UP,436.25x532.0x6.0x5.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment3,490.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "DrawTruncatedText,3,fragment4,400.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawLine,2,477.5x464.0,484.5x464.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,484.5x461.0x5.0x6.0,b2a7a7a7\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testNonexistentLayout() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1", layout = "activity_nonexistent")
      }
    }
    val scene = model.surface.scene!!

    val list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,876,928\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "DrawTruncatedText,3,fragment1,400.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testSelectedNlComponentSelectedInScene() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main") {
          action("action1", destination = "subnav")
          action("action2", destination = "activity")
        }
      }
    }
    val surface = model.surface
    val rootComponent = model.components[0]
    object : WriteCommandAction<Any?>(project, "Add") {
      override fun run(result: Result<Any?>) {
        val tag = rootComponent.tag.createChildTag("fragment", null, null, true)
        val newComponent = surface.model!!.createComponent(surface, tag, rootComponent, null, InsertType.CREATE)
        surface.selectionModel.setSelection(ImmutableList.of(newComponent))
        newComponent.assignId("myId")
      }
    }.execute()
    val manager = NavSceneManager(model, model.surface as NavDesignSurface)
    manager.update()
    val scene = manager.scene

    assertTrue(scene.getSceneComponent("myId")!!.isSelected)
  }

  fun testSelfAction() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main") {
          action("action1", destination = "fragment1")
        }
        navigation("nav1") {
          action("action2", destination = "nav1")
        }
      }
    }
    val scene = model.surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 140, 20)
    moveComponentTo(scene.getSceneComponent("nav1")!!, 320, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "DrawArrow,2,UP,459.5x532.0x6.0x5.0,b2a7a7a7\n" +
      "DrawSelfAction,476.5x464.0,462.5x536.0,b2a7a7a7\n" +
      "DrawIcon,400.0x389.0x7.0x7.0,START_DESTINATION\n" +
      "DrawTruncatedText,3,fragment1,408.0x390.0x68.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawFilledRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,fffafafa\n" +
      "DrawRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,Nested Graph,490.0x400.0x70.0x19.0,ffa7a7a7,Default:1:9,true\n" +
      "DrawArrow,2,UP,541.0x423.0x6.0x5.0,b2a7a7a7\n" +
      "DrawSelfAction,560.0x409.5,544.0x427.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,nav1,490.0x390.0x70.0x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testDeepLinks() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main") {
          deeplink("https://www.android.com/")
        }
      }
    }
    val scene = model.surface.scene!!

    val list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,876,928\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "DrawIcon,400.0x389.0x7.0x7.0,START_DESTINATION\n" +
      "DrawIcon,469.5x389.0x7.0x7.0,DEEPLINK\n" +
      "DrawTruncatedText,3,fragment1,408.0x390.0x60.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testSelectedComponent() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        action("a1", destination = "fragment1")
        fragment("fragment1")
        navigation("subnav")
      }
    }
    val scene = model.surface.scene!!

    // Selecting global nav brings it to the front
    model.surface.selectionModel.setSelection(ImmutableList.of(model.find("a1")!!))

    moveComponentTo(scene.getSceneComponent("fragment1")!!, 140, 20)
    moveComponentTo(scene.getSceneComponent("subnav")!!, 320, 20)

    scene.sceneManager.layout(false)
    var list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    val view = NavView(model.surface as NavDesignSurface, scene.sceneManager)
    scene.buildDisplayList(list, 0, view)
    val context = SceneContext.get(view)

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawFilledRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,fffafafa\n" +
      "DrawRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,Nested Graph,490.0x400.0x70.0x19.0,ffa7a7a7,Default:1:9,true\n" +
      "DrawTruncatedText,3,subnav,490.0x390.0x70.0x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawLine,2,387.0x464.0,391.0x464.0,ff1886f7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391.0x461.0x5.0x6.0,ff1886f7\n" +
      "DrawTruncatedText,3,fragment1,408.0x390.0x68.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawIcon,400.0x389.0x7.0x7.0,START_DESTINATION\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList(context)
    )

    // now "subnav" is in the front
    val subnav = model.find("subnav")!!
    model.surface.selectionModel.setSelection(ImmutableList.of(subnav))
    list.clear()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, view)

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawLine,2,387.0x464.0,391.0x464.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391.0x461.0x5.0x6.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,408.0x390.0x68.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawIcon,400.0x389.0x7.0x7.0,START_DESTINATION\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "\n" +
      "DrawFilledRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,fffafafa\n" +
      "DrawRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,ff1886f7,2.0\n" +
      "DrawTruncatedText,3,Nested Graph,490.0x400.0x70.0x19.0,ff1886f7,Default:1:9,true\n" +
      "DrawTruncatedText,3,subnav,490.0x390.0x70.0x5.0,ff656565,Default:0:9,false\n" +
      "DrawFilledCircle,6,560.0x409.5,fff5f5f5,0.0:3.5:63\n" +
      "DrawCircle,7,560.0x409.5,ff1886f7,2,0.0:2.5:63\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList(context)
    )

    // test multi select
    model.surface.selectionModel.setSelection(ImmutableList.of(model.find("fragment1")!!, subnav))

    list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawRoundRectangle,1,398.0x398.0x80.5x132.0x2.0x2.0,ff1886f7,2.0\n" +
      "DrawLine,2,387.0x464.0,391.0x464.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391.0x461.0x5.0x6.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,408.0x390.0x68.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawIcon,400.0x389.0x7.0x7.0,START_DESTINATION\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "\n" +
      "DrawFilledRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,fffafafa\n" +
      "DrawRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,ff1886f7,2.0\n" +
      "DrawTruncatedText,3,Nested Graph,490.0x400.0x70.0x19.0,ff1886f7,Default:1:9,true\n" +
      "DrawTruncatedText,3,subnav,490.0x390.0x70.0x5.0,ff656565,Default:0:9,false\n" +
      "DrawFilledCircle,6,560.0x409.5,fff5f5f5,3.5:0.0:63\n" +
      "DrawCircle,7,560.0x409.5,ff1886f7,2,2.5:0.0:63\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList(context)
    )
  }

  fun testHoveredComponent() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1") {
          action("a1", destination = "subnav")
        }
        navigation("subnav")
        action("a2", destination = "fragment1")
      }
    }

    val scene = model.surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 140, 20)
    moveComponentTo(scene.getSceneComponent("subnav")!!, 320, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    val transform = SceneContext.get(model.surface.currentSceneView)
    scene.layout(0, transform)
    scene.mouseHover(transform, 150, 30)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawRoundRectangle,1,398.0x398.0x80.5x132.0x2.0x2.0,ffa7a7a7,2.0\n" +
      "DrawAction,REGULAR,400.0x400.0x76.5x128.0,490.0x389.0x70.0x30.0,NORMAL\n" +
      "DrawArrow,2,UP,522.0x423.0x6.0x5.0,b2a7a7a7\n" +
      "DrawLine,2,387.0x464.0,391.0x464.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391.0x461.0x5.0x6.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,400.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "DrawFilledCircle,6,478.5x464.0,fff5f5f5,0.0:3.5:63\n" +
      "DrawCircle,7,478.5x464.0,ffa7a7a7,2,0.0:2.5:63\n" +
      "\n" +
      "DrawFilledRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,fffafafa\n" +
      "DrawRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,Nested Graph,490.0x400.0x70.0x19.0,ffa7a7a7,Default:1:9,true\n" +
      "DrawTruncatedText,3,subnav,490.0x390.0x70.0x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList(transform)
    )

    scene.mouseHover(transform, 552, 440)
    list.clear()
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawAction,REGULAR,400.0x400.0x76.5x128.0,490.0x389.0x70.0x30.0,NORMAL\n" +
      "DrawArrow,2,UP,522.0x423.0x6.0x5.0,b2a7a7a7\n" +
      "DrawLine,2,387.0x464.0,391.0x464.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391.0x461.0x5.0x6.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,400.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "DrawFilledCircle,6,478.5x464.0,fff5f5f5,3.5:0.0:63\n" +
      "DrawCircle,7,478.5x464.0,ffa7a7a7,2,2.5:0.0:63\n" +
      "\n" +
      "DrawFilledRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,fffafafa\n" +
      "DrawRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,Nested Graph,490.0x400.0x70.0x19.0,ffa7a7a7,Default:1:9,true\n" +
      "DrawTruncatedText,3,subnav,490.0x390.0x70.0x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList(transform)
    )

    scene.mouseHover(transform, 120, 148)
    list.clear()
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawAction,REGULAR,400.0x400.0x76.5x128.0,490.0x389.0x70.0x30.0,NORMAL\n" +
      "DrawArrow,2,UP,522.0x423.0x6.0x5.0,b2a7a7a7\n" +
      "DrawLine,2,387.0x464.0,391.0x464.0,ffa7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391.0x461.0x5.0x6.0,ffa7a7a7\n" +
      "DrawTruncatedText,3,fragment1,400.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "\n" +
      "DrawFilledRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,fffafafa\n" +
      "DrawRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,Nested Graph,490.0x400.0x70.0x19.0,ffa7a7a7,Default:1:9,true\n" +
      "DrawTruncatedText,3,subnav,490.0x390.0x70.0x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList(transform)
    )
  }

  fun testHoveredHandle() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
      }
    }

    val scene = model.surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 20, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    val transform = SceneContext.get(model.surface.currentSceneView)
    scene.layout(0, transform)

    // If rectangle extends from (20, 20) to (173, 276), then the handle should be at (173, 148)
    // Hover over a point to the right of that so that we're over the handle but not the rectangle
    scene.mouseHover(transform, 177, 148)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,876,928\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawRoundRectangle,1,398.0x398.0x80.5x132.0x2.0x2.0,ffa7a7a7,2.0\n" +
      "DrawTruncatedText,3,fragment1,400.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "DrawFilledCircle,6,478.5x464.0,fff5f5f5,0.0:5.5:100\n" +
      "DrawCircle,7,478.5x464.0,ffa7a7a7,2,0.0:4.0:100\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList(transform)
    )
  }

  fun testHoverDuringDrag() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1") {
          action("a1", destination = "subnav")
        }
        navigation("subnav")
        action("a2", destination = "fragment1")
      }
    }

    val surface = model.surface
    val scene = surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 140, 20)
    moveComponentTo(scene.getSceneComponent("subnav")!!, 320, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    val sceneContext = SceneContext.get(surface.currentSceneView)

    scene.layout(0, sceneContext)

    val interactionManager = mock(InteractionManager::class.java)
    `when`(interactionManager.isInteractionInProgress).thenReturn(true)
    `when`(surface.interactionManager).thenReturn(interactionManager)

    val drawRect1 = scene.getSceneComponent("fragment1")!!
    scene.mouseDown(sceneContext, drawRect1.drawX + drawRect1.drawWidth, drawRect1.centerY)

    val drawRect2 = scene.getSceneComponent("subnav")!!
    scene.mouseDrag(sceneContext, drawRect2.centerX, drawRect2.centerY)

    scene.buildDisplayList(list, 0, NavView(surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
        "Clip,0,0,960,928\n" +
        "DrawFilledRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,fffafafa\n" +
        "DrawRoundRectangle,1,490.0x400.0x70.0x19.0x6.0x6.0,ff1886f7,2.0\n" +
        "DrawTruncatedText,3,Nested Graph,490.0x400.0x70.0x19.0,ffa7a7a7,Default:1:9,true\n" +
        "DrawTruncatedText,3,subnav,490.0x390.0x70.0x5.0,ff656565,Default:0:9,false\n" +
        "\n" +
        "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
        "DrawRoundRectangle,1,398.0x398.0x80.5x132.0x2.0x2.0,ff1886f7,2.0\n" +
        "DrawAction,REGULAR,400.0x400.0x76.5x128.0,490.0x389.0x70.0x30.0,NORMAL\n" +
        "DrawArrow,2,UP,522.0x423.0x6.0x5.0,b2a7a7a7\n" +
        "DrawLine,2,387.0x464.0,391.0x464.0,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,391.0x461.0x5.0x6.0,b2a7a7a7\n" +
        "DrawTruncatedText,3,fragment1,400.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
        "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
        "DrawFilledCircle,6,478.5x464.0,fff5f5f5,0.0:3.5:63\n" +
        "DrawFilledCircle,7,478.5x464.0,ff1886f7,2.5:2.5:0\n" +
        "DrawActionHandleDrag,478,464\n" +
        "\n" +
        "UNClip\n", list.generateSortedDisplayList(sceneContext)
    )

  }

  // TODO: this should test the different "Simulated Layouts", once that's implemented.
  fun disabledTestDevices() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
      }
    }
    val list = DisplayList()
    val surface = model.surface
    val scene = surface.scene!!
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,977,1028\n" +
      "DrawRoundRectangle,450x450x77x128,FRAMES,1,0\n" +
      "DrawActionHandle,527,514,0,0,FRAMES,0\n" +
      "DrawScreenLabel,450,445,fragment1\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )

    list.clear()
    model.configuration
      .setDevice(DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevice("wear_square", "Google"), false)
    surface.sceneManager!!.update()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,914,914\n" +
      "DrawRoundRectangle,425x425x64x64,FRAMES,1,0\n" +
      "DrawActionHandle,489,456,0,0,FRAMES,0\n" +
      "DrawTruncatedText,3,fragment1,425x415x64x5,SUBDUED_TEXT,0,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )

    list.clear()
    model.configuration.setDevice(DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevice("tv_1080p", "Google"), false)
    surface.sceneManager!!.update()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,1028,972\n" +
      "DrawRoundRectangle,450x450x128x72,FRAMES,1,0\n" +
      "DrawActionHandle,578,486,0,0,FRAMES,0\n" +
      "DrawTruncatedText,3,fragment1,450x440x128x5,SUBDUED_TEXT,0,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testGlobalActions() {
    val model = model("nav.xml") {
      navigation("root") {
        action("action1", destination = "fragment1")
        action("action2", destination = "fragment2")
        action("action3", destination = "fragment2")
        action("action4", destination = "fragment3")
        action("action5", destination = "fragment3")
        action("action6", destination = "fragment3")
        action("action7", destination = "invalid")
        fragment("fragment1")
        fragment("fragment2") {
          action("action8", destination = "fragment3")
          action("action9", destination = "fragment2")
        }
        fragment("fragment3")
      }
    }

    val list = DisplayList()
    val surface = model.surface
    val scene = surface.scene!!

    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment2")!!, 380, 20)
    moveComponentTo(scene.getSceneComponent("fragment3")!!, 20, 20)
    scene.sceneManager.layout(false)

    scene.layout(0, SceneContext.get())
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,1056,928\n" +
      "DrawRectangle,1,490.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,491.0x401.0x74.5x126.0\n" +
      "DrawTruncatedText,3,fragment1,490.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawLine,2,477.0x464.0,481.0x464.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,481.0x461.0x5.0x6.0,b2a7a7a7\n" +
      "\n" +
      "DrawRectangle,1,580.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,581.0x401.0x74.5x126.0\n" +
      "DrawAction,REGULAR,580.0x400.0x76.5x128.0,400.0x389.0x78.5x139.0,NORMAL\n" +
      "DrawArrow,2,UP,436.25x532.0x6.0x5.0,b2a7a7a7\n" +
      "DrawArrow,2,UP,639.5x532.0x6.0x5.0,b2a7a7a7\n" +
      "DrawSelfAction,656.5x464.0,642.5x536.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment2,580.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawLine,2,567.0x455.0,571.0x455.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,571.0x452.0x5.0x6.0,b2a7a7a7\n" +
      "DrawLine,2,567.0x464.0,571.0x464.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,571.0x461.0x5.0x6.0,b2a7a7a7\n" +
      "\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "DrawTruncatedText,3,fragment3,400.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawLine,2,387.0x446.0,391.0x446.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391.0x443.0x5.0x6.0,b2a7a7a7\n" +
      "DrawLine,2,387.0x455.0,391.0x455.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391.0x452.0x5.0x6.0,b2a7a7a7\n" +
      "DrawLine,2,387.0x473.0,391.0x473.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391.0x470.0x5.0x6.0,b2a7a7a7\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testPopToDestination() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
        fragment("fragment2") {
          action("a", popUpTo = "fragment1")
        }
      }
    }
    val list = DisplayList()
    val surface = model.surface
    val scene = surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment2")!!, 20, 20)
    scene.sceneManager.layout(false)

    scene.layout(0, SceneContext.get())
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,966,928\n" +
      "DrawRectangle,1,490.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,491.0x401.0x74.5x126.0\n" +
      "DrawTruncatedText,3,fragment1,490.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "DrawAction,REGULAR,400.0x400.0x76.5x128.0,490.0x389.0x78.5x139.0,NORMAL\n" +
      "DrawArrow,2,RIGHT,481.0x455.5x5.0x6.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment2,400.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testExitActions() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1")
        navigation("nav1") {
          fragment("fragment2") {
            action("action1", destination = "fragment1")
          }
          fragment("fragment3") {
            action("action2", destination = "fragment1")
            action("action3", destination = "fragment1")
          }
          fragment("fragment4") {
            action("action4", destination = "fragment1")
            action("action5", destination = "fragment1")
            action("action6", destination = "fragment1")
            action("action7", destination = "fragment2")
          }
          fragment("fragment5") {
            action("action8", destination = "fragment1")
            action("action9", destination = "fragment5")
          }
          navigation("nav2") {
            action("action8", destination = "root")
          }
        }
      }
    }

    val surface = model.surface as NavDesignSurface
    `when`<NlComponent>(surface.currentNavigation).then { model.find("nav1")!! }

    val scene = surface.scene!!
    scene.sceneManager.update()

    moveComponentTo(scene.getSceneComponent("fragment2")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment3")!!, 380, 20)
    moveComponentTo(scene.getSceneComponent("fragment4")!!, 20, 260)
    moveComponentTo(scene.getSceneComponent("fragment5")!!, 200, 320)
    moveComponentTo(scene.getSceneComponent("nav2")!!, 20, 20)
    scene.sceneManager.layout(false)

    val view = NavView(surface, surface.sceneManager!!)
    scene.layout(0, SceneContext.get(view))

    val list = DisplayList()
    scene.buildDisplayList(list, 0, view)

    assertEquals(
      "Clip,0,0,1056,1078\n" +
      "DrawRectangle,1,490.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,491.0x401.0x74.5x126.0\n" +
      "DrawTruncatedText,3,fragment2,490.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawLine,2,567.5x464.0,574.5x464.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,574.5x461.0x5.0x6.0,b2a7a7a7\n" +
      "\n" +
      "DrawRectangle,1,580.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,581.0x401.0x74.5x126.0\n" +
      "DrawTruncatedText,3,fragment3,580.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawLine,2,657.5x455.0,664.5x455.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,664.5x452.0x5.0x6.0,b2a7a7a7\n" +
      "DrawLine,2,657.5x464.0,664.5x464.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,664.5x461.0x5.0x6.0,b2a7a7a7\n" +
      "\n" +
      "DrawRectangle,1,400.0x520.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,401.0x521.0x74.5x126.0\n" +
      "DrawAction,REGULAR,400.0x520.0x76.5x128.0,490.0x389.0x78.5x139.0,NORMAL\n" +
      "DrawArrow,2,UP,526.25x532.0x6.0x5.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment4,400.0x510.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawLine,2,477.5x566.0,484.5x566.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,484.5x563.0x5.0x6.0,b2a7a7a7\n" +
      "DrawLine,2,477.5x575.0,484.5x575.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,484.5x572.0x5.0x6.0,b2a7a7a7\n" +
      "DrawLine,2,477.5x593.0,484.5x593.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,484.5x590.0x5.0x6.0,b2a7a7a7\n" +
      "\n" +
      "DrawRectangle,1,490.0x550.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,491.0x551.0x74.5x126.0\n" +
      "DrawArrow,2,UP,549.5x682.0x6.0x5.0,b2a7a7a7\n" +
      "DrawSelfAction,566.5x614.0,552.5x686.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment5,490.0x540.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "DrawLine,2,567.5x605.0,574.5x605.0,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,574.5x602.0x5.0x6.0,b2a7a7a7\n" +
      "\n" +
      "DrawFilledRoundRectangle,1,400.0x400.0x70.0x19.0x6.0x6.0,fffafafa\n" +
      "DrawRoundRectangle,1,400.0x400.0x70.0x19.0x6.0x6.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,Nested Graph,400.0x400.0x70.0x19.0,ffa7a7a7,Default:1:9,true\n" +
      "DrawTruncatedText,3,nav2,400.0x390.0x70.0x5.0,ff656565,Default:0:9,false\n" +
      "DrawLine,2,471.0x409.5,478.0x409.5,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,478.0x406.5x5.0x6.0,b2a7a7a7\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testHoverMarksComponent() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        fragment("fragment2")
      }
    }

    val scene = model.surface.scene!!
    val view = model.surface.currentSceneView!!
    `when`(view.scale).thenReturn(1.0)
    val transform = SceneContext.get(view)!!
    val fragment1 = scene.getSceneComponent("fragment1")!!
    fragment1.setPosition(100, 100)
    fragment1.setSize(100, 100, false)
    fragment1.layout(transform, 0)
    val fragment2 = scene.getSceneComponent("fragment2")!!
    fragment2.setPosition(1000, 1000)
    fragment2.setSize(100, 100, false)
    fragment2.layout(transform, 0)

    assertEquals(SceneComponent.DrawState.NORMAL, fragment1.drawState)
    assertEquals(SceneComponent.DrawState.NORMAL, fragment2.drawState)
    var version = scene.displayListVersion

    scene.mouseHover(transform, 150, 150)
    assertEquals(SceneComponent.DrawState.HOVER, fragment1.drawState)
    assertEquals(SceneComponent.DrawState.NORMAL, fragment2.drawState)
    assertTrue(version < scene.displayListVersion)
    version = scene.displayListVersion

    scene.mouseHover(transform, 1050, 1050)
    assertEquals(SceneComponent.DrawState.NORMAL, fragment1.drawState)
    assertEquals(SceneComponent.DrawState.HOVER, fragment2.drawState)
    assertTrue(version < scene.displayListVersion)
    version = scene.displayListVersion

    scene.mouseHover(transform, 0, 0)
    assertEquals(SceneComponent.DrawState.NORMAL, fragment1.drawState)
    assertEquals(SceneComponent.DrawState.NORMAL, fragment2.drawState)
    assertTrue(version < scene.displayListVersion)
  }

  fun testRegularActions() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1") {
          action("a1", destination = "fragment2")
          action("a2", destination = "nav1")
        }
        fragment("fragment2")
        navigation("nav1") {
          action("a3", destination = "fragment1")
          action("a4", destination = "nav2")
        }
        navigation("nav2")
      }
    }

    val list = DisplayList()
    val surface = model.surface
    val scene = surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment2")!!, 380, 20)
    moveComponentTo(scene.getSceneComponent("nav1")!!, 20, 80)
    moveComponentTo(scene.getSceneComponent("nav2")!!, 20, 20)
    scene.sceneManager.layout(false)

    scene.layout(0, SceneContext.get())
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,1056,928\n" +
      "DrawRectangle,1,490.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,491.0x401.0x74.5x126.0\n" +
      "DrawAction,REGULAR,490.0x400.0x76.5x128.0,580.0x389.0x78.5x139.0,NORMAL\n" +
      "DrawArrow,2,RIGHT,571.0x455.5x5.0x6.0,b2a7a7a7\n" +
      "DrawAction,REGULAR,490.0x400.0x76.5x128.0,400.0x419.0x70.0x30.0,NORMAL\n" +
      "DrawArrow,2,UP,432.0x453.0x6.0x5.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,490.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,580.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,581.0x401.0x74.5x126.0\n" +
      "DrawTruncatedText,3,fragment2,580.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawFilledRoundRectangle,1,400.0x430.0x70.0x19.0x6.0x6.0,fffafafa\n" +
      "DrawRoundRectangle,1,400.0x430.0x70.0x19.0x6.0x6.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,Nested Graph,400.0x430.0x70.0x19.0,ffa7a7a7,Default:1:9,true\n" +
      "DrawAction,REGULAR,400.0x430.0x70.0x19.0,490.0x389.0x78.5x139.0,NORMAL\n" +
      "DrawArrow,2,RIGHT,481.0x455.5x5.0x6.0,b2a7a7a7\n" +
      "DrawAction,REGULAR,400.0x430.0x70.0x19.0,400.0x389.0x70.0x30.0,NORMAL\n" +
      "DrawArrow,2,UP,432.0x423.0x6.0x5.0,b2a7a7a7\n" +
      "DrawTruncatedText,3,nav1,400.0x420.0x70.0x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawFilledRoundRectangle,1,400.0x400.0x70.0x19.0x6.0x6.0,fffafafa\n" +
      "DrawRoundRectangle,1,400.0x400.0x70.0x19.0x6.0x6.0,ffa7a7a7,1.0\n" +
      "DrawTruncatedText,3,Nested Graph,400.0x400.0x70.0x19.0,ffa7a7a7,Default:1:9,true\n" +
      "DrawTruncatedText,3,nav2,400.0x390.0x70.0x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testEmptyDesigner() {
    var root: NavModelBuilderUtil.NavigationComponentDescriptor? = null

    val modelBuilder = modelBuilder("nav.xml") {
      navigation("root") {
        action("action1", destination = "root")
      }.also { root = it }
    }

    val model = modelBuilder.build()

    val surface = model.surface as NavDesignSurface
    val scene = surface.scene!!
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))

    val list = DisplayList()
    val sceneManager = scene.sceneManager as NavSceneManager
    scene.buildDisplayList(list, 0, NavView(surface, sceneManager))

    assertEquals(
      "DrawEmptyDesigner,130x258\n", list.serialize()
    )
    assertTrue(sceneManager.isEmpty)

    root?.fragment("fragment1")

    modelBuilder.updateModel(model)
    model.notifyModified(NlModel.ChangeType.EDIT)
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    list.clear()
    scene.buildDisplayList(list, 0, NavView(surface, sceneManager))

    assertEquals(
      "Clip,0,0,876,928\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "DrawTruncatedText,3,fragment1,400.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
    assertFalse(sceneManager.isEmpty)

    model.delete(listOf(model.find("fragment1")!!))
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    list.clear()
    scene.buildDisplayList(list, 0, NavView(surface, sceneManager))

    assertEquals(
      "DrawEmptyDesigner,130x258\n", list.serialize()
    )
    assertTrue(sceneManager.isEmpty)
  }

  fun testZoomIn() {
    zoomTest(3.0, "Clip,0,0,5259,5568\n" +
                  "DrawRectangle,1,2400.0x2400.0x459.0x768.0,ffa7a7a7,1.0\n" +
                  "DrawNavScreen,2401.0x2401.0x457.0x766.0\n" +
                  "DrawTruncatedText,3,fragment1,2400.0x2340.0x459.0x30.0,ff656565,Default:0:36,false\n" +
                  "\n" +
                  "UNClip\n")
  }

  fun testZoomOut() {
    zoomTest(0.25, "Clip,0,0,438,464\n" +
                   "DrawRectangle,1,200.0x200.0x38.25x64.0,ffa7a7a7,1.0\n" +
                   "DrawNavScreen,201.0x201.0x36.25x62.0\n" +
                   "DrawTruncatedText,3,fragment1,200.0x195.0x38.25x2.5,ff656565,Default:0:5,false\n" +
                   "\n" +
                   "UNClip\n")
  }

  fun testZoomToFit() {
    zoomTest(1.0, "Clip,0,0,1753,1856\n" +
                  "DrawRectangle,1,800.0x800.0x153.0x256.0,ffa7a7a7,1.0\n" +
                  "DrawNavScreen,801.0x801.0x151.0x254.0\n" +
                  "DrawTruncatedText,3,fragment1,800.0x780.0x153.0x10.0,ff656565,Default:0:12,false\n" +
                  "\n" +
                  "UNClip\n")
  }

  private fun zoomTest(newScale: Double, serialized: String) {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }

    val list = DisplayList()
    val surface = model.surface
    val scene = surface.scene!!

    scene.layout(0, SceneContext.get())
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,876,928\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "DrawTruncatedText,3,fragment1,400.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )

    list.clear()

    `when`(surface.scale).thenReturn(newScale)

    scene.layout(0, SceneContext.get())
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(serialized, list.serialize())
  }

  fun testCustomDestination() {
    val relativePath = "src/mytest/navtest/MyTestNavigator.java"
    val fileText = """
      package myTest.navtest;
      import androidx.navigation.NavDestination;
      import androidx.navigation.Navigator;
      @Navigator.Name("customComponent")
      public class TestNavigator extends Navigator<TestNavigator.Destination> {
          public static class Destination extends NavDestination {}
      }
      """

    myFixture.addFileToProject(relativePath, fileText)

    val model = model("nav.xml") {
      navigation {
        custom("customComponent")
      }
    }

    val surface = model.surface
    val scene = surface.scene!!
    scene.layout(0, SceneContext.get())

    val list = DisplayList()
    scene.buildDisplayList(list, 0, NavView(surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,876,928\n" +
      "DrawRectangle,1,400.0x400.0x76.5x128.0,ffa7a7a7,1.0\n" +
      "DrawNavScreen,401.0x401.0x74.5x126.0\n" +
      "DrawTruncatedText,3,customComponent,400.0x390.0x76.5x5.0,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  /**
   * Reposition a component. If we just set the position directly children't aren't updated.
   */
  private fun moveComponentTo(component: SceneComponent, x: Int, y: Int) {
    val dragTarget = component.targets.filterIsInstance(ScreenDragTarget::class.java).first()
    dragTarget.mouseDown(component.drawX, component.drawY)
    dragTarget.mouseDrag(x, y, listOf())
    // the release position isn't used
    dragTarget.mouseRelease(x, y, listOf())
  }
}