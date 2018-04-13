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
package com.android.tools.idea.npw.dynamicapp;

import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.model.NewProjectModel;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.project.DomainToPackageExpression;
import com.android.tools.idea.npw.project.FormFactorSdkControls;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.npw.ui.ActivityGallery;
import com.android.tools.idea.npw.validator.ModuleValidator;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.*;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.tools.adtui.validation.Validator.Result.OK;
import static com.android.tools.adtui.validation.Validator.Severity.ERROR;
import static com.android.tools.idea.npw.model.NewProjectModel.toPackagePart;
import static java.lang.String.format;
import static org.jetbrains.android.util.AndroidBundle.message;

/**
 * This class configures the Dynamic Module specific data such as the "Base Application Module", "Module Name", "Package Name" and
 * "Minimum API Level"
 */
public class ConfigureDynamicModuleStep extends SkippableWizardStep<DynamicModuleModel> {
  private final JBScrollPane myRootPanel;
  private final ValidatorPanel myValidatorPanel;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  private JPanel myPanel;
  private JTextField myModuleName;
  private JTextField myPackageName;
  private JComboBox<Module> myBaseApplication;
  private JLabel myTemplateIconTitle;
  private JLabel myTemplateIconDetail;
  private JPanel myFormFactorSdkControlsPanel;
  private FormFactorSdkControls myFormFactorSdkControls;

  public ConfigureDynamicModuleStep(@NotNull DynamicModuleModel model, @NotNull String basePackage) {
    super(model, message("android.wizard.module.config.title"));

    TextProperty packageNameText = new TextProperty(myPackageName);
    Expression<String> computedPackageName = new Expression<String>(model.moduleName()) {
      @NotNull
      @Override
      public String get() {
        return format("%s.%s", basePackage, toPackagePart(model.moduleName().get()));
      }
    };
    BoolProperty isPackageNameSynced = new BoolValueProperty(true);
    myBindings.bind(packageNameText, computedPackageName, isPackageNameSynced);
    myBindings.bind(model.packageName(), packageNameText);
    myListeners.receive(packageNameText, value -> isPackageNameSynced.set(value.equals(computedPackageName.get())));

    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myRootPanel = StudioWizardStepPanel.wrappedWithVScroll(myValidatorPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    return Collections.singletonList(new ConfigureDynamicDeliveryStep(getModel()));
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    StringProperty modelName = getModel().moduleName();
    Project project = getModel().getProject();

    myBindings.bindTwoWay(new TextProperty(myModuleName), modelName);

    StringProperty companyDomain = new StringValueProperty(NewProjectModel.getInitialDomain(false));
    String basePackage = new DomainToPackageExpression(companyDomain, new StringValueProperty("")).get();

    Expression<String> computedPackageName = modelName
      .transform(appName -> format("%s.%s", basePackage, toPackagePart(appName)));
    TextProperty packageNameText = new TextProperty(myPackageName);
    BoolProperty isPackageNameSynced = new BoolValueProperty(true);
    myBindings.bind(getModel().packageName(), packageNameText);
    myBindings.bind(packageNameText, computedPackageName, isPackageNameSynced);
    myListeners.receive(packageNameText, value -> isPackageNameSynced.set(value.equals(computedPackageName.get())));

    OptionalProperty<AndroidVersionsInfo.VersionItem> androidSdkInfo = getModel().androidSdkInfo();
    myFormFactorSdkControls.init(androidSdkInfo, this);

    AndroidProjectInfo.getInstance(project).getAllModulesOfProjectType(PROJECT_TYPE_APP)
      .stream()
      .filter(module -> AndroidModuleModel.get(module) != null)
      .forEach(module -> myBaseApplication.addItem(module));

    OptionalProperty<Module> baseApplication = getModel().baseApplication();
    myBindings.bind(baseApplication, new SelectedItemProperty<>(myBaseApplication));

    myValidatorPanel.registerValidator(modelName, value ->
      value.isEmpty() ? new Validator.Result(ERROR, message("android.wizard.validate.empty.module.name")) : OK);

    myValidatorPanel.registerValidator(getModel().packageName(),
                                       value -> Validator.Result.fromNullableMessage(WizardUtils.validatePackageName(value)));

    myValidatorPanel.registerValidator(androidSdkInfo, value ->
      value.isPresent() ? OK : new Validator.Result(ERROR, message("select.target.dialog.text")));

    myValidatorPanel.registerValidator(baseApplication, value ->
      value.isPresent() ? OK : new Validator.Result(ERROR, message("android.wizard.module.new.dynamic.select.base")));

    ModuleValidator moduleValidator = new ModuleValidator(project);
    modelName.set(WizardUtils.getUniqueName(modelName.get(), moduleValidator));
    myValidatorPanel.registerValidator(modelName, new ModuleValidator(project));
  }

  @Override
  protected void onEntering() {
    FormFactor formFactor = FormFactor.MOBILE;
    TemplateHandle templateHandle = getModel().getTemplateHandle();

    myFormFactorSdkControls.startDataLoading(formFactor, templateHandle.getMetadata().getMinSdk());
    setTemplateThumbnail(templateHandle);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myModuleName;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  private void setTemplateThumbnail(@Nullable TemplateHandle templateHandle) {
    Image image = ActivityGallery.getTemplateImage(templateHandle, false);
    if (image != null) {
      myTemplateIconTitle.setIcon(new ImageIcon(image.getScaledInstance(256, 256,  Image.SCALE_SMOOTH)));
    }
    myTemplateIconTitle.setText(ActivityGallery.getTemplateImageLabel(templateHandle, false));
    myTemplateIconDetail.setText("<html>" + ActivityGallery.getTemplateDescription(templateHandle, false) + "</html>");
  }

  private void createUIComponents() {
    myBaseApplication = new ComboBox<>(new DefaultComboBoxModel<>());
    myBaseApplication.setRenderer(new ListCellRendererWrapper<Module>() {
      @Override
      public void customize(JList list, Module module, int index, boolean selected, boolean hasFocus) {
        if (module == null) {
          setText(message("android.wizard.module.config.new.base.missing"));
        }
        else {
          setIcon(ModuleType.get(module).getIcon());
          setText(module.getName());
        }
      }
    });

    myFormFactorSdkControls = new FormFactorSdkControls();
    myFormFactorSdkControls.showStatsPanel(false);
    myFormFactorSdkControlsPanel = myFormFactorSdkControls.getRoot();
  }
}
