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
package org.jetbrains.android.dom.navigation;

import static com.android.SdkConstants.TAG_DEEP_LINK;

import com.android.SdkConstants;
import com.android.tools.idea.naveditor.NavTestUtil;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.io.ZipUtil;
import java.io.File;
import java.util.Arrays;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link NavigationSchema}.
 */
public class NavigationSchemaTest extends AndroidTestCase {
  private static final String[] LEAF_DESTINATIONS = new String[] {
    "fragment", "fragment_sub", "fragment_sub_sub", "other_1", "other_2"
  };
  private static final String[] ACTIVITIES = new String[] {"activity", "activity_sub"};
  private static final String[] EMPTIES = new String[] {"include" };
  private static final String[] GROUPS = new String[] {"navigation", "navigation_sub"};

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyDirectoryToProject("navschematest", "src");

    for (String prebuiltPath : NavTestUtil.getNavEditorAarPaths()) {
      File aar = new File(PathManager.getHomePath(), prebuiltPath);
      File tempDir = FileUtil.createTempDirectory("NavigationSchemaTest", null);
      ZipUtil.extract(aar, tempDir, null);
      PsiTestUtil.addLibrary(myFixture.getModule(), new File(tempDir, "classes.jar").getPath());
    }
    NavigationSchema.createIfNecessary(myModule);
  }

  public void testSubtags() {
    NavigationSchema schema = NavigationSchema.get(myModule);

    // Destination types
    Multimap<Class<? extends AndroidDomElement>, String> subtags;

    Multimap<Class<? extends AndroidDomElement>, String> expected = HashMultimap.create();
    expected.put(DeeplinkElement.class, TAG_DEEP_LINK);
    expected.put(NavArgumentElement.class, NavigationSchema.TAG_ARGUMENT);
    for (String activity : ACTIVITIES) {
      subtags = schema.getDestinationSubtags(activity);
      assertEquals(activity, expected, subtags);
    }
    expected.put(NavActionElement.class, NavigationSchema.TAG_ACTION);
    for (String leaf : LEAF_DESTINATIONS) {
      subtags = schema.getDestinationSubtags(leaf);
      assertEquals(leaf, expected, subtags);
    }

    expected.putAll(NavGraphElement.class, Arrays.asList(GROUPS));
    expected.put(NavGraphElement.class, "include");
    expected.putAll(ConcreteDestinationElement.class, Arrays.asList(LEAF_DESTINATIONS));
    expected.putAll(ConcreteDestinationElement.class, Arrays.asList(ACTIVITIES));
    for (String group : GROUPS) {
      subtags = schema.getDestinationSubtags(group);
      assertEquals(group, expected, subtags);
    }

    for (String empty : EMPTIES) {
      assertTrue(schema.getDestinationSubtags(empty).isEmpty());
    }

    // Non-destination types
    expected.clear();
    assertEquals(expected, schema.getDestinationSubtags(NavigationSchema.TAG_ARGUMENT));
    assertEquals(expected, schema.getDestinationSubtags(TAG_DEEP_LINK));
    expected.put(NavArgumentElement.class, NavigationSchema.TAG_ARGUMENT);
    assertEquals(expected, schema.getDestinationSubtags(NavigationSchema.TAG_ACTION));
  }

  public void testDestinationClassByTag() {
    NavigationSchema schema = NavigationSchema.get(myModule);
    PsiClass activity = findClass(SdkConstants.CLASS_ACTIVITY);
    PsiClass fragment = findClass(SdkConstants.CLASS_V4_FRAGMENT.oldName());
    PsiClass navGraph = findClass("androidx.navigation.NavGraph");
    // TODO: update custom navs so some have custom destination classes in the release after alpha06

    assertSameElements(schema.getDestinationClassesForTag("activity"), activity);
    assertSameElements(schema.getDestinationClassesForTag("activity_sub"), activity);
    assertSameElements(schema.getDestinationClassesForTag("fragment"), fragment);
    assertSameElements(schema.getDestinationClassesForTag("fragment_sub"), fragment);
    assertSameElements(schema.getDestinationClassesForTag("fragment_sub_sub"), fragment);
    assertSameElements(schema.getDestinationClassesForTag("navigation"), navGraph);
    assertSameElements(schema.getDestinationClassesForTag("navigation_sub"), navGraph);
    assertEmpty(schema.getDestinationClassesForTag("other_1"));
    assertEmpty(schema.getDestinationClassesForTag("other_2"));
  }

  @NotNull
  private PsiClass findClass(@NotNull String className) {
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(getProject());
    return javaPsiFacade.findClass(className, GlobalSearchScope.allScope(getProject()));
  }

  public void testDestinationType() {
    // TODO: update custom navs so some have multiple types in the release after alpha06
    NavigationSchema schema = NavigationSchema.get(myModule);
    assertSameElements(schema.getDestinationTypesForTag("activity"), NavigationSchema.DestinationType.ACTIVITY);
    assertSameElements(schema.getDestinationTypesForTag("activity_sub"), NavigationSchema.DestinationType.ACTIVITY);
    assertSameElements(schema.getDestinationTypesForTag("fragment"), NavigationSchema.DestinationType.FRAGMENT);
    assertSameElements(schema.getDestinationTypesForTag("fragment_sub"), NavigationSchema.DestinationType.FRAGMENT);
    assertSameElements(schema.getDestinationTypesForTag("fragment_sub_sub"), NavigationSchema.DestinationType.FRAGMENT);
    assertSameElements(schema.getDestinationTypesForTag("navigation"), NavigationSchema.DestinationType.NAVIGATION);
    assertSameElements(schema.getDestinationTypesForTag("navigation_sub"), NavigationSchema.DestinationType.NAVIGATION);
    assertSameElements(schema.getDestinationTypesForTag("other_1"), NavigationSchema.DestinationType.OTHER);
    assertSameElements(schema.getDestinationTypesForTag("other_2"), NavigationSchema.DestinationType.OTHER);
  }

  public void testTagByType() {
    // TODO: update custom navs so some have "OTHER" type in the release after alpha06
    NavigationSchema schema = NavigationSchema.get(myModule);
    assertEquals("activity", schema.getDefaultTag(NavigationSchema.DestinationType.ACTIVITY));
    assertEquals("navigation", schema.getDefaultTag(NavigationSchema.DestinationType.NAVIGATION));
    assertEquals("fragment", schema.getDefaultTag(NavigationSchema.DestinationType.FRAGMENT));
  }

  public void testTagLabel() {
    // TODO: update custom navs so some have multiple types in the release after alpha06
    NavigationSchema schema = NavigationSchema.get(myModule);
    assertEquals("Activity", schema.getTagLabel("activity"));
    assertEquals("Activity (activity_sub)", schema.getTagLabel("activity_sub"));
    assertEquals("Fragment", schema.getTagLabel("fragment"));
    assertEquals("Fragment (fragment_sub)", schema.getTagLabel("fragment_sub"));
    assertEquals("Fragment (fragment_sub_sub)", schema.getTagLabel("fragment_sub_sub"));
    assertEquals("Nested Graph", schema.getTagLabel("navigation"));
    assertEquals("Nested Graph", schema.getTagLabel("navigation", false));
    assertEquals("Root Graph", schema.getTagLabel("navigation", true));
    assertEquals("Nested Graph (navigation_sub)", schema.getTagLabel("navigation_sub"));
    assertEquals("other_1", schema.getTagLabel("other_1"));
    assertEquals("other_2", schema.getTagLabel("other_2"));
    assertEquals("Include Graph", schema.getTagLabel("include"));
    assertEquals("Action", schema.getTagLabel("action"));
  }

  private PsiClass addClass(@Language("JAVA") @NotNull String content) {
    return WriteCommandAction.runWriteCommandAction(
      getProject(),
      (Computable<PsiClass>)(() -> myFixture.addClass(content)));
  }

  private void updateContent(PsiClass psiClass, @Language("JAVA") String newContent) {
    WriteCommandAction.runWriteCommandAction(
      getProject(), () -> {
        try {
          psiClass.getContainingFile().getVirtualFile().setBinaryContent(newContent.getBytes());
        }
        catch (Exception e) {
          fail(e.getMessage());
        }
      });
  }

  private void testQuickValidate(@NotNull @Language("JAVA") String initialContent, @NotNull @Language("JAVA") String newContent,
                                 boolean doesValidate) throws Exception {
    PsiClass navigator = addClass(initialContent);
    NavigationSchema schema = NavigationSchema.get(myModule).rebuildSchema().get();
    assertTrue(schema.quickValidate());

    updateContent(navigator, newContent);
    assertEquals(doesValidate, schema.quickValidate());
  }

  public void testQuickValidateNoChange() throws Exception {
    testQuickValidate("import androidx.navigation.*;\n" +
                      "@Navigator.Name(\"activity_sub\")\n" +
                      "public class QuickValidateNoChange extends ActivityNavigator {}\n",
                      "import androidx.navigation.*;\n" +
                      "@Navigator.Name(\"activity_sub\")\n" +
                      "public class QuickValidateNoChange extends ActivityNavigator {}\n",
                      true);
  }

  public void testQuickValidateIrrelevantChange() throws Exception {
    testQuickValidate("import androidx.navigation.*;\n" +
                      "@Navigator.Name(\"activity_sub\")\n" +
                      "public class QuickValidateIrrelevantChange extends ActivityNavigator {}\n",
                      "import androidx.navigation.*;\n" +
                      "@Navigator.Name(\"activity_sub\")\n" +
                      "public class QuickValidateIrrelevantChange extends ActivityNavigator {\n" +
                      "  public String foo() { return \"bar\"; }\n" +
                      "}\n",
                      true);
  }

  public void testQuickValidateWithDelete() throws Exception {
    @Language("JAVA")
    String content = "import androidx.navigation.*;\n" +
                     "@Navigator.Name(\"fragment_sub\")\n" +
                     "public class QuickValidateWithDelete extends ActivityNavigator {}\n";
    PsiClass navigator = addClass(content);
    NavigationSchema schema = NavigationSchema.get(myModule).rebuildSchema().get();
    assertTrue(schema.quickValidate());

    WriteCommandAction.runWriteCommandAction(getProject(), () -> navigator.getContainingFile().delete());
    assertFalse(schema.quickValidate());
  }

  public void testQuickValidateChangeTag() throws Exception {
    testQuickValidate("import androidx.navigation.*;\n" +
                      "@Navigator.Name(\"fragment_sub\")\n" +
                      "public class QuickValidateChangeTag extends ActivityNavigator {}\n",
                      "import androidx.navigation.*;\n" +
                      "@Navigator.Name(\"fragment_sub_foo\")\n" +
                      "public class QuickValidateChangeTag extends ActivityNavigator {}\n",
                      false);
  }

  public void testQuickValidateAddTag() throws Exception {
    testQuickValidate("import androidx.navigation.*;\n" +
                      "public class QuickValidateAddTag extends ActivityNavigator {}\n",
                      "import androidx.navigation.*;\n" +
                      "@Navigator.Name(\"fragment_sub\")\n" +
                      "public class QuickValidateAddTag extends ActivityNavigator {}\n",
                      false);
  }

  public void testQuickValidateRemoveTag() throws Exception {
    testQuickValidate("import androidx.navigation.*;\n" +
                      "@Navigator.Name(\"fragment_sub\")\n" +
                      "public class QuickValidateRemoveTag extends ActivityNavigator {}\n",
                      "import androidx.navigation.*;\n" +
                      "public class QuickValidateRemoveTag extends ActivityNavigator {}\n",
                      false);
  }

  public void testQuickValidateChangeDestinationButNoAnnotationChange() throws Exception {
    testQuickValidate("import android.app.Activity;\n" +
                      "import androidx.navigation.*;\n" +
                      "\n" +
                      "public class QuickValidateChangeDestinationButNoAnnotationChange extends " +
                      "    Navigator<QuickValidateChangeDestinationButNoAnnotationChange.Destination1> {\n" +
                      "  @NavDestination.ClassType(Activity.class)\n" +
                      "  public static class Destination1 extends NavDestination {}\n" +
                      "\n" +
                      "  @NavDestination.ClassType(Activity.class)\n" +
                      "  public static class Destination2 extends NavDestination {}\n" +
                      "}\n",
                      "import android.app.Activity;\n" +
                      "import androidx.navigation.*;\n" +
                      "\n" +
                      "public class QuickValidateChangeDestinationButNoAnnotationChange extends " +
                      "    Navigator<QuickValidateChangeDestinationButNoAnnotationChange.Destination2> {\n" +
                      "  @NavDestination.ClassType(Activity.class)\n" +
                      "  public static class Destination1 extends NavDestination {}\n" +
                      "\n" +
                      "  @NavDestination.ClassType(Activity.class)\n" +
                      "  public static class Destination2 extends NavDestination {}\n" +
                      "}\n",
                      true);
  }

  public void testQuickValidateChangeDestinationWithAnnotationChange() throws Exception {
    testQuickValidate("import android.app.Activity;\n" +
                      "import androidx.navigation.*;\n" +
                      "\n" +
                      "public class QuickValidateChangeDestinationWithAnnotationChange extends " +
                      "    Navigator<QuickValidateChangeDestinationWithAnnotationChange.Destination1> {\n" +
                      "  @NavDestination.ClassType(Activity.class)\n" +
                      "  public static class Destination1 extends NavDestination {}\n" +
                      "\n" +
                      "  @NavDestination.ClassType(Activity.class)\n" +
                      "  public static class Destination2 extends NavDestination {}\n" +
                      "}\n",
                      "import android.app.Activity;\n" +
                      "import androidx.navigation.*;\n" +
                      "import android.support.v4.app.Fragment;\n" +
                      "\n" +
                      "public class QuickValidateChangeDestinationWithAnnotationChange extends " +
                      "    Navigator<QuickValidateChangeDestinationWithAnnotationChange.Destination2> {\n" +
                      "  @NavDestination.ClassType(Activity.class)\n" +
                      "  public static class Destination1 extends NavDestination {}\n" +
                      "\n" +
                      "  @NavDestination.ClassType(Fragment.class)\n" +
                      "  public static class Destination2 extends NavDestination {}\n" +
                      "}\n",
                      false);
  }

  public void testQuickValidateDestinationChangeInSuper() throws Exception {
    testQuickValidate("import android.app.Activity;\n" +
                      "import androidx.navigation.*;\n" +
                      "\n" +
                      "public class QuickValidateDestinationChangeInSuper extends " +
                      "    Navigator<QuickValidateDestinationChangeInSuper.Destination> {\n" +
                      "  @NavDestination.ClassType(Activity.class)\n" +
                      "  public static class DestinationSuper extends NavDestination {}\n" +
                      "\n" +
                      "  public static class Destination extends DestinationSuper {}\n" +
                      "}\n",
                      "import android.app.Activity;\n" +
                      "import androidx.navigation.*;\n" +
                      "import android.support.v4.app.Fragment;\n" +
                      "\n" +
                      "public class QuickValidateDestinationChangeInSuper extends " +
                      "    Navigator<QuickValidateDestinationChangeInSuper.Destination> {\n" +
                      "  @NavDestination.ClassType(Fragment.class)\n" +
                      "  public static class DestinationSuper extends NavDestination {}\n" +
                      "\n" +
                      "  public static class Destination extends DestinationSuper {}\n" +
                      "}\n",
                      false);
  }

  public void testQuickValidateAddDestinationNoAnnotation() throws Exception {
    testQuickValidate("import android.app.Activity;\n" +
                      "import androidx.navigation.*;\n" +
                      "\n" +
                      "public class QuickValidateAddDestinationNoAnnotation extends Navigator {}\n",
                      "import android.app.Activity;\n" +
                      "import androidx.navigation.*;\n" +
                      "\n" +
                      "public class QuickValidateAddDestinationNoAnnotation extends " +
                      "    Navigator<QuickValidateAddDestinationNoAnnotation.Destination> {\n" +
                      "  public static class Destination extends NavDestination {}\n" +
                      "}\n",
                      true);
  }

  public void testQuickValidateAddDestinationAnnotation() throws Exception {
    testQuickValidate("import android.app.Activity;\n" +
                      "import androidx.navigation.*;\n" +
                      "\n" +
                      "public class QuickValidateAddDestinationAnnotation extends " +
                      "    Navigator<QuickValidateAddDestinationAnnotation.Destination> {\n" +
                      "  public static class Destination extends NavDestination {}\n" +
                      "}\n",
                      "import android.app.Activity;\n" +
                      "import androidx.navigation.*;\n" +
                      "\n" +
                      "public class QuickValidateAddDestinationAnnotation extends " +
                      "    Navigator<QuickValidateAddDestinationAnnotation.Destination> {\n" +
                      "  @NavDestination.ClassType(Activity.class)\n" +
                      "  public static class Destination extends NavDestination {}\n" +
                      "}\n",
                      false);
  }

  public void testQuickValidateRemoveDestinationNoAnnotation() throws Exception {
    testQuickValidate("import androidx.navigation.*;\n" +
                      "\n" +
                      "public class QuickValidateRemoveDestinationNoAnnotation extends " +
                      "    Navigator<QuickValidateRemoveDestinationNoAnnotation.Destination> {\n" +
                      "  public static class Destination extends NavDestination {}\n" +
                      "}\n",
                      "import androidx.navigation.*;\n" +
                      "\n" +
                      "public class QuickValidateRemoveDestinationNoAnnotation extends Navigator {}\n",
                      true);
  }

  public void testQuickValidateRemoveDestinationWithAnnotation() throws Exception {
    testQuickValidate("import androidx.navigation.*;\n" +
                      "import android.app.Activity;\n" +
                      "\n" +
                      "public class QuickValidateRemoveDestinationWithAnnotation extends " +
                      "    Navigator<QuickValidateRemoveDestinationWithAnnotation.Destination> {\n" +
                      "  @NavDestination.ClassType(Activity.class)\n" +
                      "  public static class Destination extends NavDestination {}\n" +
                      "}\n",
                      "import androidx.navigation.*;\n" +
                      "\n" +
                      "public class QuickValidateRemoveDestinationWithAnnotation extends Navigator {}\n",
                      false);
  }

  public void testQuickValidateRemoveDestinationAnnotation() throws Exception {
    testQuickValidate("import androidx.navigation.*;\n" +
                      "import android.app.Activity;\n" +
                      "\n" +
                      "public class QuickValidateRemoveDestinationWithAnnotation extends " +
                      "    Navigator<QuickValidateRemoveDestinationWithAnnotation.Destination> {\n" +
                      "  @NavDestination.ClassType(Activity.class)\n" +
                      "  public static class Destination extends NavDestination {}\n" +
                      "}\n",
                      "import androidx.navigation.*;\n" +
                      "\n" +
                      "public class QuickValidateRemoveDestinationWithAnnotation extends " +
                      "    Navigator<QuickValidateRemoveDestinationWithAnnotation.Destination> {\n" +
                      "  public static class Destination extends NavDestination {}\n" +
                      "}\n",
                      false);

  }

  public void testQuickValidateChangeDestination() throws Exception {
    testQuickValidate("import android.app.Activity;\n" +
                      "import androidx.navigation.*;\n" +
                      "\n" +
                      "public class QuickValidateChangeDestination extends " +
                      "    Navigator<QuickValidateChangeDestination.Destination> {\n" +
                      "  public static class ActualDestination extends Activity {}\n" +
                      "  @NavDestination.ClassType(QuickValidateChangeDestination.ActualDestination.class)\n" +
                      "  public static class Destination extends NavDestination {}\n" +
                      "}\n",
                      "import android.app.Activity;\n" +
                      "import androidx.navigation.*;\n" +
                      "\n" +
                      "public class QuickValidateChangeDestination extends " +
                      "    Navigator<QuickValidateChangeDestination.Destination> {\n" +
                      "  public static class ActualDestination extends Activity {" +
                      "    public String foo() { return \"bar\"; }" +
                      "}\n" +
                      "  @NavDestination.ClassType(QuickValidateChangeDestination.ActualDestination.class)\n" +
                      "  public static class Destination extends NavDestination {}\n" +
                      "}\n",
                      true);
  }
}
