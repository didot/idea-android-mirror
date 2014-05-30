/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.android.SdkConstants;
import com.android.builder.model.SourceProvider;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.collect.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * Wizard step for specifying template-specific parameters.
 */
public class TemplateParameterStep2 extends DynamicWizardStepWithHeaderAndDescription {
  public static final Logger LOG = Logger.getInstance(TemplateParameterStep2.class);
  public static final int COLUMN_COUNT = 3;
  private final Function<Parameter, Key<?>> myParameterToKey;
  private final Map<String, Object> myPresetParameters;
  @Nullable private final VirtualFile myTargetDirectory;
  private JLabel myTemplateIcon;
  private JPanel myTemplateParameters;
  private JLabel myTemplateDescription;
  private JPanel myRootPanel;
  private JLabel myParameterDescription;
  private JSeparator myFooterSeparator;
  private Map<Parameter, List<JComponent>> components = Maps.newHashMap();
  private Map<Parameter, Object> myParameterDefaultValues = ImmutableMap.of();
  private TemplateEntry myCurrentTemplate;
  private JComboBox mySourceSet;
  private JLabel mySourceSetLabel;

  /**
   * Creates a new template parameters wizard step.
   *
   * @param presetParameters some parameter values may be predefined outside of this step.
   *                         User will not be allowed to change their values.
   */
  public TemplateParameterStep2(Map<String, Object> presetParameters, @Nullable VirtualFile targetDirectory,
                                @NotNull Disposable disposable) {
    super("Choose options for your new file", null, AndroidIcons.Wizards.FormFactorPhoneTablet, disposable);
    myPresetParameters = presetParameters;
    myTargetDirectory = targetDirectory;
    myParameterToKey = CacheBuilder.newBuilder().weakKeys().build(CacheLoader.from(new ParameterKeyFunction()));
    myRootPanel.setBackground(JBColor.white);
    myRootPanel.setBorder(createBodyBorder());
    myTemplateDescription.setBorder(BorderFactory.createEmptyBorder(0, 0, myTemplateDescription.getFont().getSize(), 0));
    setBodyComponent(myRootPanel);
  }

  private static JComponent createTextFieldWithBrowse(Parameter parameter) {
    String sourceUrl = parameter.sourceUrl;
    if (sourceUrl == null) {
      LOG.warn(String.format("Source URL is missing for parameter %1$s", parameter.name));
      sourceUrl = "";
    }
    return new TextFieldWithLaunchBrowserButton(sourceUrl);
  }

  private static JComponent createEnumCombo(Parameter parameter) {
    JComboBox combo = new ComboBox();
    List<Element> options = parameter.getOptions();
    assert !options.isEmpty();
    for (Element option : options) {
      //noinspection unchecked
      combo.addItem(createItemForOption(parameter, option));
      String isDefault = option.getAttribute(Template.ATTR_DEFAULT);
      if (isDefault != null && !isDefault.isEmpty() && Boolean.valueOf(isDefault)) {
        combo.setSelectedIndex(combo.getItemCount() - 1);
      }
    }
    return combo;
  }

  public static ComboBoxItem createItemForOption(Parameter parameter, Element option) {
    String optionId = option.getAttribute(SdkConstants.ATTR_ID);
    assert optionId != null && !optionId.isEmpty() : SdkConstants.ATTR_ID;
    NodeList childNodes = option.getChildNodes();
    assert childNodes.getLength() == 1 && childNodes.item(0).getNodeType() == Node.TEXT_NODE;
    String optionLabel = childNodes.item(0).getNodeValue().trim();
    int minSdk = getIntegerOptionValue(option, TemplateMetadata.ATTR_MIN_API, parameter.name, 1);
    int minBuildApi = getIntegerOptionValue(option, TemplateMetadata.ATTR_MIN_BUILD_API, parameter.name, 1);
    return new ComboBoxItem(optionId, optionLabel, minSdk, minBuildApi);
  }

  private static int getIntegerOptionValue(Element option, String attribute, @Nullable String parameterName, int defaultValue) {
    String stringValue = option.getAttribute(attribute);
    try {
      return StringUtil.isEmpty(stringValue) ? defaultValue : Integer.parseInt(stringValue);
    }
    catch (Exception e) {
      LOG.warn(String.format("Invalid %1$s value (%2$s) for option %3$s in parameter %4$s", attribute, stringValue,
                             option.getAttribute(SdkConstants.ATTR_ID), parameterName), e);
      return defaultValue;
    }
  }

  private static void addComponent(JComponent parent, JComponent component, int row, int column, boolean isLast) {
    GridConstraints gridConstraints = new GridConstraints();
    gridConstraints.setRow(row);
    gridConstraints.setColumn(column);

    boolean isGreedyComponent = component instanceof JTextField || component instanceof Spacer;

    int columnSpan = (isLast && isGreedyComponent) ? COLUMN_COUNT - column : 1;
    gridConstraints.setColSpan(columnSpan);
    gridConstraints.setAnchor(GridConstraints.ALIGN_LEFT);
    gridConstraints.setHSizePolicy(isGreedyComponent
                                   ? GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW
                                   : GridConstraints.SIZEPOLICY_CAN_SHRINK);
    gridConstraints.setVSizePolicy(component instanceof Spacer
                                   ? GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW
                                   : GridConstraints.SIZEPOLICY_FIXED);
    gridConstraints.setFill(GridConstraints.FILL_HORIZONTAL);
    parent.add(component, gridConstraints);
    if (isLast && !isGreedyComponent && column < COLUMN_COUNT - 1) {
      addComponent(parent, new Spacer(), row, column + 1, true);
    }
  }

  private static Map<Parameter, Object> getParameterObjectMap(Collection<Parameter> parameters,
                                                              Map<Parameter, Object> parametersWithDefaultValues,
                                                              Map<Parameter, Object> parametersWithNonDefaultValues) {
    Map<Parameter, Object> computedDefaultValues =
      ParameterDefaultValueComputer.newDefaultValuesMap(parameters, parametersWithNonDefaultValues);
    Map<Parameter, Object> parameterValues = Maps.newHashMap();
    parameterValues.putAll(parametersWithDefaultValues);
    for (Map.Entry<Parameter, Object> entry : computedDefaultValues.entrySet()) {
      if (!parametersWithNonDefaultValues.keySet().contains(entry.getKey()) && entry.getValue() != null) {
        parameterValues.put(entry.getKey(), entry.getValue());
      }
    }
    return parameterValues;
  }


  @Override
  public boolean isStepVisible() {
    return myState.get(AddAndroidActivityPath.KEY_SELECTED_TEMPLATE) != null;
  }

  @NotNull
  private List<JComponent> createComponents(Parameter parameter) {
    JLabel label = new JLabel(parameter.name + ":");
    final JComponent dataComponent;
    switch (parameter.type) {
      case BOOLEAN:
        label.setText(null);
        dataComponent = new JCheckBox(parameter.name);
        break;
      case ENUM:
        dataComponent = createEnumCombo(parameter);
        break;
      case EXTERNAL:
        dataComponent = createTextFieldWithBrowse(parameter);
        break;
      case STRING:
        dataComponent = new JTextField();
        break;
      case SEPARATOR:
        return Collections.<JComponent>singletonList(new JSeparator(SwingConstants.HORIZONTAL));
      default:
        throw new IllegalStateException(parameter.type.toString());
    }
    register(parameter, dataComponent);
    return Arrays.asList(label, dataComponent);
  }

  @SuppressWarnings("unchecked")
  private void register(Parameter parameter, JComponent dataComponent) {
    Key<?> key = getParameterKey(parameter);
    if (dataComponent instanceof JCheckBox) {
      register((Key<Boolean>)key, (JCheckBox)dataComponent);
    }
    else if (dataComponent instanceof JComboBox) {
      register(key, (JComboBox)dataComponent);
    }
    else if (dataComponent instanceof JTextField) {
      register((Key<String>)key, (JTextField)dataComponent);
    }
    else if (dataComponent instanceof TextFieldWithBrowseButton) {
      register((Key<String>)key, (TextFieldWithBrowseButton)dataComponent);
    }
    else {
      throw new IllegalArgumentException(dataComponent.getClass().getName());
    }
  }

  @Override
  protected JLabel getDescriptionText() {
    return myParameterDescription;
  }

  @Override
  public void deriveValues(Set<Key> modified) {
    super.deriveValues(modified);
    if (myCurrentTemplate != null) {
      updateStateWithDefaults(myCurrentTemplate.getParameters());
    }
  }

  @Override
  public boolean validate() {
    setErrorHtml(null);

    AndroidVersion minApi = myState.get(AddAndroidActivityPath.KEY_MIN_SDK);
    Integer buildApi = myState.get(AddAndroidActivityPath.KEY_BUILD_SDK);

    TemplateEntry templateEntry = myState.get(AddAndroidActivityPath.KEY_SELECTED_TEMPLATE);
    if (templateEntry == null) {
      return false;
    }
    for (Parameter param : templateEntry.getParameters()) {
      if (param != null) {
        Object value = getStateParameterValue(param);
        String error = param.validate(getProject(), getModule(), getSourceProvider(),
                                      myState.get(AddAndroidActivityPath.KEY_PACKAGE_NAME),
                                      value != null ? value : "");
        if (error != null) {
          // Highlight?
          setErrorHtml(error);
          return false;
        }

        // Check to see that the selection's constraints are met if this is a combo box
        if (value instanceof ComboBoxItem) {
          ComboBoxItem selectedItem = (ComboBoxItem)value;

          if (minApi != null && selectedItem.minApi > minApi.getApiLevel()) {
            setErrorHtml(String.format("The \"%s\" option for %s requires a minimum API level of %d",
                                       selectedItem.label, param.name, selectedItem.minApi));
            return false;
          }
          if (buildApi != null && selectedItem.minBuildApi > buildApi) {
            setErrorHtml(String.format("The \"%s\" option for %s requires a minimum API level of %d",
                                       selectedItem.label, param.name, selectedItem.minBuildApi));
            return false;
          }
        }
      }
    }
    return true;
  }

  @Nullable
  private SourceProvider getSourceProvider() {
    return myState.get(AddAndroidActivityPath.KEY_SOURCE_PROVIDER);
  }

  @Override
  public void init() {
    super.init();
    List<SourceProvider> sourceProviders = getSourceProviders();
    if (sourceProviders.size() > 0) {
      myState.put(AddAndroidActivityPath.KEY_SOURCE_PROVIDER, sourceProviders.get(0));
    }
    register(AddAndroidActivityPath.KEY_SELECTED_TEMPLATE, (JComponent)myTemplateDescription.getParent(),
             new ComponentBinding<TemplateEntry, JComponent>() {
               @Override
               public void setValue(@Nullable TemplateEntry newValue, @NotNull JComponent component) {
                 setSelectedTemplate(newValue);
               }
             }
    );
    register(KEY_DESCRIPTION, myFooterSeparator, new ComponentBinding<String, JSeparator>() {
      @Override
      public void setValue(@Nullable String newValue, @NotNull JSeparator component) {
        component.setVisible(!StringUtil.isEmpty(newValue));
      }
    });
  }

  private void setSelectedTemplate(@Nullable TemplateEntry template) {
    if (template == null) {
      return;
    }
    TemplateMetadata metadata = template.getMetadata();
    Image image = template.getImage();
    final ImageIcon icon;
    if (image != null) {
      icon = new ImageIcon(image.getScaledInstance(256, 256, Image.SCALE_SMOOTH), template.getTitle());
    }
    else {
      icon = null;
    }
    myTemplateIcon.setIcon(icon);
    myTemplateIcon.setText(template.getTitle());

    String string = ImportUIUtil.makeHtmlString(metadata.getDescription());
    myTemplateDescription.setText(string);
    //myState.put(KEY_TITLE, template.getTitle());
    updateControls(template);
  }

  private void updateControls(@Nullable TemplateEntry entry) {
    if (Objects.equal(myCurrentTemplate, entry)) {
      return;
    }
    myCurrentTemplate = entry;
    final Set<Parameter> parameters;
    if (entry != null) {
      updateStateWithDefaults(entry.getParameters());
      parameters = ImmutableSet.copyOf(filterNonUIParameters(entry));
    }
    else {
      parameters = ImmutableSet.of();
    }
    for (Component component : myTemplateParameters.getComponents()) {
      myTemplateParameters.remove(component);
      if (component instanceof JComponent) {
        deregister((JComponent)component);
      }
    }
    GridLayoutManager layout = new GridLayoutManager(parameters.size() + 1, COLUMN_COUNT);
    layout.setSameSizeHorizontally(false);
    myTemplateParameters.setLayout(layout);
    int row = 0;
    for (final Parameter parameter : parameters) {
      addComponents(parameter, row++);
    }
    addSourceSetControls(row);
  }

  private void addSourceSetControls(int row) {
    List<SourceProvider> sourceProviders = getSourceProviders();
    if (sourceProviders.size() > 1) {
      if (mySourceSetLabel == null) {
        mySourceSetLabel = new JLabel("Target Source Set:");
        mySourceSet = new ComboBox();
        register(AddAndroidActivityPath.KEY_SOURCE_PROVIDER, mySourceSet);
        setControlDescription(mySourceSet, "The selected folder contains multiple source sets, " +
                                           "this can include source sets that do not yet exist on disk. " +
                                           "Please select the target source set in which to create the files.");
      }
      mySourceSet.removeAllItems();
      for (SourceProvider sourceProvider : sourceProviders) {
        //noinspection unchecked
        mySourceSet.addItem(new ComboBoxItem(sourceProvider, sourceProvider.getName(), 0, 0));
      }
      addComponent(myTemplateParameters, mySourceSetLabel, row, 0, false);
      addComponent(myTemplateParameters, mySourceSet, row, 1, true);
    }
  }

  @NotNull
  private List<SourceProvider> getSourceProviders() {
    Module module = getModule();
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        if (myTargetDirectory != null) {
          return IdeaSourceProvider.getSourceProvidersForFile(facet, myTargetDirectory, facet.getMainSourceSet());
        }
        else {
          return IdeaSourceProvider.getAllSourceProviders(facet);
        }
      }
    }
    return ImmutableList.of();
  }

  private Iterable<Parameter> filterNonUIParameters(TemplateEntry entry) {
    return Iterables.filter(entry.getParameters(), new Predicate<Parameter>() {
      @Override
      public boolean apply(Parameter input) {
        return input != null && !StringUtil.isEmpty(input.name) && !myPresetParameters.containsKey(input.id);
      }
    });
  }

  private void updateStateWithDefaults(Collection<Parameter> parameters) {
    for (Parameter parameter : parameters) {
      if (myPresetParameters.containsKey(parameter.id)) {
        myState.unsafePut(getParameterKey(parameter), myPresetParameters.get(parameter.id));
      }
    }
    final Map<Parameter, Object> parametersAtDefault = Maps.newHashMap();
    final Map<Parameter, Object> parametersAtNonDefault = Maps.newHashMap();

    for (Parameter parameter : parameters) {
      if (isDefaultParameterValue(parameter)) {
        parametersAtDefault.put(parameter, myParameterDefaultValues.get(parameter));
      }
      else {
        parametersAtNonDefault.put(parameter, getStateParameterValue(parameter));
      }
    }
    myParameterDefaultValues = getParameterObjectMap(parameters, parametersAtDefault, parametersAtNonDefault);
    for (Map.Entry<Parameter, Object> entry : myParameterDefaultValues.entrySet()) {
      myState.unsafePut(getParameterKey(entry.getKey()), entry.getValue());
    }
  }

  @NotNull
  public Key<?> getParameterKey(@NotNull Parameter parameter) {
    //noinspection ConstantConditions
    return myParameterToKey.apply(parameter);
  }

  @Nullable
  private Object getStateParameterValue(Parameter parameter) {
    if (myPresetParameters.containsKey(parameter.id)) {
      return myPresetParameters.get(parameter.id);
    }
    else {
      return myState.get(getParameterKey(parameter));
    }
  }

  private boolean isDefaultParameterValue(Parameter parameter) {
    Object stateValue = getStateParameterValue(parameter);
    if (stateValue == null) {
      return true;
    }
    else {
      return Objects.equal(myParameterDefaultValues.get(parameter), stateValue);
    }
  }

  private void addComponents(Parameter parameter, int row) {
    List<JComponent> keyComponents = components.get(parameter);
    if (keyComponents == null) {
      keyComponents = createComponents(parameter);
      components.put(parameter, keyComponents);
    }
    int column = 0;
    for (Iterator<JComponent> iterator = keyComponents.iterator(); iterator.hasNext(); ) {
      JComponent keyComponent = iterator.next();
      addComponent(myTemplateParameters, keyComponent, row, column++, !iterator.hasNext());
      setControlDescription(keyComponent, parameter.help);
    }
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Template parameters";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTemplateParameters;
  }

  private static class ParameterKeyFunction implements Function<Parameter, Key<?>> {
    @Override
    public Key<?> apply(Parameter input) {
      final Class<?> clazz;
      switch (input.type) {
        case BOOLEAN:
          clazz = Boolean.class;
          break;
        case ENUM:
        case EXTERNAL:
        case STRING:
        case SEPARATOR:
          clazz = String.class;
          break;
        default:
          throw new IllegalStateException(input.type.toString());
      }
      assert input.id != null;
      return createKey(input.id, ScopedStateStore.Scope.PATH, clazz);
    }
  }
}
