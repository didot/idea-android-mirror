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
package com.android.tools.idea.npw.project

import com.android.tools.adtui.ASGallery
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.adtui.util.FormScalingUtil
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.FormFactor.Companion.get
import com.android.tools.idea.npw.cpp.ConfigureCppSupportStep
import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.npw.model.NewProjectModuleModel
import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.ui.ActivityGallery.getTemplateDescription
import com.android.tools.idea.npw.ui.ActivityGallery.getTemplateIcon
import com.android.tools.idea.npw.ui.ActivityGallery.getTemplateImageLabel
import com.android.tools.idea.npw.ui.WizardGallery
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.templates.Template.CATEGORY_APPLICATION
import com.android.tools.idea.templates.TemplateManager
import com.android.tools.idea.wizard.model.ModelWizard.Facade
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.google.common.base.Function
import com.google.common.base.Suppliers
import com.google.common.collect.Lists
import com.google.common.collect.Lists.newArrayList
import com.google.common.collect.Maps
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.ui.GuiUtils
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER
import com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH
import com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW
import com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK
import com.intellij.uiDesigner.core.GridLayoutManager
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.io.File
import java.util.Arrays
import java.util.Comparator
import java.util.Objects
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Collectors.toList
import java.util.stream.Collectors.toMap
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

/**
 * First page in the New Project wizard that allows user to select the Form Factor (Mobile, Wear, TV, etc) and its
 * Template ("Empty Activity", "Basic", "Nav Drawer", etc)
 */
class ChooseAndroidProjectStep(model: NewProjectModel) : ModelWizardStep<NewProjectModel>(
  model, message("android.wizard.project.new.choose")) {
  private var myLoadingPanel = JBLoadingPanel(BorderLayout(), this)
  private val myTabsPanel = CommonTabbedPane()
  private val myRootPanel = JPanel(GridLayoutManager(1, 1))
  private val myFormFactors: Supplier<List<FormFactorInfo>>? = Suppliers.memoize(
    com.google.common.base.Supplier { createFormFactors(title) })
  private val canGoForward = BoolValueProperty()
  private var myNewProjectModuleModel: NewProjectModuleModel? = null

  init {
    val anySize = Dimension(-1, -1)
    myLoadingPanel.add(myTabsPanel)
    val canGrowAndShrink = SIZEPOLICY_CAN_GROW or SIZEPOLICY_CAN_SHRINK
    myRootPanel.add(myLoadingPanel,
                    GridConstraints(0, 0, 1, 1, ANCHOR_CENTER, FILL_BOTH, canGrowAndShrink, canGrowAndShrink, anySize, anySize, anySize, 0,
                                    false))
  }

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> {
    myNewProjectModuleModel = NewProjectModuleModel(model)
    val renderModel = myNewProjectModuleModel!!.extraRenderTemplateModel
    return newArrayList(
      ConfigureAndroidProjectStep(myNewProjectModuleModel!!, model),
      ConfigureCppSupportStep(model),
      ConfigureTemplateParametersStep(renderModel, message("android.wizard.config.activity.title"), newArrayList()))
  }

  private fun createUIComponents() {
    myLoadingPanel = object : JBLoadingPanel(
      BorderLayout(), this,
      ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS) {
      override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        super.setBounds(x, y, width, height)

        // Work-around for IDEA-205343 issue.
        for (component in components) {
          component!!.setBounds(x, y, width, height)
        }
      }
    }
    myLoadingPanel.setLoadingText("Loading Android project template files")
  }

  override fun onWizardStarting(wizard: Facade) {
    myLoadingPanel.startLoading()
    BackgroundTaskUtil.executeOnPooledThread(this, Runnable {
      // Constructing FormFactors performs disk access and XML parsing, so let's do it in background
      // thread.
      val formFactors = myFormFactors!!.get()

      // Update UI with the loaded formFactors. Switch back to UI thread.


      GuiUtils.invokeLaterIfNeeded(
        {
          updateUi(wizard, formFactors)
        },
        ModalityState.any())
    })
  }

  /**
   * Updates UI with a given form factors. This method must be executed on event dispatch thread.
   */
  private fun updateUi(wizard: Facade,
                       formFactors: List<FormFactorInfo>) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    for (formFactorInfo in formFactors) {
      val tabPanel = formFactorInfo.tabPanel
      myTabsPanel.addTab(formFactorInfo.formFactor.toString(), tabPanel.myRootPanel)
      tabPanel.myGallery.setDefaultAction(object : AbstractAction() {
        override fun actionPerformed(actionEvent: ActionEvent?) {
          wizard.goForward()
        }
      })
      val activitySelectedListener = ListSelectionListener { selectionEvent: ListSelectionEvent? ->
        val selectedTemplate = tabPanel.myGallery.selectedElement
        if (selectedTemplate != null) {
          tabPanel.myTemplateName.text = selectedTemplate.imageLabel
          tabPanel.myTemplateDesc.text = "<html>" + selectedTemplate.templateDescription + "</html>"
          tabPanel.myDocumentationLink.isVisible = selectedTemplate.isCppTemplate
        }
        canGoForward.set(selectedTemplate != null)
      }
      tabPanel.myGallery.addListSelectionListener(activitySelectedListener)
      activitySelectedListener.valueChanged(null)
    }
    FormScalingUtil.scaleComponentTree(this.javaClass, myRootPanel)
    myLoadingPanel.stopLoading()
  }

  override fun onProceeding() {
    val formFactorInfo = myFormFactors!!.get()[myTabsPanel.selectedIndex]
    val selectedTemplate = formFactorInfo.tabPanel.myGallery.selectedElement!!
    model.enableCppSupport.set(selectedTemplate.isCppTemplate)
    myNewProjectModuleModel!!.formFactor.set(formFactorInfo.formFactor)
    myNewProjectModuleModel!!.moduleTemplateFile().setNullableValue(formFactorInfo.templateFile)
    myNewProjectModuleModel!!.renderTemplateHandle.setNullableValue(selectedTemplate.template)
    val extraStepTemplateHandle = if (formFactorInfo.formFactor === FormFactor.THINGS) selectedTemplate.template else null
    myNewProjectModuleModel!!.extraRenderTemplateModel.templateHandle = extraStepTemplateHandle
  }

  override fun canGoForward(): ObservableBool {
    return canGoForward
  }

  override fun getComponent(): JComponent {
    return myRootPanel
  }

  override fun getPreferredFocusComponent(): JComponent {
    return myTabsPanel
  }

  private class FormFactorInfo internal constructor(
    internal var templateFile: File,
    internal val formFactor: FormFactor,
    internal var minSdk: Int,
    internal val tabPanel: ChooseAndroidProjectPanel<TemplateRenderer>)

  private class TemplateRenderer internal constructor(internal val template: TemplateHandle?, internal val isCppTemplate: Boolean) {

    internal val imageLabel: String
      get() = getTemplateImageLabel(template, isCppTemplate)

    internal val templateDescription: String
      get() = getTemplateDescription(template, isCppTemplate)

    override fun toString(): String {
      return imageLabel
    }

    /**
     * Return the image associated with the current template, if it specifies one, or null otherwise.
     */
    internal val icon: Icon?
      get() = getTemplateIcon(template, isCppTemplate)

  }

  companion object {
    // To have the sequence specified by design, we hardcode the sequence.
    private val ORDERED_ACTIVITY_NAMES = arrayOf(
      "Basic Activity", "Empty Activity", "Bottom Navigation Activity", "Fragment + ViewModel", "Fullscreen Activity", "Master/Detail Flow",
      "Navigation Drawer Activity", "Google Maps Activity", "Login Activity", "Scrolling Activity", "Tabbed Activity"
    )

    private fun createFormFactors(wizardTitle: String): List<FormFactorInfo> {
      val formFactorInfoMap: MutableMap<FormFactor, FormFactorInfo> = Maps.newTreeMap()
      val manager = TemplateManager.getInstance()
      val applicationTemplates: List<File> = manager!!.getTemplatesInCategory(CATEGORY_APPLICATION)
      for (templateFile in applicationTemplates) {
        val metadata = manager.getTemplateMetadata(templateFile)
        if (metadata == null || metadata.formFactor == null) {
          continue
        }
        val formFactor = get(metadata.formFactor!!)
        val prevFormFactorInfo = formFactorInfoMap[formFactor]
        val templateMinSdk = metadata.minSdk
        if (prevFormFactorInfo == null) {
          val minSdk = Math.max(templateMinSdk, formFactor.minOfflineApiLevel)
          val tabPanel = ChooseAndroidProjectPanel(createGallery(wizardTitle, formFactor))
          formFactorInfoMap[formFactor] = FormFactorInfo(templateFile, formFactor, minSdk, tabPanel)
        }
        else if (templateMinSdk > prevFormFactorInfo.minSdk) {
          prevFormFactorInfo.minSdk = templateMinSdk
          prevFormFactorInfo.templateFile = templateFile
        }
      }
      return formFactorInfoMap.values.stream().sorted(Comparator.comparing { f: FormFactorInfo -> f.formFactor }).collect(toList())
    }

    private fun getFilteredTemplateHandles(formFactor: FormFactor): List<TemplateHandle?> {
      val templateHandles: List<TemplateHandle?> = TemplateManager.getInstance().getTemplateList(formFactor)
      if (formFactor === FormFactor.MOBILE) {
        val entryMap: Map<String, TemplateHandle?>? = templateHandles.stream().collect(toMap({ it!!.metadata.title!! }, { it }))
        return Arrays.stream(ORDERED_ACTIVITY_NAMES).map { entryMap!![it] }.filter { Objects.nonNull(it) }.collect(toList())
      }
      return templateHandles
    }

    private fun createGallery(title: String,
                              formFactor: FormFactor): ASGallery<TemplateRenderer> {
      val templateHandles = getFilteredTemplateHandles(formFactor)
      val templateRenderers: MutableList<TemplateRenderer>? = Lists.newArrayListWithExpectedSize(
        templateHandles.size + 2)
      templateRenderers!!.add(TemplateRenderer(null, false)) // "No Activity" entry

      for (templateHandle in templateHandles) {
        templateRenderers.add(TemplateRenderer(templateHandle, false))
      }
      if (formFactor === FormFactor.MOBILE) {
        templateRenderers.add(TemplateRenderer(null, true)) // "Native C++" entry
      }
      val listItems = templateRenderers.toTypedArray()
      val gallery: ASGallery<TemplateRenderer> = WizardGallery(title, { it!!.icon }, { it!!.imageLabel })
      gallery.model = JBList.createDefaultListModel<Any?>(*listItems as Array<Any>)
      gallery.selectedIndex = getDefaultSelectedTemplateIndex(listItems)
      return gallery
    }

    private fun getDefaultSelectedTemplateIndex(templateRenderers: Array<TemplateRenderer>): Int {
      for (i in templateRenderers.indices) {
        if (templateRenderers[i].imageLabel == "Empty Activity") {
          return i
        }
      }

      // Default template not found. Instead, return the index to the first valid template renderer (e.g. skip "No Activity", etc.)
      for (i in templateRenderers.indices) {
        if (templateRenderers[i].template != null) {
          return i
        }
      }
      assert(false) { "No valid Template found" }
      return 0
    }
  }
}