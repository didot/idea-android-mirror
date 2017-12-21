import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

apply { plugin("kotlin") }

val androidSdk by configurations.creating

dependencies {
    compileOnly(project(":kotlin-reflect-api"))
    compile(project(":compiler:util"))
    compile(project(":compiler:light-classes"))
    compile(project(":compiler:frontend"))
    compile(project(":compiler:frontend.java"))
    compile(project(":idea"))
    compile(project(":idea:idea-jvm"))
    compile(project(":idea:idea-core"))
    compile(project(":idea:ide-common"))
    compile(project(":idea:idea-gradle"))

    compile(androidDxJar())

    compileOnly(intellijDep()) { includeJars("openapi", "idea", "extensions", "util", "guava-21.0") }
    compileOnly(intellijPluginDep("android")) {
        includeJars("android", "android-common", "sdk-common", "sdklib", "sdk-tools", "layoutlib-api")
    }

    testCompile(projectDist(":kotlin-test:kotlin-test-jvm"))
    testCompile(project(":idea:idea-test-framework")) { isTransitive = false }
    testCompile(project(":plugins:lint")) { isTransitive = false }
    testCompile(project(":idea:idea-jvm"))
    testCompile(projectTests(":compiler:tests-common"))
    testCompile(projectTests(":idea"))
    testCompile(projectTests(":idea:idea-gradle"))
    testCompile(commonDep("junit:junit"))

    testCompile(intellijDep()) { includeJars("gson-2.5") }
    testCompile(intellijPluginDep("properties"))
    testCompileOnly(intellijPluginDep("android")) {
        includeJars("android", "android-common", "sdk-common", "sdklib", "sdk-tools", "layoutlib-api")
    }

    testRuntime(projectDist(":kotlin-reflect"))
    testRuntime(project(":plugins:android-extensions-ide"))
    testRuntime(project(":plugins:kapt3-idea"))
    testRuntime(project(":sam-with-receiver-ide-plugin"))
    testRuntime(project(":noarg-ide-plugin"))
    testRuntime(project(":allopen-ide-plugin"))

    testRuntime(intellijCoreDep()) { includeJars("intellij-core") }
    testRuntime(intellijDep())
    testRuntime(intellijPluginDep("android"))
    testRuntime(intellijPluginDep("copyright"))
    testRuntime(intellijPluginDep("coverage"))
    testRuntime(intellijPluginDep("gradle"))
    testRuntime(intellijPluginDep("Groovy"))
    testRuntime(intellijPluginDep("IntelliLang"))
    testRuntime(intellijPluginDep("java-decompiler"))
    testRuntime(intellijPluginDep("java-i18n"))
    testRuntime(intellijPluginDep("junit"))
    testRuntime(intellijPluginDep("maven"))
    testRuntime(intellijPluginDep("testng"))

    androidSdk(project(":custom-dependencies:android-sdk", configuration = "androidSdk"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}

projectTest {
    dependsOn(androidSdk)
    workingDir = rootDir
    doFirst {
        systemProperty("android.sdk", androidSdk.singleFile.canonicalPath)
    }
}

testsJar {}

