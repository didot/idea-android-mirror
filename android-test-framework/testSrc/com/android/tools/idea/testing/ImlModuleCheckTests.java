/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.testing;

import java.lang.reflect.Method;
import java.util.ArrayList;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.junit.Assume.assumeNotNull;

/**
 * Tests that validate that all the tests inside a provided jar meet certain
 * criteria, e.g. tests are not based on AndroidGradleTestCase.
 *
 * Each validation check is encoded as a JUnit test in this class. Inputs to this
 * test (such as the jar to validate) are passed in via system properties.
 */
public class ImlModuleCheckTests {

  /** A bazel label for a jar file containing tests. */
  static final String TEST_LIB_JAR = System.getProperty("testlib");
  /** Enables tests which will fail prevent gradle project tests being added to testlib. */
  static final boolean DISALLOW_GRADLE_PROJECT_TESTS = Boolean.getBoolean("disallow_gradle_project_tests");
  /** A bazel label of a text file containing gradle project tests allowed to exist inside testlib. */
  static final String GRADLE_PROJECT_TESTS_ALLOWLIST = System.getProperty("gradle_project_tests_allowlist");
  /** A bazel label of the module expected to contain gradle project tests. */
  static final String AGP_TEST_MODULE = System.getProperty("agp_test_module");

  @AfterClass
  public static void writeGradleProjectTests() throws Exception {
    assumeNotNull(TEST_LIB_JAR);
    Set<Class<?>> gradleBasedTests = getGradleBasedTests();
    List<String> classNames = gradleBasedTests.stream()
      .map(Class::getName)
      .sorted()
      .collect(Collectors.toList());

    Path gradleTestFile = getTestOutputDir().resolve("gradle-project-tests.txt");
    List<String> content = new ArrayList<>();
    content.add("# This file is automatically generated by ImlModuleCheckTests.");
    content.add("# The class names below are all gradle project tests which subclass");
    content.add("# AndroidGradleTestCase or use the AndroidGradleProjectRule.");
    content.add("#");
    content.add("# Please do not add to this list. Instead add gradle project tests");
    content.add("# to " + AGP_TEST_MODULE);
    content.addAll(classNames);

    Files.write(gradleTestFile, content);
  }

  @Test
  public void gradleProjectTests_noAgpBasedTests() throws Exception {
    assumeTrue(DISALLOW_GRADLE_PROJECT_TESTS);
    Set<String> allowedClassNames = getGradleProjectTestsAllowlist();
    Set<Class<?>> gradleTestClasses = getGradleBasedTests();

    Set<String> notInAllowList = gradleTestClasses.stream()
      .map(Class::getName)
      .filter(n -> !allowedClassNames.contains(n))
      .collect(Collectors.toSet());

    String failureMsg = "New tests using AndroidGradleTestClass or AndroidGradleProjectRule " +
                        "cannot be added to this module. Please add the following tests to " +
                        AGP_TEST_MODULE + ":\n * " +
                        String.join("\n * ", notInAllowList);
    Assert.assertTrue(failureMsg, notInAllowList.isEmpty());
  }

  @Test
  public void gradleProjectTests_allowlistOutOfDate() throws Exception {
    assumeTrue(DISALLOW_GRADLE_PROJECT_TESTS);
    Set<String> allowedClassNames = getGradleProjectTestsAllowlist();
    assumeFalse(allowedClassNames.isEmpty());
    Set<String> gradleTestNames = getGradleBasedTests()
      .stream()
      .map(Class::getName)
      .collect(Collectors.toSet());

    allowedClassNames.removeAll(gradleTestNames);

    String failureMsg = "gradle_project_tests_allowlist is out of date. Please remove the " +
                        "following tests from " + GRADLE_PROJECT_TESTS_ALLOWLIST + ":\n * " +
                        String.join("\n * ", allowedClassNames);
    Assert.assertTrue(failureMsg, allowedClassNames.isEmpty());
  }

  private static Set<String> getGradleProjectTestsAllowlist() throws IOException {
    if (GRADLE_PROJECT_TESTS_ALLOWLIST == null) {
      return Collections.emptySet();
    }
    return Files.readAllLines(Paths.get(normalizeBazelLabel(GRADLE_PROJECT_TESTS_ALLOWLIST)))
      .stream()
      // eliminate any lines that start with a comment
      .filter(line -> !line.startsWith("#"))
      .collect(Collectors.toSet());
  }

  /**
   * Returns a file system path for a bazel label.
   *
   * <p>Does not work for external workspaces containing the "@" prefix.
   */
  private static String normalizeBazelLabel(String label) {
    return label.replaceAll("//", "").replaceAll(":", File.separator);
  }

  private static Path getTestOutputDir() {
    return Paths.get(System.getenv("TEST_UNDECLARED_OUTPUTS_DIR"));
  }

  private static Set<Class<?>> loadImlModuleClasses() throws IOException, ClassNotFoundException {
    String testlib = TEST_LIB_JAR.replace("//", "").replace(":", "/");
    return loadClasses(Paths.get(testlib));
  }

  private static Set<Class<?>> getGradleBasedTests() throws IOException, ClassNotFoundException {
    return loadImlModuleClasses()
        .stream()
        .filter(cls -> inheritsAndroidGradleTestCase(cls) || usesAndroidGradleProjectRule(cls))
        .collect(Collectors.toSet());
  }

  private static boolean inheritsAndroidGradleTestCase(Class<?> cls) {
    while (cls.getSuperclass() != null) {
      Class<?> superClass = cls.getSuperclass();
      if (superClass.equals(AndroidGradleTestCase.class)) {
        return true;
      }
      cls = cls.getSuperclass();
    }
    return false;
  }

  private static boolean usesAndroidGradleProjectRule(Class<?> cls) {
    Field[] fields = cls.getFields();
    for (Field field : fields) {
      if (field.getDeclaringClass().equals(AndroidGradleProjectRule.class)) {
        return true;
      }
    }
    // find usages of @get.Rule used in kotlin
    for (Method method : cls.getMethods()) {
      if (method.getAnnotation(Rule.class) != null && method.getReturnType().equals(AndroidGradleProjectRule.class)) {
        return true;
      }
    }
    return false;
  }

  private static Set<Class<?>> loadClasses(Path jar) throws IOException, ClassNotFoundException {
    URLClassLoader
      urlClassLoader
      = URLClassLoader.newInstance(new URL[]{jar.toUri().toURL()});

    Set<Class<?>> classes = new HashSet<>();
    try (JarInputStream jis = new JarInputStream(new FileInputStream(jar.toString()))) {
      while (true) {
        JarEntry jarEntry = jis.getNextJarEntry();
        if (jarEntry == null) {
          break;
        }
        if (jarEntry.getName().endsWith(".class")) {
          String clsName = jarEntry.getName().replaceAll("/", ".").replaceAll(".class$", "");
          Class<?> clz = urlClassLoader.loadClass(clsName);
          classes.add(clz);
        }
      }
    }
    return classes;
  }
}
