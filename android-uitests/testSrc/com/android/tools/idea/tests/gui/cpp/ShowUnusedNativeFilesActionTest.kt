package com.android.tools.idea.tests.gui.cpp

import com.android.flags.junit.RestoreFlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.NewFilePopupFixture
import com.google.common.truth.Truth
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.core.MouseButton
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class ShowUnusedNativeFilesActionTest {
  @get:Rule
  val guiTest = GuiTestRule().withTimeout(5, TimeUnit.MINUTES)

  @get:Rule
  val restoreNpwNativeModuleFlagRule = RestoreFlagRule(StudioFlags.NPW_NEW_NATIVE_MODULE)

  @Test
  fun actionShouldToggleVisibilityOfUnusedFiles() {
    // Set up a normal C++ project
    guiTest.welcomeFrame()
      .createNewProject()
      .chooseAndroidProjectStep
      .chooseActivity("Native C++")
      .wizard()
      .clickNext()
      .configureNewAndroidProjectStep
      .enterPackageName("dev.tools")
      .wizard()
      .clickNext()
      .clickFinishAndWaitForSyncToFinish()

    // Add an unused C file
    val unusedFile1 = guiTest.projectPath.resolve("app/src/main/cpp/unused1.c")
    unusedFile1.writeText("int i1 = 1;")

    val ideFrame = guiTest.ideFrame()
    ideFrame.requestProjectSync()

    val projectView = ideFrame.projectView
    val androidPane = projectView.selectAndroidPane()

    androidPane.doubleClickPath("app", "cpp")

    // Check the file is shown since by default we show all unused files
    Truth.assertThat(androidPane.hasPath("app", "cpp", "unused1.c")).isTrue()

    // Turn off show unused files
    projectView.showOptionsMenu()
    GuiTests.clickPopupMenuItem("Show Unused Native Files", ideFrame.target(), ideFrame.robot())

    // Check the file is now hidden
    Truth.assertThat(androidPane.hasPath("app", "cpp", "unused1.c")).isFalse()

    // Add a new file inside the IDE
    androidPane.clickPath(MouseButton.RIGHT_BUTTON, "app", "cpp")
      .openFromContextualMenu(NewFilePopupFixture::find, "New", "File")
      .setFilePath("unused2.c")
      .pressEnter()
    androidPane.doubleClickPath("app", "cpp")

    ideFrame.editor
      .enterText("int i2 = 1;")
      .invokeAction(EditorFixture.EditorAction.SAVE)
    GuiTests.waitForBackgroundTasks(ideFrame.robot())

    // Check that the new file is shown
    Truth.assertThat(androidPane.hasPath("app", "cpp", "unused2.c")).isTrue()

    // And the new file should be hidden after sync since it's not used.
    ideFrame.requestProjectSync()
    GuiTests.waitForBackgroundTasks(ideFrame.robot())

    Truth.assertThat(androidPane.hasPath("app", "cpp", "unused2.c")).isFalse()
  }
}