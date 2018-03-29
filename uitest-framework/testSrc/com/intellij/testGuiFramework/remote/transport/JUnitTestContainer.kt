/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.remote.transport

import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.TargetBuildSystem
import java.io.Serializable

/**
 * [JUnitTestContainer] holds a description of a test to run for transmission between the server and client. The [segmentIndex] field is
 * only used in the case of a RESUME_TEST message, in which case it holds the number of times the IDE has been restarted in this test method.
 * While the test could handle inspecting and interpreting this value, restartIdeBetween() in RestartUtils.kt provides a more convenient interface.
 *
 * @author Sergey Karashevich
 */
data class JUnitTestContainer(val testClass: Class<*>, val methodName: String, val segmentIndex: Int = 0,
                              val buildSystem: TargetBuildSystem.BuildSystem = TargetBuildSystem.BuildSystem.GRADLE) : Serializable