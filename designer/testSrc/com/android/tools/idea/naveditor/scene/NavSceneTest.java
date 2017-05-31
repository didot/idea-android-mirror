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
package com.android.tools.idea.naveditor.scene;

import com.android.SdkConstants;
import com.android.tools.idea.naveditor.NavigationTestCase;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.naveditor.surface.NavView;
import com.android.tools.idea.uibuilder.SyncNlModel;
import com.android.tools.idea.uibuilder.fixtures.ComponentDescriptor;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.surface.ZoomType;
import org.jetbrains.android.dom.navigation.NavigationSchema;

import static org.mockito.Mockito.when;

/**
 * Tests for the nav editor Scene.
 */
public class NavSceneTest extends NavigationTestCase {

  public void testDisplayList() {
    ComponentDescriptor root = component(NavigationSchema.TAG_NAVIGATION)
      .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_START_DESTINATION, "@id/fragment1")
      .unboundedChildren(
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment1")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main")
          .unboundedChildren(
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/subnav"),
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@id/activity")
          ),
        component(NavigationSchema.TAG_NAVIGATION).id("@+id/subnav")
          .unboundedChildren(
            component(NavigationSchema.TAG_FRAGMENT)
              .id("@+id/fragment2")
              .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main2")
              .unboundedChildren(component(NavigationSchema.TAG_ACTION)
                                   .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@id/activity"))),
        component("activity").id("@+id/activity"));
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,690,390\n" +
                 "DrawNavScreen,311,51,191,319\n" +
                 "DrawComponentFrame,310,50,192,320,1,false\n" +
                 "DrawAction,NORMAL,310x50x192x320,570x50x100x25,NORMAL\n" +
                 "DrawAction,NORMAL,310x50x192x320,50x50x192x320,NORMAL\n" +
                 "DrawActionHandle,502,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawAction,NORMAL,98x50x192x320,310x50x192x320,NORMAL\n" +
                 "DrawTextRegion,570,50,100,25,0,20,true,false,4,4,14,1.0,\"navigation\"\n" +
                 "DrawComponentFrame,570,50,100,25,1,true\n" +
                 "DrawAction,NORMAL,570x50x100x25,50x50x192x320,NORMAL\n" +
                 "DrawActionHandle,670,62,0,0,ffc0c0c0,fafafa\n" +
                 "DrawComponentFrame,50,50,192,320,1,false\n" +
                 "DrawActionHandle,242,210,0,0,ffc0c0c0,fafafa\n" +
                 "UNClip\n", list.serialize());
  }

  public void testAddComponent() {
    ComponentDescriptor root = component(NavigationSchema.TAG_NAVIGATION)
      .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_START_DESTINATION, "@id/fragment2")
      .unboundedChildren(
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment1")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main")
          .unboundedChildren(
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/fragment2")
          ),
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment2")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main2"));
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get());

    root.addChild(component(NavigationSchema.TAG_FRAGMENT).id("@+id/fragment3"), null);
    modelBuilder.updateModel(model);
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,782,390\n" +
                 "DrawNavScreen,311,51,191,319\n" +
                 "DrawComponentFrame,310,50,192,320,1,false\n" +
                 "DrawAction,NORMAL,310x50x192x320,570x50x192x320,NORMAL\n" +
                 "DrawActionHandle,502,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawNavScreen,571,51,191,319\n" +
                 "DrawComponentFrame,570,50,192,320,1,false\n" +
                 "DrawActionHandle,762,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawAction,NORMAL,358x50x192x320,570x50x192x320,NORMAL\n" +
                 "DrawComponentFrame,50,50,192,320,1,false\n" +
                 "DrawActionHandle,242,210,0,0,ffc0c0c0,fafafa\n" +
                 "UNClip\n", list.serialize());
  }

  public void testRemoveComponent() {
    ComponentDescriptor fragment2 = component(NavigationSchema.TAG_FRAGMENT)
      .id("@+id/fragment2")
      .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main2");
    ComponentDescriptor root = component(NavigationSchema.TAG_NAVIGATION)
      .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_START_DESTINATION, "@id/fragment2")
      .unboundedChildren(
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment1")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main")
          .unboundedChildren(
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/fragment2")),
        fragment2);
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get());

    root.removeChild(fragment2);
    modelBuilder.updateModel(model);
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,262,390\n" +
                 "DrawNavScreen,51,51,191,319\n" +
                 "DrawComponentFrame,50,50,192,320,1,false\n" +
                 "DrawActionHandle,242,210,0,0,ffc0c0c0,fafafa\n" +
                 "UNClip\n", list.serialize());
  }

  public void testSubflow() {
    ComponentDescriptor root = component(NavigationSchema.TAG_NAVIGATION)
      .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_START_DESTINATION, "@id/fragment2")
      .unboundedChildren(
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment1")
          .unboundedChildren(
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/fragment2")
          ),
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment2")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main2")
          .unboundedChildren(
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/fragment3")
          ),
        component(NavigationSchema.TAG_NAVIGATION)
          .id("@+id/subnav")
          .unboundedChildren(
            component(NavigationSchema.TAG_FRAGMENT)
              .id("@+id/fragment3")
              .unboundedChildren(
                component(NavigationSchema.TAG_ACTION)
                  .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/fragment4")),
            component(NavigationSchema.TAG_FRAGMENT)
              .id("@+id/fragment4")
              .unboundedChildren(
                component(NavigationSchema.TAG_ACTION)
                  .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/fragment1"))));
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    NavDesignSurface surface = new NavDesignSurface(getProject(), getTestRootDisposable());
    surface.setSize(1000, 1000);
    surface.setModel(model);
    surface.zoom(ZoomType.ACTUAL);

    Scene scene = surface.getScene();
    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get());

    NavView view = new NavView(surface, model);
    scene.buildDisplayList(list, 0, view);
    assertEquals("Clip,0,0,1380,780\n" +
                 "DrawComponentFrame,620,100,384,640,1,false\n" +
                 "DrawAction,NORMAL,620x100x384x640,100x100x384x640,NORMAL\n" +
                 "DrawActionHandle,1004,420,0,0,ffc0c0c0,fafafa\n" +
                 "DrawNavScreen,101,101,383,639\n" +
                 "DrawComponentFrame,100,100,384,640,1,false\n" +
                 "DrawActionHandle,484,420,0,0,ffc0c0c0,fafafa\n" +
                 "DrawAction,NORMAL,-304x100x384x640,100x100x384x640,NORMAL\n" +
                 "DrawTextRegion,1140,100,200,50,0,20,true,false,4,4,14,1.0,\"navigation\"\n" +
                 "DrawComponentFrame,1140,100,200,50,1,true\n" +
                 "DrawAction,NORMAL,1140x100x200x50,620x100x384x640,NORMAL\n" +
                 "DrawActionHandle,1340,124,0,0,ffc0c0c0,fafafa\n" +
                 "UNClip\n", list.serialize());
    list.clear();
    surface.setCurrentNavigation(model.find("subnav"));
    scene.layout(0, SceneContext.get(view));
    scene.buildDisplayList(list, 0, view);
    assertEquals("Clip,0,0,1044,780\n" +
                 "DrawComponentFrame,620,100,384,640,1,false\n" +
                 "DrawAction,NORMAL,620x100x384x640,100x100x384x640,NORMAL\n" +
                 "DrawActionHandle,1004,420,0,0,ffc0c0c0,fafafa\n" +
                 "DrawComponentFrame,100,100,384,640,1,false\n" +
                 "DrawActionHandle,484,420,0,0,ffc0c0c0,fafafa\n" +
                 "UNClip\n", list.serialize());
  }

}
