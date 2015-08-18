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
package com.android.tools.idea.editors.hprof.descriptors;

import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Instance;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ContainerDescriptorImpl extends NodeDescriptorImpl {
  @NotNull ClassObj myClassObj;
  @NotNull private List<Instance> myInstancesCache;

  public ContainerDescriptorImpl(@NotNull ClassObj classObj, int heapId) {
    myClassObj = classObj;
    myInstancesCache = myClassObj.getHeapInstances(heapId);
  }

  @NotNull
  public ClassObj getClassObj() {
    return myClassObj;
  }

  @NotNull
  public List<Instance> getInstances() {
    return myInstancesCache;
  }

  @Override
  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) throws EvaluateException {
    return null;
  }

  @Override
  public boolean isExpandable() {
    return true;
  }

  @Override
  public void setContext(EvaluationContextImpl context) {

  }
}
