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
package com.android.tools.idea.serverflags

import com.android.tools.idea.ServerFlagList
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import java.io.File
import java.io.IOException
import java.nio.file.Path

const val FILE_NAME = "serverflaglist.protobuf"
const val DIRECTORY_PREFIX = "serverflags"
private const val VERSION_OVERRIDE_KEY = "studio.server.flags.version.override"

val localCacheDirectory: Path
  get() = File(PathManager.getSystemPath()).toPath().resolve(DIRECTORY_PREFIX)

fun buildLocalFilePath(localCacheDirectory: Path, version: String): Path = localCacheDirectory.resolve(version).resolve(FILE_NAME)

val flagsVersion: String
  get() = System.getProperty(VERSION_OVERRIDE_KEY, ApplicationInfo.getInstance().versionString)

fun unmarshalFlagList(file: File): ServerFlagList? {
  return try {
    file.inputStream().use { ServerFlagList.parseFrom(it) }
  }
  catch (e: IOException) {
    null
  }
}

private val ApplicationInfo.versionString: String
  get() {
    val major = majorVersion ?: return ""
    val minor = minorVersion ?: return ""
    val micro = microVersion ?: return ""
    val patch = patchVersion ?: return ""
    return "$major.$minor.$micro.$patch"
  }