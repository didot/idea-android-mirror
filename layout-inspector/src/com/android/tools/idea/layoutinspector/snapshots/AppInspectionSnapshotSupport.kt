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
package com.android.tools.idea.layoutinspector.snapshots

import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionPropertiesProvider
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionTreeLoader
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeParametersCache
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.DisconnectedViewPropertiesCache
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewLayoutInspectorClient
import com.android.tools.idea.layoutinspector.skia.SkiaParserImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.write
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetAllParametersResponse
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetComposablesResponse
import layoutinspector.snapshots.Metadata
import layoutinspector.snapshots.Snapshot
import layoutinspector.view.inspection.LayoutInspectorViewProtocol
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.file.Path

val APP_INSPECTION_SNAPSHOT_VERSION = ProtocolVersion.Version4

/**
 * [SnapshotLoader] that can load snapshots saved by the app inspection-based version of the layout inspector.
 */
class AppInspectionSnapshotLoader : SnapshotLoader {
  override lateinit var propertiesProvider: AppInspectionPropertiesProvider
    private set
  override lateinit var metadata: SnapshotMetadata
    private set

  override val capabilities = mutableSetOf(InspectorClient.Capability.SUPPORTS_SYSTEM_NODES)

  override fun loadFile(file: VirtualFile, model: InspectorModel): SnapshotMetadata {
    val viewPropertiesCache = DisconnectedViewPropertiesCache(model)
    val composeParametersCache = ComposeParametersCache(null, model)
    propertiesProvider = AppInspectionPropertiesProvider(viewPropertiesCache, composeParametersCache, model)
    // TODO: error handling
    ObjectInputStream(file.inputStream).use { input ->
      val options = LayoutInspectorCaptureOptions().apply { parse(input.readUTF()) }
      if (options.version != APP_INSPECTION_SNAPSHOT_VERSION) {
        val message = "AppInspectionSnapshotSupport only supports version ${APP_INSPECTION_SNAPSHOT_VERSION.value}, got ${options.version}."
        Logger.getInstance(AppInspectionSnapshotLoader::class.java).error(message)
        throw Exception(message)
      }

      metadata = Metadata.parseDelimitedFrom(input).convert(APP_INSPECTION_SNAPSHOT_VERSION)
      val snapshot = Snapshot.parseDelimitedFrom(input)
      val response = snapshot.viewSnapshot
      val allWindows = response.windowSnapshotsList.associateBy { it.layout.rootView.id }
      val rootIds = response.windowRoots.idsList
      val allComposeInfo = snapshot.composeInfoList.associateBy { it.viewId }
      val metrics = LayoutInspectorMetrics(model.project, processDescriptor, snapshotMetadata = metadata)
      rootIds.map { allWindows[it] }.forEach { windowInfo ->
        // should always be true
        if (windowInfo != null) {
          val composeInfo = allComposeInfo[windowInfo.layout.rootView.id]
          val data = ViewLayoutInspectorClient.Data(0, rootIds, windowInfo.layout,
                                                    composeInfo?.composables)

          val treeLoader = AppInspectionTreeLoader(model.project, metrics::logEvent, SkiaParserImpl({}))
          val treeData = treeLoader.loadComponentTree(data, model.resourceLookup, processDescriptor) ?: throw Exception()
          capabilities.addAll(treeData.dynamicCapabilities)
          model.update(treeData.window, rootIds, treeData.generation)
          viewPropertiesCache.setAllFrom(windowInfo.properties)
          composeInfo?.composeParameters?.let { composeParametersCache.setAllFrom(it) }
        }
      }
    }
    return metadata
  }
}

fun saveAppInspectorSnapshot(
  path: Path,
  data: Map<Long, ViewLayoutInspectorClient.Data>,
  properties: Map<Long, LayoutInspectorViewProtocol.PropertiesEvent>,
  composeProperties: Map<Long, GetAllParametersResponse>,
  snapshotMetadata: SnapshotMetadata
) {
  val response = LayoutInspectorViewProtocol.CaptureSnapshotResponse.newBuilder().apply {
    val allRootIds = data.values.firstOrNull()?.rootIds
    addAllWindowSnapshots(allRootIds?.map { rootId ->
      LayoutInspectorViewProtocol.CaptureSnapshotResponse.WindowSnapshot.newBuilder().apply {
        layout = data[rootId]?.viewEvent
        this.properties = properties[rootId]
      }.build()
    })
    windowRoots = LayoutInspectorViewProtocol.WindowRootsEvent.newBuilder().apply {
      addAllIds(allRootIds)
    }.build()
  }.build()
  val composeInfo = composeProperties.mapValues { (id, composePropertyEvent) ->
    data[id]?.composeEvent to composePropertyEvent
  }
  saveAppInspectorSnapshot(path, response, composeInfo, snapshotMetadata)
}

fun saveAppInspectorSnapshot(
  path: Path,
  data: LayoutInspectorViewProtocol.CaptureSnapshotResponse,
  composeInfo: Map<Long, Pair<GetComposablesResponse?, GetAllParametersResponse>>,
  snapshotMetadata: SnapshotMetadata
) {
  snapshotMetadata.containsCompose = composeInfo.isNotEmpty()
  val snapshot = Snapshot.newBuilder().apply {
    viewSnapshot = data
    addAllComposeInfo(composeInfo.map { (viewId, composableAndParameters) ->
      val (composables, composeParameters) = composableAndParameters
      Snapshot.ComposeInfo.newBuilder().apply {
        this.viewId = viewId
        this.composables = composables
        this.composeParameters = composeParameters
      }.build()
    })
  }.build()
  val output = ByteArrayOutputStream()
  ObjectOutputStream(output).use { objectOutput ->
    objectOutput.writeUTF(LayoutInspectorCaptureOptions(APP_INSPECTION_SNAPSHOT_VERSION, "TODO").toString())
    snapshotMetadata.toProto().writeDelimitedTo(objectOutput)
    snapshot.writeDelimitedTo(objectOutput)
  }
  path.write(output.toByteArray())
}

