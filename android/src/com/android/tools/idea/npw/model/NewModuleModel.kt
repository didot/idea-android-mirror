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
package com.android.tools.idea.npw.model

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.getInitialSourceLanguage
import com.android.tools.idea.npw.module.getModuleRoot
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.AbstractProperty
import com.android.tools.idea.observable.BatchInvoker.INVOKE_IMMEDIATELY_STRATEGY
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.templates.Template
import com.android.tools.idea.templates.TemplateMetadata.ATTR_APP_TITLE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_INCLUDE_FORM_FACTOR
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_MODULE_NAME
import com.android.tools.idea.templates.TemplateUtils
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File
import java.util.ArrayList

private val log: Logger get() = logger<NewModuleModel>()

class NewModuleModel : WizardModel {
  val isLibrary: BoolProperty = BoolValueProperty()
  val renderTemplateModel: OptionalProperty<RenderTemplateModel> = OptionalValueProperty()
  val projectTemplateValues: MutableMap<String, Any>
  val templateValues = mutableMapOf<String, Any>()
  val project: OptionalProperty<Project>
  val moduleParent: String?
  val projectSyncInvoker: ProjectSyncInvoker
  val multiTemplateRenderer: MultiTemplateRenderer

  // Note: INVOKE_IMMEDIATELY otherwise Objects may be constructed in the wrong state
  private val bindings = BindingsManager(INVOKE_IMMEDIATELY_STRATEGY)
  val moduleName = StringValueProperty()
  val splitName = StringValueProperty("feature")
  // A template that's associated with a user's request to create a new module. This may be null if the user skips creating a
  // module, or instead modifies an existing module (for example just adding a new Activity)
  val templateFile = OptionalValueProperty<File>()
  val applicationName: StringProperty
  val projectLocation: StringProperty
  val packageName = StringValueProperty()
  private val projectPackageName: StringProperty
  val enableCppSupport: BoolProperty
  val language: OptionalValueProperty<Language>
  private val createInExistingProject: Boolean
  val template: ObjectProperty<NamedModuleTemplate>
  val androidSdkInfo: OptionalValueProperty<AndroidVersionsInfo.VersionItem> = OptionalValueProperty()
  val formFactor: ObjectValueProperty<FormFactor>

  init {
    moduleName.addConstraint(AbstractProperty.Constraint(String::trim))
    splitName.addConstraint(AbstractProperty.Constraint(String::trim))
  }

  constructor(project: Project,
              moduleParent: String?,
              projectSyncInvoker: ProjectSyncInvoker,
              template: NamedModuleTemplate) {
    this.project = OptionalValueProperty(project)
    this.moduleParent = moduleParent
    this.projectSyncInvoker = projectSyncInvoker
    this.template = ObjectValueProperty(template)
    projectPackageName = packageName
    createInExistingProject = true
    enableCppSupport = BoolValueProperty()
    language = OptionalValueProperty(getInitialSourceLanguage(project))
    applicationName = StringValueProperty(message("android.wizard.module.config.new.application"))
    applicationName.addConstraint(AbstractProperty.Constraint(String::trim))
    projectLocation = StringValueProperty(project.basePath!!)
    isLibrary.addListener { updateApplicationName() }
    multiTemplateRenderer = MultiTemplateRenderer(project, projectSyncInvoker)
    projectTemplateValues = mutableMapOf()
    formFactor = ObjectValueProperty(FormFactor.MOBILE)
  }

  constructor(
    projectModel: NewProjectModel, templateFile: File, template: NamedModuleTemplate,
    formFactor: ObjectValueProperty<FormFactor> = ObjectValueProperty(FormFactor.MOBILE)
  ) {
    this.template = ObjectValueProperty(template)
    project = projectModel.project
    this.moduleParent = null
    projectPackageName = projectModel.packageName
    projectSyncInvoker = projectModel.projectSyncInvoker
    createInExistingProject = false
    enableCppSupport = projectModel.enableCppSupport
    applicationName = projectModel.applicationName
    projectLocation = projectModel.projectLocation
    this.templateFile.value = templateFile
    multiTemplateRenderer = projectModel.multiTemplateRenderer
    multiTemplateRenderer.incrementRenders()
    language = OptionalValueProperty()
    projectTemplateValues = projectModel.templateValues
    this.formFactor = formFactor

    bindings.bind(packageName, projectPackageName)
  }

  override fun dispose() {
    super.dispose()
    bindings.releaseAll()
  }

  /**
   * This method should be called if there is no "Activity Render Template" step (For example when creating a Library, or the activity
   * creation is skipped by the user)
   */
  fun setRenderTemplateModel(renderModel: RenderTemplateModel) {
    this.renderTemplateModel.value = renderModel
  }

  public override fun handleFinished() {
    multiTemplateRenderer.requestRender(ModuleTemplateRenderer())
  }

  override fun handleSkipped() {
    multiTemplateRenderer.skipRender()
  }

  private inner class ModuleTemplateRenderer : MultiTemplateRenderer.TemplateRenderer {
    @WorkerThread
    override fun init() {
      // By the time we run handleFinished(), we must have a Project
      if (!project.get().isPresent) {
        log.error("NewModuleModel did not collect expected information and will not complete. Please report this error.")
      }

      // TODO(qumeric): let project know about formFactors (it is being rendered before NewModuleModel.init runs)
      projectTemplateValues.also {
        it[formFactor.get().id + ATTR_INCLUDE_FORM_FACTOR] = true
        it[formFactor.get().id + ATTR_MODULE_NAME] = moduleName.get()
        templateValues.putAll(it)
      }

      templateValues[ATTR_APP_TITLE] = applicationName.get()
      templateValues[ATTR_IS_LIBRARY_MODULE] = isLibrary.get()

      val project = project.value
      TemplateValueInjector(templateValues).apply {
        setProjectDefaults(project)
        setModuleRoots(template.get().paths, project.basePath!!, moduleName.get(), packageName.get())
        if (androidSdkInfo.isPresent.get()) {
          setBuildVersion(androidSdkInfo.value, project)
        }
        if (language.get().isPresent) { // For new Projects, we have a different UI, so no Language should be present
          setLanguage(language.value)
        }
      }

      renderTemplateModel.valueOrNull?.templateValues?.putAll(templateValues)
    }

    @WorkerThread
    override fun doDryRun(): Boolean {
      // This is done because module needs to know about all included form factors, and currently we know about them only after init run,
      // so we need to set it after all inits (thus in dryRun) TODO(qumeric): remove after adding formFactors to the project
      templateValues.putAll(projectTemplateValues)
      if (templateFile.valueOrNull == null) {
        return false // If here, the user opted to skip creating any module at all, or is just adding a new Activity
      }

      // Returns false if there was a render conflict and the user chose to cancel creating the template
      return renderModule(true, project.value)
    }

    @WorkerThread
    override fun render() {
      val project = project.value

      val success = WriteCommandAction.writeCommandAction(project).withName("New Module").compute<Boolean, Exception> {
        renderModule(false, project)
      }

      if (!success) {
        log.warn("A problem occurred while creating a new Module. Please check the log file for possible errors.")
      }
    }

    private fun renderModule(dryRun: Boolean, project: Project): Boolean {
      val projectRoot = File(project.basePath!!)
      val moduleRoot = getModuleRoot(project.basePath!!, moduleName.get())
      val template = Template.createFromPath(templateFile.value)
      val filesToOpen = ArrayList<File>()

      val context = RenderingContext.Builder.newContext(template, project)
        .withCommandName("New Module")
        .withDryRun(dryRun)
        .withShowErrors(true)
        .withOutputRoot(projectRoot)
        .withModuleRoot(moduleRoot)
        .intoOpenFiles(filesToOpen)
        .withParams(templateValues)
        .build()

      return template.render(context, dryRun).also {
        if (it && !dryRun) {
          // calling smartInvokeLater will make sure that files are open only when the project is ready
          DumbService.getInstance(project).smartInvokeLater { TemplateUtils.openEditors(project, filesToOpen, false) }
        }
      }
    }
  }

  private fun updateApplicationName() {
    val msgId: String = when {
      isLibrary.get() -> "android.wizard.module.config.new.library"
      else -> "android.wizard.module.config.new.application"
    }
    applicationName.set(message(msgId))
  }
}
