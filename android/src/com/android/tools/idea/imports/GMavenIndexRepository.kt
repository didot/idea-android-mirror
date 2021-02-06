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
package com.android.tools.idea.imports

import com.android.annotations.concurrency.Slow
import com.android.io.CancellableFileIo
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.exists
import com.intellij.util.io.outputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Network connection timeout in milliseconds. */
private const val NETWORK_TIMEOUT_MILLIS = 5000
private const val GZ_EXT = ".gz"

/** Key used in property list to find ETag value.  */
private const val ETAG_KEY = "etag"

/**
 * A repository provides Maven class registry generated from loading local disk cache, which is actively refreshed from
 * network request on GMaven indices on [baseUrl]/[RELATIVE_PATH], on a scheduled basis (daily).
 *
 * [getMavenClassRegistry] returns the last known [MavenClassRegistryFromRepository] if possible.
 *
 * The underlying [lastComputedMavenClassRegistry] is for storing the last known value for instant query. The
 * freshness is guaranteed by the [scheduler].
 */
class GMavenIndexRepository(
  private val baseUrl: String,
  private val cacheDir: Path,
  private val refreshInterval: Duration
) : Disposable {
  private class ValueWithETag(val data: ByteArray, val eTag: String)

  private val relativeCachePath = if (RELATIVE_PATH.endsWith(GZ_EXT)) RELATIVE_PATH.dropLast(GZ_EXT.length) else RELATIVE_PATH
  private var lastComputedMavenClassRegistry = AtomicReference<MavenClassRegistryFromRepository?>()
  private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
    "MavenClassRegistry Refresher",
    1
  )

  init {
    val task = Runnable {
      thisLogger().info("Scheduled to refresh ${this.javaClass.name}.")
      refresh("$baseUrl/$RELATIVE_PATH")
    }
    // Schedules to refresh local disk cache on a daily basis.
    scheduler.scheduleWithFixedDelay(task, 0, refreshInterval.toMillis(), TimeUnit.MILLISECONDS)
  }

  /**
   * Returns the last known [MavenClassRegistryFromRepository] if possible.
   *
   * Or new Maven class registry is created in the calling thread.
   */
  fun getMavenClassRegistry(): MavenClassRegistryFromRepository {
    return lastComputedMavenClassRegistry.get() ?: MavenClassRegistryFromRepository(this).apply {
      lastComputedMavenClassRegistry.set(this)
    }
  }

  /**
   * Refreshes both local disk cache and [lastComputedMavenClassRegistry] if exists.
   */
  @Slow
  private fun refresh(url: String) {
    val status = refreshDiskCache(url)

    if (status == RefreshedStatus.UPDATED) {
      lastComputedMavenClassRegistry.getAndUpdate {
        if (it == null) {
          null
        }
        else {
          val mavenClassRegistry = MavenClassRegistryFromRepository(this)
          // TODO: make it `debug` instead of `info` once it's stable.
          thisLogger().info("Updated in-memory Maven class registry.")
          mavenClassRegistry
        }
      }
    }
  }

  /**
   * Loads the index from the local disk cache if possible. Or it falls back to the built-in index.
   */
  fun loadIndexFromDisk(): InputStream {
    val file = cacheDir.resolve(relativeCachePath)
    try {
      return CancellableFileIo.newInputStream(file)
    }
    catch (ignore: NoSuchFileException) {
    }

    // Fallback: Builtin index, used for offline scenarios etc.
    return readDefaultData()
  }

  /**
   * Returns [RefreshedStatus.UPDATED] if the disk cache is successfully updated.
   *
   * Or returns [RefreshedStatus.UNCHANGED] if it's already up to date. Or returns [RefreshedStatus.ERROR] when errors
   * occur.
   *
   * When requesting content, we explicitly store the corresponding ETag values, in a `.properties` file, as a sibling
   * to the local cached content. So, such cached ETag value can be an identifier to determine if there's new changes
   * since the last request, and `304 Not Modified Response` is the expected response if we've already gotten an up to
   * date cache.
   */
  @Slow
  private fun refreshDiskCache(url: String): RefreshedStatus {
    try {
      val cacheFile = cacheDir.resolve(relativeCachePath)
      val eTagForCacheFile: String? = if (cacheFile.exists()) loadETag(getETagFile(cacheFile)) else null

      val valueWithETag = readUrlData(url, NETWORK_TIMEOUT_MILLIS, eTagForCacheFile)
      if (valueWithETag == null) {
        thisLogger().info("Kept the old disk cache with an old ETag header: $eTagForCacheFile.")
        return RefreshedStatus.UNCHANGED
      }

      saveCache(valueWithETag.data, cacheFile)
      saveETag(getETagFile(cacheFile), valueWithETag.eTag)
      thisLogger().info("Refreshed disk cache successfully with a new ETag header: ${valueWithETag.eTag}.")
      return RefreshedStatus.UPDATED
    }
    catch (e: Exception) {
      thisLogger().info("Failed to refresh local disk cache:\n$e")
      return RefreshedStatus.ERROR
    }
  }

  /**
   * Reads the given query URL, with the given time out, and returns the bytes found.
   */
  @Slow
  private fun readUrlData(url: String, timeoutMillis: Int, eTag: String?): ValueWithETag? {
    return HttpRequests
      .request(URL(url).toExternalForm())
      .connectTimeout(timeoutMillis)
      .readTimeout(timeoutMillis)
      .tuner { connection ->
        eTag?.let { connection.setRequestProperty("If-None-Match", it) }
      }
      .connect { request ->
        val eTagField = request.connection.getHeaderField("ETag")
        val responseCode = (request.connection as HttpURLConnection).responseCode
        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
          thisLogger().info("HTTP not modified since the last request for URL: $url (etag: $eTagField).")
          return@connect null
        }

        val bytes = request.readBytes(null)
        return@connect ValueWithETag(bytes, eTagField)
      }
  }

  private fun readDefaultData(): InputStream {
    return GMavenIndexRepository::class.java.classLoader.getResourceAsStream("gmavenIndex/$OFFLINE_NAME.json") ?: throw Error(
      "Unexpected error when reading resource file: $OFFLINE_NAME.json."
    )
  }

  private fun saveCache(data: ByteArray, cacheFile: Path) {
    Files.createDirectories(cacheFile.parent!!)
    val tempFile = Files.createTempFile(cacheFile.parent, "${cacheFile.fileName}", ".tmp")

    try {
      ungzip(data).let {
        // Writes the decompressed bytes of the data to the temp file.
        Files.write(tempFile, it)
      }
      Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
    catch (e: Exception) {
      Files.deleteIfExists(tempFile)
      throw e
    }
  }

  private fun loadETag(file: Path): String? {
    return try {
      val properties = Properties().apply {
        CancellableFileIo.newInputStream(file).use { inputStream ->
          this.load(inputStream)
        }
      }
      properties.getProperty(ETAG_KEY)
    }
    catch (e: Exception) {
      thisLogger().info("Error when loading ETag value:\n$e")
      null
    }
  }

  private fun saveETag(file: Path, eTag: String) {
    Properties().apply {
      setProperty(ETAG_KEY, eTag)
      store(file.outputStream(), "## Metadata for gmaven index cache. Do not modify.")
    }
  }

  private fun getETagFile(file: Path): Path {
    return file.resolveSibling("${file.fileName}.properties")
  }

  /**
   * Status after the local disk cache being refreshed.
   */
  private enum class RefreshedStatus {
    /**
     * Content is updated to the latest.
     */
    UPDATED,

    /**
     * No changes after refreshing.
     */
    UNCHANGED,

    /**
     * Errors happen when refreshing.
     */
    ERROR
  }

  override fun dispose() {
    scheduler.shutdown()
  }
}