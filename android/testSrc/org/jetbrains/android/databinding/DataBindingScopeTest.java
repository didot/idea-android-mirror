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
package org.jetbrains.android.databinding;

import com.android.SdkConstants;
import com.android.ide.common.blame.Message;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_DATA_BINDING;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_DATA_BINDING_AND_SIMPLE_LIB;

/**
 * This class compiles a real project with data binding then checks whether the generated Binding classes match the virtual ones.
 * This test requires DataBinding's rebuildRepo task to be run first otherwise it will fail because it won't find the snapshot versions.
 */
public class DataBindingScopeTest extends AndroidGradleTestCase {

  @Override
  protected void prepareProjectForImport(@NotNull String relativePath) throws IOException {
    super.prepareProjectForImport(relativePath);
    createGradlePropertiesFile(getProjectFolderPath());
    updateGradleVersions(getProjectFolderPath(), getLocalRepositories() + getLocalRepository("out/repo"),
                         SdkConstants.GRADLE_PLUGIN_WH_VERSION, "25.0.0");
  }

  private static void createGradlePropertiesFile(@NotNull File projectFolder) throws IOException {
    File dataBindingRoot = new File(getTestDataPath(), "/../../../../data-binding/internal-prebuilts");
    File out = new File(projectFolder, "databinding.props");
    FileUtils.writeStringToFile(out, "internalDataBindingRepo=" + dataBindingRoot.getCanonicalPath());
  }

  public void testDummy() {
    // placehlder to make PSQ happy until the test below can be re-enabled.
  }

  // TODO re-enable when we can run tests with top of tree gradle
  public void ignored_testAccessFromInaccessibleScope() throws Exception {
    loadProject(PROJECT_WITH_DATA_BINDING_AND_SIMPLE_LIB);
    // temporary fix until test model can detect dependencies properly
    GradleInvocationResult assembleDebug = invokeGradleTasks(getProject(), "assembleDebug");
    GradleSyncState syncState = GradleSyncState.getInstance(getProject());
    assertFalse(syncState.isSyncNeeded().toBoolean());
    assertTrue(myAndroidFacet.isDataBindingEnabled());
    assertTrue(myModules.hasModule("lib"));
    assertTrue(myModules.hasModule("lib2"));
    // app depends on lib depends on lib2

    // trigger initialization
    myAndroidFacet.getModuleResources(true);

    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(getProject());
    String appBindingClassName = "com.android.example.appwithdatabinding.databinding.ActivityMainBinding";
    assertNotNull(javaPsiFacade.findClass(appBindingClassName, myAndroidFacet.getModule().getModuleWithDependenciesScope()));
    assertNull(javaPsiFacade.findClass(appBindingClassName, myModules.getModule("lib").getModuleWithDependenciesScope()));
    assertNull(javaPsiFacade.findClass(appBindingClassName, myModules.getModule("lib2").getModuleWithDependenciesScope()));

    // only exists in lib
    String libLayoutBindingClassName = "com.foo.bar.databinding.LibLayoutBinding";
    assertNotNull(javaPsiFacade.findClass(libLayoutBindingClassName, myAndroidFacet.getModule().getModuleWithDependenciesScope()));
    assertNotNull(javaPsiFacade.findClass(libLayoutBindingClassName, myModules.getModule("lib").getModuleWithDependenciesScope()));
    assertNull(javaPsiFacade.findClass(libLayoutBindingClassName, myModules.getModule("lib2").getModuleWithDependenciesScope()));

    // only exists in lib2
    String lib2LayoutBindingClassName = "com.foo.bar2.databinding.Lib2LayoutBinding";
    assertNotNull(javaPsiFacade.findClass(lib2LayoutBindingClassName, myAndroidFacet.getModule().getModuleWithDependenciesScope()));
    assertNotNull(javaPsiFacade.findClass(lib2LayoutBindingClassName, myModules.getModule("lib").getModuleWithDependenciesScope()));
    assertNotNull(javaPsiFacade.findClass(lib2LayoutBindingClassName, myModules.getModule("lib2").getModuleWithDependenciesScope()));

  }
}
