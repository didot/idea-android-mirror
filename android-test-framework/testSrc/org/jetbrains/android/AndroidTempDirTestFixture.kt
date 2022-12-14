/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android

import com.android.utils.FileUtils
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import java.io.File
import java.nio.file.Path

open class AndroidTempDirTestFixture(
  private val testName: String
) : TempDirTestFixtureImpl() {
  override fun doCreateTempDirectory(): Path {
    val folder = File(getRootTempDirectory(), FileUtil.sanitizeFileName(testName.replace('$', '.'), false))
    FileUtils.mkdirs(folder)
    return folder.toPath()
  }

  open fun getRootTempDirectory() = FileUtil.getTempDirectory()

  val projectDir: File get() = File(tempDirPath)
}
