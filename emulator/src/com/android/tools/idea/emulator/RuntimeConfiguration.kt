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
package com.android.tools.idea.emulator

import com.android.tools.idea.io.IdeFileUtils
import com.intellij.util.SystemProperties
import io.grpc.ManagedChannelBuilder
import io.grpc.netty.NettyChannelBuilder
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A container of properties that may have different values in production environment and in tests.
 */
open class RuntimeConfiguration {

  open fun getDesktopOrUserHomeDirectory(): Path {
    return IdeFileUtils.getDesktopDirectory() ?: Paths.get(SystemProperties.getUserHome())
  }

  open fun newGrpcChannelBuilder(host: String, port: Int): ManagedChannelBuilder<*> {
    return NettyChannelBuilder.forAddress(host, port)
  }
}
