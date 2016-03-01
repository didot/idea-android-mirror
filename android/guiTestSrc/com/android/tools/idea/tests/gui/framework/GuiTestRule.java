/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.WaitTimedOutError;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AssumptionViolatedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.tools.idea.tests.gui.framework.GuiTests.*;
import static com.google.common.base.Preconditions.checkState;
import static com.intellij.openapi.project.ProjectCoreUtil.DIRECTORY_BASED_PROJECT_DIR;
import static com.intellij.openapi.util.io.FileUtil.copyDir;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.io.FileUtilRt.delete;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static junit.framework.Assert.assertNotNull;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assume.assumeTrue;

public class GuiTestRule implements TestRule {
  private static final Timeout DEFAULT_TIMEOUT = new Timeout(5, TimeUnit.MINUTES);

  private File myProjectPath;

  private final Timeout myTimeout;
  private final RobotTestRule myRobotTestRule = new RobotTestRule();
  private final RuleChain myRuleChain = RuleChain.emptyRuleChain()
    .around(new BlockReloading())
    .around(myRobotTestRule)
    .around(new IdeHandling())
    .around(new TestPerformance())
    .around(new ScreenshotOnFailure());

  public GuiTestRule() {
    myTimeout = DEFAULT_TIMEOUT;
  }

  public GuiTestRule(Timeout timeout) {
    myTimeout = timeout;
  }

  @NotNull
  @Override
  public Statement apply(final Statement base, final Description description) {
    return myRuleChain.around(myTimeout).apply(base, description);
  }

  private class IdeHandling implements TestRule {
    @NotNull
    @Override
    public Statement apply(final Statement base, final Description description) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          System.out.println("Starting " + description.getDisplayName());
          assumeTrue("An IDE internal error occurred previously.", fatalErrorsFromIde().isEmpty());
          assumeOnlyWelcomeFrameShowing();
          setUp();
          List<Throwable> errors = new ArrayList<Throwable>();
          try {
            base.evaluate();
          } catch (MultipleFailureException e) {
            errors.addAll(e.getFailures());
          } catch (Throwable e) {
            errors.add(e);
          } finally {
            tearDown(errors);
          }
          MultipleFailureException.assertEmpty(errors);
        }
      };
    }
  }

  private void assumeOnlyWelcomeFrameShowing() {
    try {
      WelcomeFrameFixture.find(robot());
    } catch (WaitTimedOutError e) {
      throw new AssumptionViolatedException("didn't find welcome frame", e);
    }
    List<Window> windowsShowing = windowsShowing();
    assumeTrue("windows showing: " + windowsShowing, windowsShowing.size() == 1);
  }

  private void setUp() {
    setUpDefaultProjectCreationLocationPath();
    setIdeSettings();
    setUpSdks();
  }

  private void tearDown(List<Throwable> errors) throws Exception {
    waitForBackgroundTasks();
    errors.addAll(cleanUpAndCheckForModalDialogs());
    closeAllProjects();
    if (myProjectPath != null) {
      delete(myProjectPath);
      refreshFiles();
    }
    errors.addAll(fatalErrorsFromIde());
  }

  private List<AssertionError> cleanUpAndCheckForModalDialogs() {
    List<AssertionError> errors = new ArrayList<AssertionError>();
    // We close all modal dialogs left over, because they block the AWT thread and could trigger a deadlock in the next test.
    Dialog modalDialog;
    while ((modalDialog = getActiveModalDialog()) != null) {
      robot().close(modalDialog);
      errors.add(new AssertionError(
        String.format("Modal dialog showing: %s with title '%s'", modalDialog.getClass().getName(), modalDialog.getTitle())));
    }
    return errors;
  }

  // Note: this works with a cooperating window manager that returns focus properly. It does not work on bare Xvfb.
  private static Dialog getActiveModalDialog() {
    Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    if (activeWindow instanceof Dialog) {
      Dialog dialog = (Dialog)activeWindow;
      if (dialog.getModalityType() == Dialog.ModalityType.APPLICATION_MODAL) {
        return dialog;
      }
    }
    return null;
  }

  public void importSimpleApplication() throws IOException {
    importProjectAndWaitForProjectSyncToFinish("SimpleApplication");
  }

  public void importMultiModule() throws IOException {
    importProjectAndWaitForProjectSyncToFinish("MultiModule");
  }

  public void importProjectAndWaitForProjectSyncToFinish(@NotNull String projectDirName) throws IOException {
    importProjectAndWaitForProjectSyncToFinish(projectDirName, null);
  }

  public void importProjectAndWaitForProjectSyncToFinish(@NotNull String projectDirName, @Nullable String gradleVersion)
    throws IOException {
    importProject(projectDirName, gradleVersion);
    ideFrame().waitForGradleProjectSyncToFinish();
  }

  public void importProject(@NotNull String projectDirName) throws IOException {
    importProject(projectDirName, null);
  }

  private void importProject(@NotNull String projectDirName, String gradleVersion) throws IOException {
    setUpProject(projectDirName, gradleVersion);
    final VirtualFile toSelect = findFileByIoFile(myProjectPath, true);
    assertNotNull(toSelect);
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        GradleProjectImporter.getInstance().importProject(toSelect);
      }
    });
  }

  /**
   * Sets up a project before using it in a UI test:
   * <ul>
   * <li>Makes a copy of the project in testData/guiTests/newProjects (deletes any existing copy of the project first.) This copy is
   * the one the test will use.</li>
   * <li>Creates a Gradle wrapper for the test project.</li>
   * <li>Updates the version of the Android Gradle plug-in used by the project, if applicable</li>
   * <li>Creates a local.properties file pointing to the Android SDK path specified by the system property (or environment variable)
   * 'ADT_TEST_SDK_PATH'</li>
   * <li>Copies over missing files to the .idea directory (if the project will be opened, instead of imported.)</li>
   * <li>Deletes .idea directory, .iml files and build directories, if the project will be imported.</li>
   * <p/>
   * </ul>
   *
   * @param projectDirName             the name of the project's root directory. Tests are located in testData/guiTests.
   * @param gradleVersion              the Gradle version to use in the wrapper. If {@code null} is passed, this method will use the latest supported
   *                                   version of Gradle.
   * @throws IOException if an unexpected I/O error occurs.
   */
  private void setUpProject(@NotNull String projectDirName,
                            @Nullable String gradleVersion) throws IOException {
    copyProjectBeforeOpening(projectDirName);

    File gradlePropertiesFilePath = new File(myProjectPath, SdkConstants.FN_GRADLE_PROPERTIES);
    if (gradlePropertiesFilePath.isFile()) {
      delete(gradlePropertiesFilePath);
    }

    if (gradleVersion == null) {
      createGradleWrapper(myProjectPath, GRADLE_LATEST_VERSION);
    }
    else {
      createGradleWrapper(myProjectPath, gradleVersion);
    }

    updateGradleVersions(myProjectPath);
    updateLocalProperties(myProjectPath);
    cleanUpProjectForImport(myProjectPath);
  }

  public void copyProjectBeforeOpening(@NotNull String projectDirName) throws IOException {
    File masterProjectPath = getMasterProjectDirPath(projectDirName);

    setProjectPath(getTestProjectDirPath(projectDirName));
    if (myProjectPath.isDirectory()) {
      delete(myProjectPath);
    }
    copyDir(masterProjectPath, myProjectPath);
    System.out.println(String.format("Copied project '%1$s' to path '%2$s'", projectDirName, myProjectPath.getPath()));
  }

  protected boolean createGradleWrapper(@NotNull File projectDirPath, @NotNull String gradleVersion) throws IOException {
    return GradleUtil.createGradleWrapper(projectDirPath, gradleVersion);
  }

  protected void updateLocalProperties(File projectPath) throws IOException {
    File androidHomePath = IdeSdks.getAndroidSdkPath();
    assertNotNull(androidHomePath);

    LocalProperties localProperties = new LocalProperties(projectPath);
    localProperties.setAndroidSdkPath(androidHomePath);
    localProperties.save();
  }

  protected void updateGradleVersions(@NotNull File projectPath) throws IOException {
    AndroidGradleTestCase.updateGradleVersions(projectPath);
  }

  @NotNull
  protected File getMasterProjectDirPath(@NotNull String projectDirName) {
    return new File(getTestProjectsRootDirPath(), projectDirName);
  }

  @NotNull
  protected File getTestProjectDirPath(@NotNull String projectDirName) {
    return new File(getProjectCreationDirPath(), projectDirName);
  }

  public void cleanUpProjectForImport(@NotNull File projectPath) {
    File dotIdeaFolderPath = new File(projectPath, DIRECTORY_BASED_PROJECT_DIR);
    if (dotIdeaFolderPath.isDirectory()) {
      File modulesXmlFilePath = new File(dotIdeaFolderPath, "modules.xml");
      if (modulesXmlFilePath.isFile()) {
        SAXBuilder saxBuilder = new SAXBuilder();
        try {
          Document document = saxBuilder.build(modulesXmlFilePath);
          XPath xpath = XPath.newInstance("//*[@fileurl]");
          //noinspection unchecked
          List<Element> modules = xpath.selectNodes(document);
          int urlPrefixSize = "file://$PROJECT_DIR$/".length();
          for (Element module : modules) {
            String fileUrl = module.getAttributeValue("fileurl");
            if (!StringUtil.isEmpty(fileUrl)) {
              String relativePath = toSystemDependentName(fileUrl.substring(urlPrefixSize));
              File imlFilePath = new File(projectPath, relativePath);
              if (imlFilePath.isFile()) {
                delete(imlFilePath);
              }
              // It is likely that each module has a "build" folder. Delete it as well.
              File buildFilePath = new File(imlFilePath.getParentFile(), "build");
              if (buildFilePath.isDirectory()) {
                delete(buildFilePath);
              }
            }
          }
        }
        catch (Throwable ignored) {
          // if something goes wrong, just ignore. Most likely it won't affect project import in any way.
        }
      }
      delete(dotIdeaFolderPath);
    }
  }

  public void waitForBackgroundTasks() {
    GuiTests.waitForBackgroundTasks(robot());
  }

  public Robot robot() {
    return myRobotTestRule.getRobot();
  }

  public void setProjectPath(@NotNull File projectPath) {
    myProjectPath = projectPath;
  }

  @NotNull
  public File getProjectPath() {
    checkState(myProjectPath != null, "No project path set. Was a project imported?");
    return myProjectPath;
  }

  @NotNull
  public IdeFrameFixture ideFrame() {
    return IdeFrameFixture.find(robot(), getProjectPath(), null);
  }
}
