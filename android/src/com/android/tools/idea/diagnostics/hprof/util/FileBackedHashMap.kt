/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.hprof.util

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class FileBackedHashMap(
  private val buffer: ByteBuffer,
  private val keySize: Int,
  private val valueSize: Int
) {
  private val bucketCount: Int
  private val bucketSize = keySize + valueSize
  private var filledBuckets = 0

  init {
    val fileSize = buffer.remaining()
    assert(fileSize % bucketSize == 0)
    bucketCount = fileSize / bucketSize
  }

  companion object {
    fun createEmpty(channel: FileChannel, size: Int, keySize: Int, valueSize: Int): FileBackedHashMap {
      if (keySize != 4 && keySize != 8) {
        throw IllegalArgumentException("keySize must be 4 or 8.")
      }
      if (valueSize < 0) {
        throw IllegalArgumentException("valueSize must be positive.")
      }
      createEmptyFile(channel, (size * 1.33).toInt() * (keySize + valueSize))
      val buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, channel.size())
      return FileBackedHashMap(buffer, keySize, valueSize)
    }

    fun createEmptyFile(channel: FileChannel, size: Int) {
      val emptyBuf = ByteBuffer.allocateDirect(60_000)
      var remaining = size
      while (remaining > 0) {
        val toWrite = Math.min(emptyBuf.remaining(), remaining)
        if (toWrite == emptyBuf.remaining()) {
          channel.write(emptyBuf)
          emptyBuf.rewind()
        }
        else {
          emptyBuf.limit(toWrite)
          channel.write(emptyBuf)
        }
        remaining -= toWrite
      }
      channel.position(0)
    }
  }

  operator fun get(key: Long): ByteBuffer? {
    if (key == 0L)
      return null
    val hashcode = getBucketIndex(key)
    buffer.position(hashcode * bucketSize)
    var inspectedBuckets = 0
    while (inspectedBuckets < bucketCount) {
      val inspectedKey = readKey()
      if (inspectedKey == key)
        return buffer
      if (inspectedKey == 0L)
        return null
      if (buffer.remaining() <= valueSize) {
        buffer.position(0)
      }
      else {
        buffer.position(buffer.position() + valueSize)
      }
      inspectedBuckets++
    }
    // Map is full
    return null
  }

  private fun readKey() = if (keySize == 8) buffer.long else buffer.int.toLong()

  private fun getBucketIndex(key: Long): Int {
    return (key.hashCode() and Int.MAX_VALUE).rem(bucketCount)
  }

  fun put(key: Long): ByteBuffer {
    buffer.position(getBucketIndex(key) * bucketSize)
    var inspectedBuckets = 0
    while (inspectedBuckets < bucketCount) {
      val inspectedKey = readKey()
      if (inspectedKey == key || inspectedKey == 0L) {
        if (keySize == 4) {
          buffer.putInt(buffer.position() - keySize, key.toInt())
        } else {
          buffer.putLong(buffer.position() - keySize, key)
        }
        if (inspectedKey == 0L) {
          filledBuckets++
        }
        return buffer
      }
      if (buffer.remaining() <= valueSize) {
        buffer.position(0)
      }
      else {
        buffer.position(buffer.position() + valueSize)
      }
      inspectedBuckets++
    }
    throw RuntimeException("HashMap is full.")
  }

  fun containsKey(key: Long): Boolean {
    if (key == 0L) return true
    return get(key) != null
  }

}