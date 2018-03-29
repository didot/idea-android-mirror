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

import java.io.Serializable
import java.util.concurrent.ThreadLocalRandom

/**
 * @author Sergey Karashevich
 */

enum class MessageType {RUN_TEST, CLOSE_IDE, JUNIT_INFO, RESTART_IDE, RESTART_IDE_AND_RESUME, RESUME_TEST, KEEP_ALIVE }
data class TransportMessage(val type: MessageType, val content: Any? = null, val id: Long = ThreadLocalRandom.current().nextLong()): Serializable

