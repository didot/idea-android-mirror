load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.testFramework",
    data = ["//prebuilts/studio/jdk:mock-jdk17"],
    iml_files = ["intellij.android.testFramework.iml"],
    test_agents = ["//tools/base/threading-agent:threading_agent.jar"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-gradle",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-lang-databinding:intellij.android.lang-databinding[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module]",
        "//tools/adt/idea/project-system-gradle-sync:intellij.android.projectSystem.gradle.sync[module, test]",
        "//tools/adt/idea/project-system-gradle-upgrade:intellij.android.projectSystem.gradle.upgrade[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-junit",
        "//tools/base/resource-repository:studio.android.sdktools.resource-repository[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module]",
        "//tools/adt/idea/android-navigator:intellij.android.navigator[module]",
        "//tools/base/lint:studio.android.sdktools.lint-api[module, test]",
        "//tools/base/threading-agent-callback:studio.android.sdktools.threading-agent-callback[module, test]",
    ],
)
