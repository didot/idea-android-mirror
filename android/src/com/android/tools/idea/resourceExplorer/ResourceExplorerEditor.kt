/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer

import com.android.tools.idea.resourceExplorer.importer.ImportersProvider
import com.android.tools.idea.resourceExplorer.synchronisation.SynchronizationManager
import com.android.tools.idea.resourceExplorer.view.ExternalResourceBrowser
import com.android.tools.idea.resourceExplorer.view.InternalResourceBrowser
import com.android.tools.idea.resourceExplorer.view.QualifierParserPanel
import com.android.tools.idea.resourceExplorer.viewmodel.ExternalDesignAssetExplorer
import com.android.tools.idea.resourceExplorer.viewmodel.InternalDesignAssetExplorer
import com.android.tools.idea.resourceExplorer.viewmodel.QualifierLexerPresenter
import com.android.tools.idea.resourceExplorer.viewmodel.ResourceFileHelper
import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Editor to manage the resources of the project
 */
class ResourceExplorerEditor(facet: AndroidFacet) : UserDataHolderBase(), FileEditor {
  private val root = JPanel(BorderLayout())

  init {
    val synchronizationManager = SynchronizationManager(facet)
    val fileHelper = ResourceFileHelper.ResourceFileHelperImpl()
    val importersProvider = ImportersProvider()

    val externalResourceBrowserViewModel = ExternalDesignAssetExplorer(facet, fileHelper, importersProvider, synchronizationManager)
    val qualifierPanelPresenter = QualifierLexerPresenter(externalResourceBrowserViewModel::consumeMatcher)
    val qualifierParserPanel = QualifierParserPanel(qualifierPanelPresenter)
    val externalResourceBrowser = ExternalResourceBrowser(facet, externalResourceBrowserViewModel, qualifierParserPanel)

    val internalResourceBrowser = InternalResourceBrowser(InternalDesignAssetExplorer(facet, synchronizationManager))
    val designAssetDetailView = DesignAssetDetailView()
    internalResourceBrowser.addSelectionListener(designAssetDetailView)

    val centerContainer = Box.createVerticalBox()
    centerContainer.add(internalResourceBrowser)
    centerContainer.add(designAssetDetailView)
    root.add(externalResourceBrowser, BorderLayout.EAST)
    root.add(centerContainer, BorderLayout.CENTER)

    Disposer.register(this, synchronizationManager)
  }

  override fun getComponent(): JComponent = root

  override fun getPreferredFocusedComponent(): JComponent? = null

  override fun getName(): String = "Resource Explorer"

  override fun setState(state: FileEditorState) {
  }

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = true

  override fun selectNotify() {}

  override fun deselectNotify() {}

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null

  override fun getCurrentLocation(): FileEditorLocation? = null

  override fun dispose() {
  }
}
