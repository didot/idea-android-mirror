/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.android.tools.idea.gradle.dsl.api.BuildModelNotification;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.model.GradleSettingsModelImpl;
import com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference;
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.ModificationAware;
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.DERIVED;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.followElement;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.isNonExpressionPropertiesElement;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.isPropertiesElementOrMap;
import static com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.METHOD;
import static com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement.*;
import static com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT;
import static com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement.getStandardProjectKey;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public abstract class GradleDslElementImpl implements GradleDslElement, ModificationAware {
  @NotNull private static final String SINGLE_QUOTES = "\'";
  @NotNull private static final String DOUBLE_QUOTES = "\"";
  @NotNull protected GradleNameElement myName;

  @Nullable protected GradleDslElement myParent;

  @NotNull protected List<GradlePropertiesDslElement> myHolders = new ArrayList<>();

  @NotNull private final GradleDslFile myDslFile;

  @Nullable private PsiElement myPsiElement;

  @Nullable private GradleDslClosure myClosureElement;
  @Nullable private GradleDslClosure myUnsavedClosure;

  private long myLastCommittedModificationCount;
  private long myModificationCount;

  /**
   * Represents the expressed syntax of this element (if from the parser), defaulting to METHOD.
   */
  @NotNull protected ExternalNameSyntax mySyntax;

  @NotNull private PropertyType myElementType;

  @NotNull protected final List<GradleReferenceInjection> myDependencies = new ArrayList<>();
  @NotNull protected final List<GradleReferenceInjection> myDependents = new ArrayList<>();

  @Nullable private ModelEffectDescription myModelEffectDescription;

  /**
   * Creates an instance of a {@link GradleDslElement}
   *
   * @param parent     the parent {@link GradleDslElement} of this element. The parent element should always be a not-null value except if
   *                   this element is the root element, i.e a {@link GradleDslFile}.
   * @param psiElement the {@link PsiElement} of this dsl element.
   * @param name       the name of this element.
   */
  protected GradleDslElementImpl(@Nullable GradleDslElement parent, @Nullable PsiElement psiElement, @NotNull GradleNameElement name) {
    assert parent != null || this instanceof GradleDslFile;

    myParent = parent;
    myPsiElement = psiElement;
    myName = name;


    if (parent == null) {
      myDslFile = (GradleDslFile)this;
    }
    else {
      myDslFile = parent.getDslFile();
    }

    mySyntax = METHOD;
    // Default to DERIVED, this is overwritten in the parser if required for the given element type.
    myElementType = DERIVED;
  }

  @Override
  public void setParsedClosureElement(@NotNull GradleDslClosure closureElement) {
    myClosureElement = closureElement;
  }

  @Override
  public void setNewClosureElement(@Nullable GradleDslClosure closureElement) {
    myUnsavedClosure = closureElement;
    setModified();
  }

  @Override
  @Nullable
  public GradleDslClosure getUnsavedClosure() {
    return myUnsavedClosure;
  }

  @Override
  @Nullable
  public GradleDslClosure getClosureElement() {
    return myUnsavedClosure == null ? myClosureElement : myUnsavedClosure;
  }

  @Override
  @NotNull
  public String getName() {
    return myModelEffectDescription == null ? myName.name() : myModelEffectDescription.property.name;
  }

  @Override
  @NotNull
  public String getQualifiedName() {
    // Don't include the name of the parent if this element is a direct child of the file.
    if (myParent == null || myParent instanceof GradleDslFile) {
      return GradleNameElement.escape(getName());
    }

    String ourName = getName();
    return myParent.getQualifiedName() + (ourName.isEmpty() ? "" : "." + GradleNameElement.escape(ourName));
  }

  @Override
  @NotNull
  public String getFullName() {
    if (myModelEffectDescription == null) {
      return myName.fullName();
    }
    else {
      List<String> parts = myName.qualifyingParts();
      parts.add(getName());
      return GradleNameElement.createNameFromParts(parts);
    }
  }

  @Override
  @NotNull
  public GradleNameElement getNameElement() {
    return myName;
  }

  @Override
  public void rename(@NotNull String newName) {
    rename(Arrays.asList(newName));
  }

  @Override
  public void rename(@NotNull List<String> hierarchicalName) {
    myName.rename(hierarchicalName);
    setModified();

    // If we are a GradleDslSimpleExpression we need to ensure our dependencies are correct.
    if (!(this instanceof GradleDslSimpleExpression)) {
      return;
    }

    List<GradleReferenceInjection> dependents = getDependents();
    unregisterAllDependants();

    reorder();

    // The property we renamed could have been shadowing another one. Attempt to re-resolve all dependents.
    dependents.forEach(e -> e.getOriginElement().resolve());

    // The new name could also create new dependencies, we need to make sure to resolve them.
    getDslFile().getContext().getDependencyManager().resolveWith(this);
  }

  @Override
  @Nullable
  public GradleDslElement getParent() {
    return myParent;
  }

  @Override
  public void setParent(@NotNull GradleDslElement parent) {
    myParent = parent;
  }

  @Override
  @NotNull
  public List<GradlePropertiesDslElement> getHolders() {
    return myHolders;
  }

  @Override
  public void addHolder(@NotNull GradlePropertiesDslElement holder) {
    myHolders.add(holder);
  }

  @Override
  @Nullable
  public PsiElement getPsiElement() {
    return myPsiElement;
  }

  @Override
  public void setPsiElement(@Nullable PsiElement psiElement) {
    myPsiElement = psiElement;
  }

  @Override
  @NotNull
  public ExternalNameSyntax getExternalSyntax() {
    return mySyntax;
  }

  @Override
  public void setExternalSyntax(@NotNull ExternalNameSyntax syntax) {
    mySyntax = syntax;
  }

  @Override
  @NotNull
  public PropertyType getElementType() {
    return myElementType;
  }

  @Override
  public void setElementType(@NotNull PropertyType propertyType) {
    myElementType = propertyType;
  }

  @Override
  @NotNull
  public GradleDslFile getDslFile() {
    return myDslFile;
  }

  @Override
  @NotNull
  public List<GradleReferenceInjection> getResolvedVariables() {
    ImmutableList.Builder<GradleReferenceInjection> resultBuilder = ImmutableList.builder();
    for (GradleDslElement child : getChildren()) {
      resultBuilder.addAll(child.getResolvedVariables());
    }
    return resultBuilder.build();
  }

  @Override
  @Nullable
  public GradleDslElement requestAnchor(@NotNull GradleDslElement element) {
    return null;
  }

  @Override
  @Nullable
  public GradleDslElement getAnchor() {
    return myParent == null ? null : myParent.requestAnchor(this);
  }

  @Override
  @Nullable
  public PsiElement create() {
    return myDslFile.getWriter().createDslElement(this);
  }

  @Override
  @Nullable
  public PsiElement move() {
    return myDslFile.getWriter().moveDslElement(this);
  }

  @Override
  public void delete() {
    this.getDslFile().getWriter().deleteDslElement(this);
  }

  @Override
  public void setModified() {
    modify();
    if (myParent != null) {
      myParent.setModified();
    }
  }

  @Override
  public boolean isModified() {
    return getLastCommittedModificationCount() != getModificationCount();
  }

  @Override
  public boolean isBlockElement() {
    return false;
  }

  @Override
  public boolean isInsignificantIfEmpty() {
    return true;
  }

  @Override
  @NotNull
  public abstract Collection<GradleDslElement> getChildren();

  @Override
  public final void applyChanges() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    apply();
    commit();
  }

  protected abstract void apply();

  @Override
  public final void resetState() {
    reset();
    commit();
  }

  protected abstract void reset();

  @Override
  @NotNull
  public List<GradleDslElement> getContainedElements(boolean includeProperties) {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Map<String, GradleDslElement> getInScopeElements() {
    Map<String, GradleDslElement> results = new LinkedHashMap<>();

    if (isNonExpressionPropertiesElement(this)) {
      GradlePropertiesDslElement thisElement = (GradlePropertiesDslElement)this;
      results.putAll(thisElement.getVariableElements());
    }

    // Trace parents finding any variable elements present.
    GradleDslElement currentElement = this;
    while (currentElement != null && currentElement.getParent() != null) {
      currentElement = currentElement.getParent();
      if (isNonExpressionPropertiesElement(currentElement)) {
        GradlePropertiesDslElement element = (GradlePropertiesDslElement)currentElement;
        results.putAll(element.getVariableElements());
      }
    }

    // Get Ext properties from the GradleDslFile, and the EXT properties from the buildscript.
    if (currentElement instanceof GradleDslFile) {
      GradleDslFile file = (GradleDslFile)currentElement;
      while (file != null) {
        ExtDslElement ext = file.getPropertyElement(ExtDslElement.EXT);
        if (ext != null) {
          results.putAll(ext.getPropertyElements());
        }
        // Add properties files properties
        GradleDslFile propertiesFile = file.getSiblingDslFile();
        if (propertiesFile != null) {
          // Only properties with no qualifier are picked up by build scripts.
          Map<String, GradleDslElement> filteredProperties =
            propertiesFile.getPropertyElements().entrySet().stream().filter(entry -> !entry.getKey().contains("."))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
          results.putAll(filteredProperties);
        }
        // Add BuildScriptExt properties.
        BuildScriptDslElement buildScriptElement = file.getPropertyElement(BUILDSCRIPT);
        if (buildScriptElement != null) {
          ExtDslElement buildScriptExt = buildScriptElement.getPropertyElement(ExtDslElement.EXT);
          if (buildScriptExt != null) {
            results.putAll(buildScriptExt.getPropertyElements());
          }
        }

        file = file.getParentModuleDslFile();
      }
    }

    return results;
  }

  @Override
  @NotNull
  public <T extends BuildModelNotification> T notification(@NotNull NotificationTypeReference<T> type) {
    return getDslFile().getContext().getNotificationForType(myDslFile, type);
  }

  @Override
  public void registerDependent(@NotNull GradleReferenceInjection injection) {
    assert injection.isResolved() && injection.getToBeInjected() == this;
    myDependents.add(injection);
  }

  @Override
  public void unregisterDependent(@NotNull GradleReferenceInjection injection) {
    assert injection.isResolved() && injection.getToBeInjected() == this;
    assert myDependents.contains(injection);
    myDependents.remove(injection);
  }

  @Override
  public void unregisterAllDependants() {
    // We need to create a new array to avoid concurrent modification exceptions.
    myDependents.forEach(e -> {
      // Break the dependency.
      e.resolveWith(null);
      // Register with DependencyManager
      getDslFile().getContext().getDependencyManager().registerUnresolvedReference(e);
    });
    myDependents.clear();
  }

  @Override
  @NotNull
  public List<GradleReferenceInjection> getDependents() {
    return new ArrayList<>(myDependents);
  }

  @Override
  @NotNull
  public List<GradleReferenceInjection> getDependencies() {
    return new ArrayList<>(myDependencies);
  }

  @Override
  public void updateDependenciesOnAddElement(@NotNull GradleDslElement newElement) {
    newElement.resolve();
    newElement.getDslFile().getContext().getDependencyManager().resolveWith(newElement);
  }

  @Override
  public void updateDependenciesOnReplaceElement(@NotNull GradleDslElement oldElement, @NotNull GradleDslElement newElement) {
    // Switch dependents to point to the new element.
    List<GradleReferenceInjection> injections = oldElement.getDependents();
    oldElement.unregisterAllDependants();
    injections.forEach(e -> e.resolveWith(newElement));
    // Register all the dependents with this new element.
    injections.forEach(newElement::registerDependent);

    // Go though our dependencies and unregister us as a dependent.
    oldElement.getResolvedVariables().forEach(e -> {
      GradleDslElement toBeInjected = e.getToBeInjected();
      if (toBeInjected != null) {
        toBeInjected.unregisterDependent(e);
      }
    });
  }

  @Override
  public void updateDependenciesOnRemoveElement(@NotNull GradleDslElement oldElement) {
    List<GradleReferenceInjection> dependents = oldElement.getDependents();
    oldElement.unregisterAllDependants();

    // The property we remove could have been shadowing another one. Attempt to re-resolve all dependents.
    dependents.forEach(e -> e.getOriginElement().resolve());

    // Go though our dependencies and unregister us as a dependent.
    oldElement.getResolvedVariables().forEach(e -> {
      GradleDslElement toBeInjected = e.getToBeInjected();
      if (toBeInjected != null) {
        toBeInjected.unregisterDependent(e);
      }
    });
  }

  @Override
  public void resolve() {
  }

  protected void reorder() {
    if (myParent instanceof ExtDslElement) {
      ((ExtDslElement)myParent).reorderAndMaybeGetNewIndex(this);
    }
  }

  @Override
  public long getModificationCount() {
    return myModificationCount;
  }

  public long getLastCommittedModificationCount() {
    return myLastCommittedModificationCount;
  }

  @Override
  public void modify() {
    myModificationCount++;
    myDependents.forEach(e -> e.getOriginElement().modify());
  }

  public void commit() {
    myLastCommittedModificationCount = myModificationCount;
  }

  @Nullable
  public static String getPsiText(@NotNull PsiElement psiElement) {
    return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> psiElement.getText());
  }

  @Override
  public boolean isNewEmptyBlockElement() {
    if (myPsiElement != null) {
      return false;
    }

    if (!isBlockElement() || !isInsignificantIfEmpty()) {
      return false;
    }

    Collection<GradleDslElement> children = getContainedElements(true);
    if (children.isEmpty()) {
      return true;
    }

    for (GradleDslElement child : children) {
      if (!child.isNewEmptyBlockElement()) {
        return false;
      }
    }

    return true;
  }

  @Override
  @NotNull
  public ImmutableMap<Pair<String, Integer>, ModelEffectDescription> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return ImmutableMap.of();
  }

  @Nullable
  @Override
  public ModelEffectDescription getModelEffect() {
    return myModelEffectDescription;
  }

  @Override
  public void setModelEffect(@Nullable ModelEffectDescription effect) {
    myModelEffectDescription = effect;
  }

  @Nullable
  @Override
  public ModelPropertyDescription getModelProperty() {
    return myModelEffectDescription == null ? null : myModelEffectDescription.property;
  }

  @Nullable
  public static GradleDslElement dereference(@NotNull GradleDslElement element, @NotNull String index) {
    if (element instanceof GradleDslExpressionList) {
      int offset;
      try {
        offset = Integer.parseInt(index);
      }
      catch (NumberFormatException e) {
        return null;
      }

      GradleDslExpressionList list = (GradleDslExpressionList)element;
      if (list.getExpressions().size() <= offset) {
        return null;
      }
      return list.getExpressions().get(offset);
    }
    else if (element instanceof GradleDslExpressionMap) {
      GradleDslExpressionMap map = (GradleDslExpressionMap)element;
      index = stripQuotes(index);

      return map.getPropertyElement(index);
    }
    else if (element instanceof GradleDslLiteral && ((GradleDslLiteral)element).isReference()) {
      GradleDslElement value = followElement((GradleDslLiteral)element);
      if (value == null) {
        return null;
      }
      else {
        return dereference(value, index);
      }
    }
    else {
      return null;
    }
  }

  @Nullable
  private static GradleDslElement extractElementFromProperties(@NotNull GradlePropertiesDslElement properties,
                                                               @NotNull String name,
                                                               GradleDslNameConverter converter,
                                                               boolean sameScope,
                                                               @Nullable GradleDslElement childElement,
                                                               boolean includeSelf) {
    // First check if any indexing has been done.
    Matcher indexMatcher = GradleNameElement.INDEX_PATTERN.matcher(name);

    // If the index matcher doesn't give us anything, just attempt to find the property on the element;
    if (!indexMatcher.find()) {
      ModelPropertyDescription property = converter.modelDescriptionForParent(name, properties);
      String modelName = property == null ? name : property.name;

      return sameScope
             ? properties.getElementBefore(childElement, modelName, includeSelf)
             : properties.getPropertyElementBefore(childElement, modelName, includeSelf);
    }

    // Sanity check
    if (indexMatcher.groupCount() != 2) {
      return null;
    }

    // We have some index present, find the element we need to index. The first match, the property, is always the whole match.
    String elementName = indexMatcher.group(0);
    if (elementName == null) {
      return null;
    }
    ModelPropertyDescription property = converter.modelDescriptionForParent(elementName, properties);
    String modelName = property == null ? elementName : property.name;

    GradleDslElement element =
      sameScope
      ? properties.getElementBefore(childElement, modelName, includeSelf)
      : properties.getPropertyElementBefore(childElement, modelName, includeSelf);

    // Construct a list of all of the index parts
    Deque<String> indexParts = new ArrayDeque<>();
    while (indexMatcher.find()) {
      // Sanity check
      if (indexMatcher.groupCount() != 2) {
        return null;
      }
      // second and subsequent matches of INDEX_PATTERN have .group(0) being "[...]", and .group(1) the text inside the brackets.
      indexParts.add(indexMatcher.group(1));
    }

    // Go through each index and search for the element.
    while (!indexParts.isEmpty()) {
      String index = indexParts.pop();
      // Ensure the element is not null
      if (element == null) {
        return null;
      }

      // Get the type of the element and ensure the index is compatible, e.g numerical index for a list.
      element = dereference(element, index);
    }

    return element;
  }

  @Nullable
  private static GradleDslFile findDslFile(GradleDslFile rootModuleDslFile, File moduleDirectory) {
    if (filesEqual(rootModuleDslFile.getDirectoryPath(), moduleDirectory)) {
      return rootModuleDslFile;
    }

    for (GradleDslFile dslFile : rootModuleDslFile.getChildModuleDslFiles()) {
      if (filesEqual(dslFile.getDirectoryPath(), moduleDirectory)) {
        return dslFile;
      }
      GradleDslFile childDslFile = findDslFile(dslFile, moduleDirectory);
      if (childDslFile != null) {
        return dslFile;
      }
    }
    return null;
  }

  @Nullable
  private static GradleDslElement resolveReferenceOnPropertiesElement(@NotNull GradlePropertiesDslElement properties,
                                                                      @NotNull List<String> nameParts,
                                                                      GradleDslNameConverter converter,
                                                                      @NotNull List<GradleDslElement> trace) {
    int traceIndex = trace.size() - 1;
    // Go through each of the parts and extract the elements from each of them.
    GradleDslElement element;
    for (int i = 0; i < nameParts.size() - 1; i++) {
      // Only look for variables on the first iteration, otherwise only properties should be accessible.
      element = extractElementFromProperties(properties, nameParts.get(i), converter, i == 0, traceIndex < 0 ? null : trace.get(traceIndex--),
                                             traceIndex >= 0);
      if (element instanceof GradleDslLiteral && ((GradleDslLiteral)element).isReference()) {
        element = followElement((GradleDslLiteral)element);
      }

      // All elements we find must be GradlePropertiesDslElement on all but the last iteration.
      if (!isPropertiesElementOrMap(element)) {
        return null;
      }
      // isPropertiesElementOrMap should always return false when is not an instance of GradlePropertiesDslElement.
      //noinspection ConstantConditions
      properties = (GradlePropertiesDslElement)element;
    }

    return extractElementFromProperties(properties, nameParts.get(nameParts.size() - 1), converter, nameParts.size() == 1,
                                        traceIndex < 0 ? null : trace.get(traceIndex--), traceIndex >= 0);
  }

  @Nullable
  private static GradleDslElement resolveReferenceOnElement(@NotNull GradleDslElement element,
                                                            @NotNull List<String> nameParts,
                                                            GradleDslNameConverter converter,
                                                            boolean resolveWithOrder,
                                                            boolean checkExt,
                                                            int ignoreParentNumber) {
    // We need to keep track of the last element we saw to ensure we only check items BEFORE the one we are resolving.
    Stack<GradleDslElement> elementTrace = new Stack<>();
    if (resolveWithOrder) {
      elementTrace.push(element);
    }
    // Make sure we don't check any nested scope for the element.
    while (ignoreParentNumber-- > 0 && element != null && !(element instanceof GradleDslFile) && !(element instanceof BuildScriptDslElement)) {
      element = element.getParent();
    }
    while (element != null) {
      GradleDslElement lastElement = elementTrace.isEmpty() ? null : elementTrace.peek();
      if (isPropertiesElementOrMap(element)) {
        GradleDslElement propertyElement = resolveReferenceOnPropertiesElement((GradlePropertiesDslElement)element, nameParts,
                                                                               converter, elementTrace);
        if (propertyElement != null) {
          return propertyElement;
        }

        // If it is then we have already checked the ExtElement of this object.
        if (!(lastElement instanceof ExtDslElement) && checkExt) {
          GradleDslElement extElement =
            ((GradlePropertiesDslElement)element).getPropertyElementBefore(lastElement, EXT.name, false);
          if (extElement instanceof ExtDslElement) {
            GradleDslElement extPropertyElement =
              resolveReferenceOnPropertiesElement((ExtDslElement)extElement, nameParts, converter, elementTrace);
            if (extPropertyElement != null) {
              return extPropertyElement;
            }
          }
        }

        if (!(lastElement instanceof BuildScriptDslElement)) {
          GradleDslElement bsDslElement =
            ((GradlePropertiesDslElement)element).getPropertyElementBefore(element, BUILDSCRIPT.name, false);
          if (bsDslElement instanceof BuildScriptDslElement) {
            GradleDslElement bsElement =
              resolveReferenceOnElement(bsDslElement, nameParts, converter, true /* Must be true or we just jump between buildscript -> parent */,
                                        false, -1);
            if (bsElement != null) {
              return bsElement;
            }
          }
        }
      }

      if (resolveWithOrder) {
        elementTrace.push(element);
      }

      // Don't resolve up the parents for BuildScript elements.
      if (element instanceof BuildScriptDslElement) {
        return null;
      }
      element = element.getParent();
    }

    return null;
  }

  @Nullable
  private static GradleDslElement resolveReferenceInPropertiesFile(@NotNull GradleDslFile buildDslFile, @NotNull String referenceText) {
    GradleDslFile propertiesDslFile = buildDslFile.getSiblingDslFile();
    return propertiesDslFile != null ? propertiesDslFile.getPropertyElement(referenceText) : null;
  }

  @Nullable
  private static GradleDslElement resolveReferenceInParentModules(
    @NotNull GradleDslFile dslFile,
    @NotNull List<String> referenceText,
    GradleDslNameConverter converter
  ) {
    GradleDslFile parentDslFile = dslFile.getParentModuleDslFile();
    while (parentDslFile != null) {
      ExtDslElement extDslElement = parentDslFile.getPropertyElement(EXT);
      if (extDslElement != null) {
        GradleDslElement extPropertyElement = resolveReferenceOnPropertiesElement(extDslElement, referenceText, converter, new Stack<>());
        if (extPropertyElement != null) {
          return extPropertyElement;
        }
      }

      BuildScriptDslElement bsDslElement = parentDslFile.getPropertyElement(BUILDSCRIPT);
      if (bsDslElement != null) {
        GradleDslElement bsElement = resolveReferenceOnElement(bsDslElement, referenceText, converter, false, true, -1);
        if (bsElement != null) {
          return bsElement;
        }
      }

      if (parentDslFile.getParentModuleDslFile() == null) {
        // This is the root project build.gradle file and the root project's gradle.properties file is already considered in
        // resolveReferenceInSameModule method.
        return null;
      }

      GradleDslElement propertyElement = resolveReferenceInPropertiesFile(parentDslFile, String.join(".", referenceText));
      if (propertyElement != null) {
        return propertyElement;
      }

      parentDslFile = parentDslFile.getParentModuleDslFile();
    }
    return null;
  }

  @Nullable
  private static GradleDslElement resolveReferenceInSameModule(@NotNull GradleDslElement startElement,
                                                               @NotNull List<String> referenceText,
                                                               GradleDslNameConverter converter,
                                                               boolean resolveWithOrder) {
    // Try to resolve in the build.gradle file the startElement belongs to.
    GradleDslElement element =
      resolveReferenceOnElement(startElement, referenceText, converter, resolveWithOrder, true, startElement.getNameElement().fullNameParts().size());
    if (element != null) {
      return element;
    }

    // Join the text before looking in the properties files.
    String text = String.join(".", referenceText);

    // TODO: Add support to look at <GRADLE_USER_HOME>/gradle.properties before looking at this module's gradle.properties file.

    // Try to resolve in the gradle.properties file of the startElement's module.
    GradleDslFile dslFile = startElement.getDslFile();
    GradleDslElement propertyElement = resolveReferenceInPropertiesFile(dslFile, text);
    if (propertyElement != null) {
      return propertyElement;
    }

    // Ensure we check the buildscript as well.
    BuildScriptDslElement bsDslElement = dslFile.getPropertyElement(BUILDSCRIPT);
    if (bsDslElement != null) {
      GradleDslElement bsElement = resolveReferenceOnElement(bsDslElement, referenceText, converter, false, true, -1);
      if (bsElement != null) {
        return bsElement;
      }
    }


    if (dslFile.getParentModuleDslFile() == null) {
      return null; // This is the root project build.gradle file and there is no further path to look up.
    }

    // Try to resolve in the root project gradle.properties file.
    GradleDslFile rootProjectDslFile = dslFile;
    while (true) {
      GradleDslFile parentModuleDslFile = rootProjectDslFile.getParentModuleDslFile();
      if (parentModuleDslFile == null) {
        break;
      }
      rootProjectDslFile = parentModuleDslFile;
    }
    return resolveReferenceInPropertiesFile(rootProjectDslFile, text);
  }

  @Nullable
  @Override
  public GradleDslElement resolveExternalSyntaxReference(@NotNull String referenceText, boolean resolveWithOrder) {
    GradleDslElement searchStartElement = this;
    GradleDslParser parser = getDslFile().getParser();
    referenceText = parser.convertReferenceText(searchStartElement, referenceText);

    return resolveInternalSyntaxReference(referenceText, resolveWithOrder);
  }

  @Nullable
  @Override
  public GradleDslElement resolveInternalSyntaxReference(@NotNull String referenceText, boolean resolveWithOrder) {
    GradleDslElement searchStartElement = this;
    GradleDslParser parser = getDslFile().getParser();

    boolean withinBuildscript = false;
    GradleDslElement element = this;
    while (element != null) {
      element = element.getParent();
      if (element instanceof BuildScriptDslElement) {
        withinBuildscript = true;
        break;
      }
    }

    List<String> referenceTextSegments = GradleNameElement.split(referenceText);
    int index = 0;
    int segmentCount = referenceTextSegments.size();
    for (; index < segmentCount; index++) {
      // Resolve the project reference elements like parent, rootProject etc.
      GradleDslFile dslFile = resolveProjectReference(searchStartElement, referenceTextSegments.get(index));
      if (dslFile == null) {
        break;
      }
      // start the search for our element at the top-level of the Dsl file (but see below for buildscript handling)
      searchStartElement = dslFile;
    }

    /* For a project with the below hierarchy ...

    | <GRADLE_USER_HOME>/gradle.properties
    | RootProject
    | - - build.gradle
    | - - gradle.properties
    | - - FirstLevelChildProject
    | - - - - build.gradle
    | - - - - gradle.properties
    | - - - - SecondLevelChildProject
    | - - - - - - build.gradle
    | - - - - - - gradle.properties
    | - - - - - - ThirdLevelChildProject
    | - - - - - - - - build.gradle
    | - - - - - - - - gradle.properties

    the resolution path for a property defined in ThirdLevelChildProject's build.gradle file will be ...

      1. ThirdLevelChildProject/build.gradle
      2. <GRADLE_USER_HOME>/gradle.properties
      3. ThirdLevelChildProject/gradle.properties
      4. RootProject/gradle.properties
      5. SecondLevelChildProject/build.gradle
      6. SecondLevelChildProject/gradle.properties
      7. FirstLevelChildProject/build.gradle
      8. FirstLevelChildProject/gradle.properties
      9. RootProject/build.gradle
    */

    GradleDslElement resolvedElement = null;
    GradleDslFile dslFile = searchStartElement.getDslFile();
    if (index >= segmentCount) {
      // the reference text is fully resolved by now. ex: if the while text itself is "rootProject" etc.
      resolvedElement = searchStartElement;
    }
    else {
      // Search in the file that searchStartElement belongs to.
      referenceTextSegments = referenceTextSegments.subList(index, segmentCount);
      // if we are resolving in the general context of buildscript { } within the same module, then build code external to the
      // buildscript block will not yet have run: restrict search to the buildscript element (which should exist)
      if (dslFile == searchStartElement  && withinBuildscript) {
        searchStartElement = dslFile.getPropertyElement(BUILDSCRIPT);
      }
      if (searchStartElement != null) {
        resolvedElement = resolveReferenceInSameModule(searchStartElement, referenceTextSegments, parser, resolveWithOrder);
      }
    }

    if (resolvedElement == null) {
      // Now look in the parent projects ext blocks.
      resolvedElement = resolveReferenceInParentModules(dslFile, referenceTextSegments, parser);
    }

    return resolvedElement;
  }

  @Nullable
  private static GradleDslFile resolveProjectReference(GradleDslElement startElement, @NotNull String projectReference) {
    GradleDslFile dslFile = startElement.getDslFile();
    if ("project".equals(projectReference)) {
      return dslFile;
    }

    if ("parent".equals(projectReference)) {
      return dslFile.getParentModuleDslFile();
    }

    if ("rootProject".equals(projectReference)) {
      while (dslFile != null && !filesEqual(dslFile.getDirectoryPath(), virtualToIoFile(dslFile.getProject().getBaseDir()))) {
        dslFile = dslFile.getParentModuleDslFile();
      }
      return dslFile;
    }

    String standardProjectKey = getStandardProjectKey(projectReference);
    if (standardProjectKey != null) { // project(':project:path')
      String modulePath = standardProjectKey.substring(standardProjectKey.indexOf('\'') + 1, standardProjectKey.lastIndexOf('\''));
      VirtualFile settingFile = dslFile.tryToFindSettingsFile();
      if (settingFile == null) {
        return null;
      }
      GradleSettingsFile file = dslFile.getContext().getOrCreateSettingsFile(settingFile);
      GradleSettingsModel model = new GradleSettingsModelImpl(file);
      File moduleDirectory = model.moduleDirectory(modulePath);
      if (moduleDirectory == null) {
        return null;
      }
      while (dslFile != null && !filesEqual(dslFile.getDirectoryPath(), virtualToIoFile(dslFile.getProject().getBaseDir()))) {
        dslFile = dslFile.getParentModuleDslFile();
      }
      if (dslFile == null) {
        return null;
      }
      return findDslFile(dslFile, moduleDirectory); // root module dsl File.
    }
    return null;
  }

  @NotNull
  private static String stripQuotes(@NotNull String index) {
    if (index.startsWith(SINGLE_QUOTES) && index.endsWith(SINGLE_QUOTES) ||
        index.startsWith(DOUBLE_QUOTES) && index.endsWith(DOUBLE_QUOTES)) {
      return index.substring(1, index.length() - 1);
    }
    return index;
  }
}
