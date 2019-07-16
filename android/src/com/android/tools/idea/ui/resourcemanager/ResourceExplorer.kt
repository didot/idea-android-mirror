/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager

import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerToolbar
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerToolbarViewModel
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerView
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerViewModel
import com.android.tools.idea.ui.resourcemanager.importer.ImportersProvider
import com.android.tools.idea.ui.resourcemanager.importer.ResourceImportDragTarget
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.properties.Delegates

internal val MANAGER_SUPPORTED_RESOURCES get() =
  if (StudioFlags.RESOURCE_EXPLORER_PICKER.get()) {
    arrayOf(ResourceType.DRAWABLE,     ResourceType.COLOR,      ResourceType.LAYOUT,  ResourceType.MIPMAP,
            ResourceType.STRING,       ResourceType.NAVIGATION, ResourceType.ANIM,    ResourceType.ANIMATOR,
            ResourceType.INTERPOLATOR, ResourceType.TRANSITION, ResourceType.FONT,    ResourceType.MENU,
            ResourceType.STYLE,        ResourceType.ARRAY,      ResourceType.BOOL,    ResourceType.DIMEN,
            ResourceType.FRACTION,     ResourceType.INTEGER,    ResourceType.PLURALS, ResourceType.XML)
  } else {
    arrayOf(ResourceType.DRAWABLE, ResourceType.COLOR,
            ResourceType.LAYOUT,   ResourceType.MIPMAP)
  }

internal val RESOURCE_DEBUG = System.getProperty("res.manag.debug", "false")?.toBoolean() ?: false

/**
 * The resource explorer lets the user browse resources from the provided [AndroidFacet]
 */
class ResourceExplorer private constructor(
  facet: AndroidFacet,
  private val resourceExplorerViewModel: ResourceExplorerViewModel,
  private val resourceExplorerView: ResourceExplorerView,
  private val toolbarViewModel: ResourceExplorerToolbarViewModel,
  private val toolbar: ResourceExplorerToolbar,
  private val resourceImportDragTarget: ResourceImportDragTarget)
  : JPanel(BorderLayout()), Disposable {

  var facet by Delegates.observable(facet) { _, _, newValue -> updateFacet(newValue) }

  init {
    toolbarViewModel.facetUpdaterCallback = {newValue -> this.facet = newValue}
    resourceExplorerViewModel.facetUpdaterCallback = {newValue -> this.facet = newValue}
    resourceExplorerViewModel.resourceTypeUpdaterCallback = this::updateResourceType
  }

  companion object {
    private val DIALOG_PREFERRED_SIZE = JBUI.size(850, 620)

    /**
     * Create a new instance of [ResourceExplorer] optimized to be used in a [com.intellij.openapi.wm.ToolWindow]
     */
    @JvmStatic
    fun createForToolWindow(facet: AndroidFacet): ResourceExplorer {
      val importersProvider = ImportersProvider()
      val resourceExplorerViewModel = ResourceExplorerViewModel.createResManagerViewModel(facet)
      val toolbarViewModel = ResourceExplorerToolbarViewModel(
        facet,
        resourceExplorerViewModel.resourceTypes[resourceExplorerViewModel.resourceTypeIndex],
        importersProvider,
        resourceExplorerViewModel.filterOptions)
      val resourceImportDragTarget = ResourceImportDragTarget(facet, importersProvider)
      val toolbar = ResourceExplorerToolbar.create(toolbarViewModel, moduleComboEnabled = true)
      val resourceExplorerView = ResourceExplorerView(
        resourceExplorerViewModel, resourceImportDragTarget)
      return ResourceExplorer(
        facet,
        resourceExplorerViewModel,
        resourceExplorerView,
        toolbarViewModel,
        toolbar,
        resourceImportDragTarget)
    }

    /**
     * Create a new instance of [ResourceExplorer] to be used as resource picker.
     */
    fun createResourcePicker(
      facet: AndroidFacet,
      types: Set<ResourceType>,
      currentFile: VirtualFile?,
      updateResourceCallback: (resourceItem: ResourceItem) -> Unit,
      doSelectResourceCallback: (resourceItem: ResourceItem) -> Unit): ResourceExplorer {
      val importersProvider = ImportersProvider()
      val resourceExplorerViewModel = ResourceExplorerViewModel.createResPickerViewModel(facet,
                                                                                         types.toTypedArray(),
                                                                                         currentFile,
                                                                                         doSelectResourceCallback)
      val toolbarViewModel = ResourceExplorerToolbarViewModel(
        facet,
        resourceExplorerViewModel.resourceTypes[resourceExplorerViewModel.resourceTypeIndex],
        importersProvider,
        resourceExplorerViewModel.filterOptions)
      val resourceImportDragTarget = ResourceImportDragTarget(facet, importersProvider)
      val toolbar = ResourceExplorerToolbar.create(toolbarViewModel, moduleComboEnabled = false)
      val resourceExplorerView = ResourceExplorerView(
        resourceExplorerViewModel, resourceImportDragTarget, withMultiModuleSearch = false, withSummaryView = true, withDetailView = false,
        multiSelection = false)
      resourceExplorerView.addSelectionListener(object: ResourceExplorerView.SelectionListener{
        override fun onDesignAssetSetSelected(resourceAssetSet: ResourceAssetSet?) {
          resourceAssetSet?.assets?.firstOrNull()?.let {
            updateResourceCallback(it.resourceItem)
          }
        }
      })

      val explorer = ResourceExplorer(
        facet,
        resourceExplorerViewModel,
        resourceExplorerView,
        toolbarViewModel,
        toolbar,
        resourceImportDragTarget)
      explorer.preferredSize = DIALOG_PREFERRED_SIZE
      return explorer
    }
  }

  init {
    val centerContainer = JPanel(BorderLayout())

    centerContainer.add(toolbar, BorderLayout.NORTH)
    centerContainer.add(resourceExplorerView)
    add(centerContainer, BorderLayout.CENTER)
    if (resourceExplorerViewModel is Disposable) {
      // TODO: Consider making interface disposable, or passing a disposable parent to constructor.
      Disposer.register(this, resourceExplorerViewModel)
    }
    Disposer.register(this, resourceExplorerView)
  }

  private fun updateFacet(facet: AndroidFacet) {
    resourceExplorerViewModel.facet = facet
    resourceImportDragTarget.facet = facet
    toolbarViewModel.facet = facet
  }

  private fun updateResourceType(resourceType: ResourceType) {
    toolbarViewModel.resourceType = resourceType
  }

  override fun dispose() {
  }

  fun selectAsset(facet: AndroidFacet, path: VirtualFile) {
    updateFacet(facet)
    resourceExplorerView.selectAsset(path)
  }
}