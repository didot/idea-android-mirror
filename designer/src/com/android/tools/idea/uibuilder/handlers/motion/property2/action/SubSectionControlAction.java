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
package com.android.tools.idea.uibuilder.handlers.motion.property2.action;

import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector;
import com.android.tools.idea.uibuilder.handlers.motion.property2.MotionLayoutAttributesModel;
import com.android.tools.idea.uibuilder.handlers.motion.property2.MotionSelection;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.android.tools.property.panel.api.InspectorLineModel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SubSectionControlAction extends AnAction {
  private final NelePropertyItem myProperty;
  private InspectorLineModel myLineModel;
  private LookupResult myLookupResult;

  public SubSectionControlAction(@Nullable NelePropertyItem property) {
    myProperty = property;
    myLookupResult = new LookupResult();
  }

  public void setLineModel(@NotNull InspectorLineModel lineModel) {
    myLineModel = lineModel;
    myLineModel.setEnabled(check());
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    boolean isPresent = check();
    Presentation presentation = event.getPresentation();
    presentation.setIcon(isPresent ? AllIcons.Diff.GutterCheckBoxSelected : AllIcons.Diff.GutterCheckBox);
    if (myLineModel != null) {
      myLineModel.setEnabled(isPresent);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    boolean isPresent = check();
    if (isPresent) {
      MTag.TagWriter tagWriter = myLookupResult.sectionTag.getTagWriter();
      tagWriter.deleteTag();
      tagWriter.commit("Remove " + myLookupResult.sectionName);
    }
    else {
      MTag.TagWriter tagWriter = MotionLayoutAttributesModel.createConstraintSectionTag(myLookupResult.selection,
                                                                                        myLookupResult.sectionTag,
                                                                                        myLookupResult.sectionName);
      tagWriter.commit(String.format("Create %1$s tag", myLookupResult.sectionName));
    }
  }

  private boolean check() {
    if (myProperty == null) {
      return false;
    }
    MotionSelection selection = MotionLayoutAttributesModel.getMotionSelection(myProperty);
    String sectionName = MotionLayoutAttributesModel.getSubTag(myProperty);
    if (selection == null || selection.getType() != MotionEditorSelector.Type.CONSTRAINT || sectionName == null) {
      return false;
    }
    MotionSceneTag constraintTag = selection.getMotionSceneTag();
    if (constraintTag == null) {
      return false;
    }
    MotionSceneTag sectionTag = MotionLayoutAttributesModel.getConstraintSectionTag(constraintTag, sectionName);
    myLookupResult.selection = selection;
    myLookupResult.constraintTag = constraintTag;
    myLookupResult.sectionName = sectionName;
    myLookupResult.sectionTag = sectionTag;

    return sectionTag != null;
  }

  private static class LookupResult {
    MotionSelection selection;
    MotionSceneTag constraintTag;
    String sectionName;
    MotionSceneTag sectionTag;
  }
}
