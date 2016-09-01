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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SelectionListener;
import com.android.tools.idea.uibuilder.property.ptable.PTableGroupItem;
import com.android.util.PropertiesMap;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NlPropertyItemTest extends PropertyTestCase {
  private NlModel myModel;
  private NlComponent myMerge;
  private NlComponent myTextView;
  private NlComponent myButton1;
  private NlComponent myButton2;
  private NlComponent myButton3;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myModel = createModel();
    myTextView = findComponent(myModel, "text", 100);
    myButton1 = findComponent(myModel, "button1", 200);
    myButton2 = findComponent(myModel, "button2", 300);
    myButton3 = findComponent(myModel, "button3", 400);
    myMerge = myTextView.getParent();
    assertThat(myMerge).isNotNull();
  }

  public void testCreateFlagProperty() {
    NlPropertyItem item = createFrom(myTextView, ATTR_GRAVITY);
    assertThat(item).isInstanceOf(NlFlagPropertyItem.class);
    assertThat(item.getName()).isEqualTo(ATTR_GRAVITY);
  }

  public void testCreateIDProperty() {
    NlPropertyItem item = createFrom(myTextView, ATTR_ID);
    assertThat(item).isInstanceOf(NlIdPropertyItem.class);
    assertThat(item.getName()).isEqualTo(ATTR_ID);
  }

  public void testCreateTextProperty() {
    NlPropertyItem item = createFrom(myTextView, ATTR_TEXT);
    assertThat(item).isNotInstanceOf(NlFlagPropertyItem.class);
    assertThat(item).isNotInstanceOf(NlIdPropertyItem.class);
    assertThat(item.getName()).isEqualTo(ATTR_TEXT);
  }

  public void testCreateDesignProperty() {
    NlPropertyItem item = createFrom(myTextView, ATTR_TEXT);
    NlPropertyItem design = item.getDesignTimeProperty();
    assertThat(design).isNotInstanceOf(NlFlagPropertyItem.class);
    assertThat(design).isNotInstanceOf(NlIdPropertyItem.class);
    assertThat(design.getName()).isEqualTo(ATTR_TEXT);
    assertThat(design.getNamespace()).isEqualTo(TOOLS_URI);

    // The design property of a design property is itself
    assertThat(design.getDesignTimeProperty()).isSameAs(design);
  }

  public void testCreateDesignPropertyInPropertyTable() {
    NlPropertyItem item = createFrom(myTextView, ATTR_TEXT);
    PTableGroupItem group = new SimpleGroupItem();
    group.addChild(item);
    NlPropertyItem design = item.getDesignTimeProperty();
    assertThat(design).isNotInstanceOf(NlFlagPropertyItem.class);
    assertThat(design).isNotInstanceOf(NlIdPropertyItem.class);
    assertThat(design.getName()).isEqualTo(ATTR_TEXT);
    assertThat(design.getNamespace()).isEqualTo(TOOLS_URI);
    assertThat(design.getParent()).isEqualTo(group);
    assertThat(group.getChildren()).containsAllOf(item, design);

    // Deleting it removes it from the group
    design.delete();
    assertThat(group.getChildren()).doesNotContain(design);
  }

  public void testCreateWithoutSpecifyingNamespace() {
    XmlAttributeDescriptor descriptor = getDescriptor(myTextView, ATTR_TEXT);
    assertThat(descriptor).isNotNull();
    NlPropertyItem item = NlPropertyItem.create(ImmutableList.of(myTextView), descriptor, null, getDefinition(myTextView, descriptor));
    assertThat(item).isNotInstanceOf(NlFlagPropertyItem.class);
    assertThat(item).isNotInstanceOf(NlIdPropertyItem.class);
    assertThat(item.getName()).isEqualTo(ATTR_TEXT);
    assertThat(item.getNamespace()).isEqualTo(ANDROID_URI);
  }

  public void testCreateWithoutAttributeDefinition() {
    // It is an error not to specify an AttributeDefinition for normal attributes
    XmlAttributeDescriptor descriptor = getDescriptor(myTextView, ATTR_TEXT);
    assertThat(descriptor).isNotNull();
    try {
      NlPropertyItem.create(ImmutableList.of(myTextView), descriptor, null, null);
      fail("An AttributeDefinition should exist for ATTR_TEXT");
    }
    catch (IllegalArgumentException ex) {
      assertThat(ex.getMessage()).isEqualTo("Missing attribute definition for text");
    }

    // Style does not have an AttributeDefinition
    NlPropertyItem item = createFrom(myTextView, ATTR_STYLE);
    assertThat(item).isNotInstanceOf(NlFlagPropertyItem.class);
    assertThat(item).isNotInstanceOf(NlIdPropertyItem.class);
    assertThat(item.getName()).isEqualTo(ATTR_STYLE);
    assertThat(item.getDefinition()).isNull();
  }

  public void testCreateForToolAttributes() {
    AttributeDefinition context = mock(AttributeDefinition.class);
    when(context.getName()).thenReturn(ATTR_CONTEXT);
    NlPropertyItem item = new NlPropertyItem(ImmutableList.of(myMerge), TOOLS_URI, context);
    assertThat(item).isNotInstanceOf(NlFlagPropertyItem.class);
    assertThat(item).isNotInstanceOf(NlIdPropertyItem.class);
    assertThat(item.getName()).isEqualTo(ATTR_CONTEXT);
    assertThat(item.getNamespace()).isEqualTo(TOOLS_URI);
    assertThat(item.getDefinition()).isEqualTo(context);
  }

  public void testSameDefinition() {
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);
    NlPropertyItem gravity = createFrom(myTextView, ATTR_GRAVITY);
    XmlAttributeDescriptor gravityDescriptor = getDescriptor(myTextView, ATTR_GRAVITY);
    assertThat(gravityDescriptor).isNotNull();

    assertThat(text.sameDefinition(text)).isTrue();
    assertThat(text.sameDefinition(new NlPropertyItem(ImmutableList.of(myTextView), ANDROID_URI, text.getDefinition()))).isTrue();
    assertThat(text.sameDefinition(new NlPropertyItem(ImmutableList.of(myTextView), TOOLS_URI, text.getDefinition()))).isFalse();
    assertThat(text.sameDefinition(new NlPropertyItem(ImmutableList.of(myTextView), ANDROID_URI, gravity.getDefinition()))).isFalse();
    assertThat(text.sameDefinition(new NlPropertyItem(ImmutableList.of(myTextView), gravityDescriptor, TOOLS_URI, text.getDefinition())))
      .isFalse();
  }

  public void testGetValue() {
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);
    assertThat(new NlPropertyItem(ImmutableList.of(myTextView), ANDROID_URI, text.getDefinition()).getValue()).isEqualTo("Text");
    assertThat(new NlPropertyItem(ImmutableList.of(myButton1), ANDROID_URI, text.getDefinition()).getValue()).isEqualTo("Button");
    assertThat(new NlPropertyItem(ImmutableList.of(myButton2), ANDROID_URI, text.getDefinition()).getValue()).isEqualTo("Text");
    assertThat(new NlPropertyItem(ImmutableList.of(myButton3), ANDROID_URI, text.getDefinition()).getValue()).isNull();
    assertThat(new NlPropertyItem(ImmutableList.of(myButton1, myButton2), ANDROID_URI, text.getDefinition()).getValue()).isNull();
    assertThat(new NlPropertyItem(ImmutableList.of(myTextView, myButton2), ANDROID_URI, text.getDefinition()).getValue()).isEqualTo("Text");
  }

  public void testIsDefaultValue() {
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);
    assertThat(text.isDefaultValue(null)).isTrue();
    assertThat(text.isDefaultValue("Text")).isFalse();

    text.setDefaultValue(new PropertiesMap.Property("Text", "Text"));
    assertThat(text.isDefaultValue(null)).isTrue();
    assertThat(text.isDefaultValue("Text")).isTrue();
  }

  public void testGetResolvedValue() {
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);
    assertThat(text.getResolvedValue()).isEqualTo("Text");

    NlPropertyItem textAppearance = createFrom(myTextView, ATTR_TEXT_APPEARANCE);
    assertThat(textAppearance.getResolvedValue()).isNull();

    textAppearance.setValue("?android:attr/textAppearanceMedium");
    assertThat(textAppearance.getResolvedValue()).isEqualTo("@android:style/TextAppearance.Medium");

    textAppearance.setValue("@android:style/TextAppearance.Medium");
    assertThat(textAppearance.getResolvedValue()).isEqualTo("@android:style/TextAppearance.Medium");

    textAppearance.setValue("?android:attr/textAppearanceMedium");
    textAppearance.setDefaultValue(new PropertiesMap.Property("?android:attr/textAppearanceMedium", null));
    assertThat(textAppearance.getResolvedValue()).isEqualTo("@android:style/TextAppearance.Medium");

    textAppearance.setValue(null);
    textAppearance.setDefaultValue(new PropertiesMap.Property("?android:attr/textAppearanceMedium", "@android:style/TextAppearance.Medium"));
    assertThat(textAppearance.getResolvedValue()).isEqualTo("@android:style/TextAppearance.Medium");

    NlPropertyItem size = createFrom(myTextView, ATTR_TEXT_SIZE);
    assertThat(size.getResolvedValue()).isEqualTo(null);

    // TODO: Investigate why this works in Studio but not in the test.
    //size.setValue("@dimen/text_size_small_material");
    //assertThat(size.getResolvedValue()).isEqualTo("14sp");

    size.setDefaultValue(new PropertiesMap.Property("@dimen/text_size_small_material", "14sp"));
    assertThat(size.getResolvedValue()).isEqualTo("14sp");
  }

  public void testGetChildProperty() {
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);
    try {
      text.getChildProperty("SomeThing");
      fail("This should have caused an UnsupportedOperationException");
    }
    catch (UnsupportedOperationException ex) {
      assertThat(ex.getMessage()).isEqualTo("SomeThing");
    }
  }

  public void testGetTag() {
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);
    assertThat(text.getTag()).isNotNull();
    assertThat(text.getTag().getName()).isEqualTo(TEXT_VIEW);
    assertThat(text.getTagName()).isEqualTo(TEXT_VIEW);

    // Multiple component does not give access to tag and tagName
    NlPropertyItem text2 = new NlPropertyItem(ImmutableList.of(myTextView, myButton1), ANDROID_URI, text.getDefinition());
    assertThat(text2.getTag()).isNull();
    assertThat(text2.getTagName()).isNull();
  }

  public void testSetValue() {
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);
    text.setValue("Hello World");
    assertThat(myTextView.getAttribute(ANDROID_URI, ATTR_TEXT)).isEqualTo("Hello World");

    // Check that setting the parent_tag property causes a selection event, and that the parent attributes are added
    assertThat(getDescriptor(myMerge, ATTR_ORIENTATION)).isNull();
    NlPropertyItem parentTag = createFrom(myMerge, ATTR_PARENT_TAG);
    SelectionListener selectionListener = mock(SelectionListener.class);
    myModel.getSelectionModel().addListener(selectionListener);
    parentTag.setValue(LINEAR_LAYOUT);
    assertThat(myMerge.getAttribute(TOOLS_URI, ATTR_PARENT_TAG)).isEqualTo(LINEAR_LAYOUT);
    verify(selectionListener).selectionChanged(eq(myModel.getSelectionModel()), anyListOf(NlComponent.class));
    assertThat(getDescriptor(myMerge, ATTR_ORIENTATION)).isNotNull();
  }

  public void testSetValueOnDisposedProject() {
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);

    // Make a fake project instance that reports true to isDisposed()
    Project fakeProject = mock(Project.class);
    when(fakeProject.isDisposed()).thenReturn(true);
    NlModel fakeModel = mock(NlModel.class);
    when(fakeModel.getProject()).thenReturn(fakeProject);
    NlComponent fakeComponent = mock(NlComponent.class);
    when(fakeComponent.getModel()).thenReturn(fakeModel);
    when(fakeComponent.getTag()).thenThrow(new RuntimeException("setValue should bail out"));
    NlPropertyItem fake = new NlPropertyItem(ImmutableList.of(fakeComponent), ANDROID_URI, text.getDefinition());
    fake.setValue("stuff");
  }

  public void testMisc() {
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);

    assertThat(text.toString()).isEqualTo("NlPropertyItem{name=text, namespace=@android:}");
    assertThat(text.getTooltipText()).startsWith("@android:text:  Text to display.");
    assertThat(text.isEditable(0)).isTrue();
  }

  @NotNull
  private NlModel createModel() {
    return model("merge.xml",
                 component(VIEW_MERGE)
                   .withBounds(0, 0, 1000, 1000)
                   .matchParentWidth()
                   .matchParentHeight()
                   .children(
                     component(TEXT_VIEW)
                       .withBounds(100, 100, 100, 100)
                       .id("@id/text")
                       .width("100dp")
                       .height("100dp")
                       .text("Text"),
                     component(BUTTON)
                       .withBounds(100, 200, 100, 100)
                       .id("@id/button1")
                       .width("100dp")
                       .height("100dp")
                       .text("Button"),
                     component(BUTTON)
                       .withBounds(100, 300, 100, 100)
                       .id("@id/button2")
                       .width("100dp")
                       .height("100dp")
                       .text("Text"),
                     component(BUTTON)
                       .withBounds(100, 400, 100, 100)
                       .id("@id/button3")
                       .width("100dp")
                       .height("100dp")
                   )).build();
  }

  private static class SimpleGroupItem extends PTableGroupItem {
    @NotNull
    @Override
    public String getName() {
      return "Group";
    }
  }
}
