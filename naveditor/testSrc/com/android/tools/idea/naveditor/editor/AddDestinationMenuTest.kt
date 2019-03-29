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
package com.android.tools.idea.naveditor.editor

import com.android.SdkConstants
import com.android.SdkConstants.TAG_INCLUDE
import com.android.tools.idea.actions.NewAndroidComponentAction.CREATED_FILES
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.TestNavEditor
import com.android.tools.idea.naveditor.analytics.TestNavUsageTracker
import com.android.tools.idea.naveditor.model.className
import com.android.tools.idea.naveditor.model.layout
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.wireless.android.sdk.stats.NavDestinationInfo
import com.google.wireless.android.sdk.stats.NavDestinationInfo.DestinationType.FRAGMENT
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.ADD_DESTINATION
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.ADD_INCLUDE
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.CREATE_FRAGMENT
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.project.rootManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.PlatformTestUtil
import junit.framework.TestCase
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import java.awt.event.MouseEvent
import java.io.File
import java.util.stream.Collectors
import javax.swing.JPanel

// TODO: testing with custom navigators
class AddDestinationMenuTest : NavTestCase() {

  private var _modelBuilder: ModelBuilder? = null
  private val modelBuilder
    get() = _modelBuilder!!

  private var _model: SyncNlModel? = null
  private val model
    get() = _model!!

  private var _surface: NavDesignSurface? = null
  private val surface
    get() = _surface!!

  private var _menu: AddDestinationMenu? = null
  private val menu
    get() = _menu!!

  private var _panel: JPanel? = null
  private val panel
    get() = _panel!!

  private var _root: NavModelBuilderUtil.NavigationComponentDescriptor? = null
  private val root
    get() = _root!!

  override fun setUp() {
    super.setUp()
    _modelBuilder = modelBuilder("nav.xml") {
      navigation("navigation") {
        fragment("fragment")
        navigation("subnav") {
          fragment("fragment2")
        }
      }.also { _root = it }
    }
    _model = modelBuilder.build()

    _surface = NavDesignSurface(project, myRootDisposable)
    surface.setSize(1000, 1000)
    surface.model = model
    _menu = AddDestinationMenu(surface)
    _panel = getMainMenuPanel()
  }

  fun testContent() {
    val virtualFile = project.baseDir.findFileByRelativePath("../unitTest/res/layout/activity_main.xml")
    val xmlFile = PsiManager.getInstance(project).findFile(virtualFile!!) as XmlFile

    addFragment("fragment1")
    addFragment("fragment3")
    addFragment("fragment2")

    addActivity("activity2")
    addActivityWithLayout("activity3")
    val activity3VirtualFile = project.baseDir.findFileByRelativePath("../unitTest/res/layout/activity3.xml")
    val activity3XmlFile = PsiManager.getInstance(project).findFile(activity3VirtualFile!!) as XmlFile

    addActivityWithNavHost("activity1")

    addIncludeFile("include3")
    addIncludeFile("include2")
    addIncludeFile("include1")

    addDestination("NavHostFragmentChild", "androidx.navigation.fragment.NavHostFragment")

    val parent = model.components[0]

    val placeHolder = Destination.PlaceholderDestination(parent)

    val blankFragment =
      Destination.RegularDestination(parent, "fragment", null, findClass("mytest.navtest.BlankFragment"))
    val fragment1 =
      Destination.RegularDestination(parent, "fragment", null, findClass("mytest.navtest.fragment1"))
    val fragment2 =
      Destination.RegularDestination(parent, "fragment", null, findClass("mytest.navtest.fragment2"))
    val fragment3 =
      Destination.RegularDestination(parent, "fragment", null, findClass("mytest.navtest.fragment3"))

    val include1 = Destination.IncludeDestination("include1.xml", parent)
    val include2 = Destination.IncludeDestination("include2.xml", parent)
    val include3 = Destination.IncludeDestination("include3.xml", parent)
    val includeNav = Destination.IncludeDestination("navigation.xml", parent)

    val activity2 =
      Destination.RegularDestination(parent, "activity", null, findClass("mytest.navtest.activity2"))
    val activity3 = Destination.RegularDestination(
      parent, "activity", null, findClass("mytest.navtest.activity3"), layoutFile = activity3XmlFile)
    val mainActivity = Destination.RegularDestination(
      parent, "activity", null, findClass("mytest.navtest.MainActivity"), layoutFile = xmlFile)

    val expected = mutableListOf(placeHolder, blankFragment, fragment1, fragment2, fragment3, include1, include2, include3, includeNav,
                                 activity2, activity3, mainActivity)

    var destinations = AddDestinationMenu(surface).destinations
    assertEquals(destinations, expected)

    root.include("include1")
    modelBuilder.updateModel(model)
    model.notifyModified(NlModel.ChangeType.EDIT)

    expected.remove(include1)
    destinations = AddDestinationMenu(surface).destinations
    assertEquals(destinations, expected)

    root.fragment("fragment1", name = "mytest.navtest.fragment1")
    modelBuilder.updateModel(model)
    model.notifyModified(NlModel.ChangeType.EDIT)

    expected.remove(fragment1)
    destinations = AddDestinationMenu(surface).destinations
    assertEquals(destinations, expected)

    root.activity("activity2", name = "mytest.navtest.activity2")
    modelBuilder.updateModel(model)
    model.notifyModified(NlModel.ChangeType.EDIT)

    expected.remove(activity2)
    destinations = AddDestinationMenu(surface).destinations
    assertEquals(destinations, expected)

    root.activity("activity3", name = "mytest.navtest.activity3")
    modelBuilder.updateModel(model)
    model.notifyModified(NlModel.ChangeType.EDIT)

    expected.remove(activity3)
    destinations = AddDestinationMenu(surface).destinations
    assertEquals(destinations, expected)
  }

  private fun findClass(className: String) = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project))!!

  override fun tearDown() {
    _model = null
    _menu = null
    _surface = null
    super.tearDown()
  }

  fun testNewComponentSelected() {
    val gallery = menu.destinationsList
    val cell0Bounds = gallery.getCellBounds(0, 0)
    val destination = gallery.model.getElementAt(0) as Destination
    gallery.setSelectedValue(destination, false)
    gallery.dispatchEvent(MouseEvent(
      gallery, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
      cell0Bounds.centerX.toInt(), cell0Bounds.centerX.toInt(), 1, false))
    assertNotNull(destination.component)
    assertEquals(listOf(destination.component!!), surface.selectionModel.selection)
  }

  fun testUndoNewComponent() {
    val gallery = menu.destinationsList
    val cell0Bounds = gallery.getCellBounds(0, 0)
    val destination = gallery.model.getElementAt(0) as Destination
    gallery.setSelectedValue(destination, false)
    gallery.dispatchEvent(MouseEvent(
      gallery, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
      cell0Bounds.centerX.toInt(), cell0Bounds.centerX.toInt(), 1, false))

    // ordinarily this would be done by the resource change listener
    model.notifyModified(NlModel.ChangeType.EDIT)

    assertNotNull(destination.component)
    assertEquals(3, surface.currentNavigation.children.size)

    UndoManager.getInstance(project).undo(TestNavEditor(model.virtualFile, project))

    PsiDocumentManager.getInstance(project).commitAllDocuments()
    model.notifyModified(NlModel.ChangeType.EDIT)

    assertEquals(2, surface.currentNavigation.children.size)
  }

  fun testFiltering() {
    val gallery = menu.destinationsList
    val searchField = menu.searchField

    assertEquals(4, gallery.itemsCount)
    assertEquals("placeholder", (gallery.model.getElementAt(0) as Destination).label)
    assertEquals("BlankFragment", (gallery.model.getElementAt(1) as Destination).label)
    assertEquals("navigation.xml", (gallery.model.getElementAt(2) as Destination).label)
    assertEquals("activity_main", (gallery.model.getElementAt(3) as Destination).label)

    searchField.text = "v"
    assertEquals(2, gallery.itemsCount)
    assertEquals("navigation.xml", (gallery.model.getElementAt(0) as Destination).label)
    assertEquals("activity_main", (gallery.model.getElementAt(1) as Destination).label)

    searchField.text = "vig"
    assertEquals(1, gallery.itemsCount)
    assertEquals("navigation.xml", (gallery.model.getElementAt(0) as Destination).label)
  }

  fun testCreateBlank() {
    model.pendingIds.addAll(model.flattenComponents().map { it.id }.collect(Collectors.toList()))
    val event = mock(AnActionEvent::class.java)
    `when`(event.project).thenReturn(project)
    val action = object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
        TestCase.assertEquals(event, e)
        val createdFiles = DataManagerImpl().getDataContext(panel).getData(CREATED_FILES)!!
        val root = myModule.rootManager.contentRoots[0].path
        myFixture.addFileToProject("src/mytest/navtest/Frag.java",
                                   "package mytest.navtest\n" +
                                   "public class Frag extends android.support.v4.app.Fragment {}")
        myFixture.addFileToProject("res/layout/frag_layout.xml", "")
        createdFiles.add(File(root, "src/mytest/navtest/Frag.java"))
        createdFiles.add(File(root, "res/layout/frag_layout.xml"))
      }
    }
    TestNavUsageTracker.create(model).use { tracker ->
      menu.createNewDestination(event, action)

      val added = model.find("frag")!!
      assertEquals("fragment", added.tagName)
      assertEquals("@layout/frag_layout", added.layout)
      assertEquals("mytest.navtest.Frag", added.className)
      Mockito.verify(tracker).logEvent(NavEditorEvent.newBuilder().setType(CREATE_FRAGMENT).build())
      Mockito.verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                         .setType(ADD_DESTINATION)
                                         .setDestinationInfo(NavDestinationInfo.newBuilder()
                                                               .setHasClass(true)
                                                               .setHasLayout(true)
                                                               .setType(FRAGMENT)).build())
    }
  }

  fun testCreateBlankNoLayout() {
    model.pendingIds.addAll(model.flattenComponents().map { it.id }.collect(Collectors.toList()))
    val event = mock(AnActionEvent::class.java)
    `when`(event.project).thenReturn(project)
    val action = object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
        TestCase.assertEquals(event, e)
        val createdFiles = DataManagerImpl().getDataContext(panel).getData(CREATED_FILES)!!
        val root = myModule.rootManager.contentRoots[0].path
        myFixture.addFileToProject("src/mytest/navtest/Frag.java",
                                   "package mytest.navtest\n" +
                                   "public class Frag extends android.support.v4.app.Fragment {}")
        createdFiles.add(File(root, "src/mytest/navtest/Frag.java"))
      }
    }
    TestNavUsageTracker.create(model).use { tracker ->
      menu.createNewDestination(event, action)

      val added = model.find("frag")!!
      assertEquals("fragment", added.tagName)
      assertNull(added.layout)
      assertEquals("mytest.navtest.Frag", added.className)
      Mockito.verify(tracker).logEvent(NavEditorEvent.newBuilder().setType(CREATE_FRAGMENT).build())
      Mockito.verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                         .setType(ADD_DESTINATION)
                                         .setDestinationInfo(NavDestinationInfo.newBuilder()
                                                               .setHasClass(true)
                                                               .setType(FRAGMENT)).build())
    }
  }

  fun testCreatePlaceholder() {
    var gallery = menu.destinationsList
    val cell0Bounds = gallery.getCellBounds(1, 1)
    val destination = gallery.model.getElementAt(0) as Destination
    gallery.setSelectedValue(destination, false)
    TestNavUsageTracker.create(model).use { tracker ->
      gallery.dispatchEvent(MouseEvent(
        gallery, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
        cell0Bounds.centerX.toInt(), cell0Bounds.centerX.toInt(), 1, false))
      val component = destination.component
      assertNotNull(component)
      assertEquals(listOf(component!!), surface.selectionModel.selection)
      assertEquals("placeholder", component.id)
      assertNull(component.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT))
      assertNull(component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME))

      Mockito.verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                         .setType(ADD_DESTINATION)
                                         .setDestinationInfo(NavDestinationInfo.newBuilder().setType(FRAGMENT)).build())

      getMainMenuPanel()
      gallery = menu.destinationsList
      val destination2 = gallery.model.getElementAt(0) as Destination
      gallery.setSelectedValue(destination2, false)
      gallery.dispatchEvent(MouseEvent(
        gallery, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
        cell0Bounds.centerX.toInt(), cell0Bounds.centerX.toInt(), 1, false))
      val component2 = destination2.component
      assertNotNull(component2)
      assertEquals(listOf(component2!!), surface.selectionModel.selection)
      assertEquals("placeholder2", component2.id)
      assertContainsElements(surface.model?.components?.get(0)?.children?.map { it.id }!!, "placeholder", "placeholder2")
    }
  }

  private fun getMainMenuPanel(): JPanel {
    val res = menu.mainPanel
    // We kick off a worker thread to load the destinations and then update the list in the ui thread, so we have to wait and dispatch
    // events until it's set.
    while (true) {
      if (!_menu!!.destinationsList.isEmpty) {
        break
      }
      Thread.sleep(10L)
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
    return res
  }

  fun testAddDestination() {
    val destination = mock(Destination::class.java)
    val component = model.find("fragment")!!
    `when`(destination.component).thenReturn(component)
    TestNavUsageTracker.create(model).use { tracker ->
      menu.addDestination(destination)
      verify(destination).addToGraph()
      Mockito.verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                         .setType(ADD_DESTINATION)
                                         .setDestinationInfo(NavDestinationInfo.newBuilder().setType(FRAGMENT)).build())
    }
  }

  fun testAddInclude() {
    val destination = mock(Destination::class.java)
    val component = spy(model.find("fragment")!!)
    `when`(component.tagName).thenReturn(TAG_INCLUDE)
    `when`(destination.component).thenReturn(component)
    TestNavUsageTracker.create(model).use { tracker ->
      menu.addDestination(destination)
      verify(destination).addToGraph()
      Mockito.verify(tracker).logEvent(NavEditorEvent.newBuilder().setType(ADD_INCLUDE).build())
    }
  }

  private fun addFragment(name: String) {
    addDestination(name, "android.support.v4.app.Fragment")
  }

  private fun addActivityWithNavHost(name: String) {
    addActivity(name)
    val relativePath = "res/layout/$name.xml"
    val fileText = """
      <?xml version="1.0" encoding="utf-8"?>
      <android.support.constraint.ConstraintLayout xmlns:tools="http://schemas.android.com/tools"
                                                   xmlns:app="http://schemas.android.com/apk/res-auto"
                                                   xmlns:android="http://schemas.android.com/apk/res/android"
                                                   tools:context=".$name">
        <fragment
            android:id="@+id/navhost"
            android:name="androidx.navigation.fragment.NavHostFragment"
            app:defaultNavHost="true"
            app:navGraph="@navigation/nav"/>

      </android.support.constraint.ConstraintLayout>
    """.trimIndent()

    myFixture.addFileToProject(relativePath, fileText)
  }

  private fun addActivityWithLayout(name: String) {
    addActivity(name)
    val relativePath = "res/layout/$name.xml"
    val fileText = """
      <?xml version="1.0" encoding="utf-8"?>
      <android.support.constraint.ConstraintLayout xmlns:tools="http://schemas.android.com/tools"
                                                   tools:context=".$name"/>
    """.trimIndent()

    myFixture.addFileToProject(relativePath, fileText)
  }

  private fun addActivity(name: String) {
    addDestination(name, "android.app.Activity")
  }

  private fun addDestination(name: String, parentClass: String) {
    val relativePath = "src/mytest/navtest/$name.java"
    val fileText = """
      .package mytest.navtest;
      .import $parentClass;
      .
      .public class $name extends ${parentClass.substringAfterLast('.')} {
      .}
      """.trimMargin(".")

    myFixture.addFileToProject(relativePath, fileText)
  }

  private fun addIncludeFile(name: String) {
    val relativePath = "res/navigation/$name.xml"
    val fileText = """
      .<?xml version="1.0" encoding="utf-8"?>
      .<navigation xmlns:android="http://schemas.android.com/apk/res/android"
          .xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/testnav">

      .</navigation>
      """.trimMargin(".")

    myFixture.addFileToProject(relativePath, fileText)
  }
}
