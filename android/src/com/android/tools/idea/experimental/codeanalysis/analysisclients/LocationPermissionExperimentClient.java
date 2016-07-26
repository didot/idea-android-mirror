/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.experimental.codeanalysis.analysisclients;

import com.android.tools.idea.experimental.codeanalysis.PsiCFGScene;
import com.android.tools.idea.experimental.codeanalysis.callgraph.Callgraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGClass;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGMethod;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGPartialMethodSignature;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGPartialMethodSignatureBuilder;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.GraphNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.AssignStmt;
import com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.Stmt;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * An experimental clients to test
 * the potential of the Call graph
 * and CFG
 * Created by haowei on 7/19/16.
 */
public class LocationPermissionExperimentClient extends AnalysisClientBase {

  private static final String LOCATION_MANAGER_CLASS_NAME = "android.location.LocationManager";
  private static final String GOOGLE_MAPS_API_CLASS = "com.google.android.maps.MyLocationOverlay";

  private PsiCFGClass LocationManagerCFGClass;
  private PsiCFGClass GoogleMapsAPIClass;

  private List<PsiCFGMethod> targetMethodList;
  private Project mProject;

  private Callgraph mCG;

  //private List<PsiCFGMethod> longestCallStack;
  private List<PsiCFGMethod> longestMethodStack;
  private List<GraphNode> longestNodeStack;

  private List<Pair<PsiCFGMethod, PsiElement>> invocationSiteCollection;




  public LocationPermissionExperimentClient(PsiCFGScene scene) {
    super(scene);
    targetMethodList = Lists.newArrayList();
    mProject = scene.getProject();
    mCG = mScene.getCallGraph();
    LocationManagerCFGClass = null;
    GoogleMapsAPIClass = null;
    invocationSiteCollection = Lists.newArrayList();
  }

  @Override
  public void runAnalysis() {
    getTargetMethodsList();

    if (targetMethodList.isEmpty()) {
      System.out.println("No Location API used in this project.");
      return;
    }

    for (PsiCFGMethod currentTarget : targetMethodList) {
      //System.out.println("TargetMethod : " + currentTarget.getName());
      resolveInitialCaller(currentTarget);
    }
    outputInvocationSiteInfos();
    tagTheResult();
  }

  public void outputInvocationSiteInfos() {
    for (Pair<PsiCFGMethod, PsiElement> singleInvoke : invocationSiteCollection) {
      PsiCFGMethod currentMethod = singleInvoke.getFirst();
      PsiElement currentElement = singleInvoke.getSecond();
      if (currentElement != null) {
        System.out.println(String.format("In %s method, Invoke Element %s", currentMethod.getName(), currentElement.getText()));
      }
    }
  }

  private void resolveInitialCaller(PsiCFGMethod method) {
    Set<PsiCFGMethod> calledMethods = Sets.newHashSet();
    //Stack<PsiCFGMethod> callStack = new Stack<>();
    Stack<GraphNode> nodeStack = new Stack<>();
    Stack<PsiCFGMethod> methodStack = new Stack<>();
    longestMethodStack = Lists.newArrayList();
    longestNodeStack = Lists.newArrayList();

    if ((!mCG.calleeMethodToCallerMethodReturnMap.containsKey(method)) &&
        (!mCG.callerMethodToCalleeMethodMap.containsKey(method))) {
      return;
    }
    dfsFindCallChain(nodeStack,
                     methodStack,
                     null,
                     method);

    System.out.println("Target API: " + method.getDeclaringClass().getQualifiedClassName()
                       + "." + method.getName());
    //for (PsiCFGMethod currentMethod : longestCallStack) {
    //  System.out.println(currentMethod.getDeclaringClass().getQualifiedClassName()
    //                     + "." + currentMethod.getName());
    //}
    for (int i = 0; i < longestMethodStack.size(); i++) {
      GraphNode currentNode = longestNodeStack.get(i);
      PsiCFGMethod currentMethod = longestMethodStack.get(i);
        System.out.println(currentMethod.getDeclaringClass().getQualifiedClassName()
                           + "." + currentMethod.getName() + ":"
                           + (currentNode == null ? "NULL" : currentNode.getStatements()[0].getSimpleName()));
    }
    PsiCFGMethod topMethod = longestMethodStack.get(longestMethodStack.size() - 1);
    GraphNode topNode = longestNodeStack.get(longestNodeStack.size() - 1);
    PsiElement psiRef = extractPsiElement(topNode);
    invocationSiteCollection.add(new Pair<>(topMethod, psiRef));
    System.out.println("");
  }

  private PsiElement extractPsiElement(GraphNode node) {
    Stmt invocationStatement = node.getStatements()[0];
    if (invocationStatement instanceof AssignStmt) {
      Value rOP = ((AssignStmt) invocationStatement).getROp();
      return rOP.getPsiRef();
    }
    return null;
  }

  private void dfsFindCallChain(
    Stack<GraphNode> nodeStack,
    Stack<PsiCFGMethod> methodStack,
    GraphNode node,
    PsiCFGMethod target) {


    if ((methodStack.size() > 5) || methodStack.contains(target)) {
      if (longestMethodStack.size() < methodStack.size()) {
        longestNodeStack = Lists.newArrayList(nodeStack);
        longestMethodStack = Lists.newArrayList(methodStack);
      }
      return;
    }

    methodStack.push(target);
    nodeStack.push(node);

    if (mCG.calleeMethodToCallerGraphNodeMap.containsKey(target)) {
      Collection<GraphNode> invocationSites = mCG.calleeMethodToCallerGraphNodeMap.get(target);
      for (GraphNode nextTarget: invocationSites) {
        PsiCFGMethod targetMethod = mCG.getNodesParentMethod(nextTarget);
        if (targetMethod != null) {
          dfsFindCallChain(nodeStack, methodStack, nextTarget, targetMethod);
        }
      }
    } else {
      //Top
      if (longestMethodStack.size() < methodStack.size()) {
        longestNodeStack = Lists.newArrayList(nodeStack);
        longestMethodStack = Lists.newArrayList(methodStack);
      }
    }

    //if (mCG.calleeMethodToCallerGraphNodeMap.containsKey(target)) {
    //  Collection<PsiCFGMethod> callers = mCG.calleeMethodToCallerMethodReturnMap.get(target);
    //  for (PsiCFGMethod nextTarget : callers) {
    //    dfsFindCallChain(callStack, nextTarget);
    //  }
    //}
    //else {
    //  //Top
    //  if (longestCallStack.size() < callStack.size()) {
    //    longestCallStack = Lists.newArrayList(callStack);
    //  }
    //  return;
    //}
  }

  private void getTargetMethodsListFromPsiClass(@NotNull PsiClass clazz) {
    PsiMethod[] methodsArray = clazz.getMethods();
    methodsArray = removeMethodsRequireNoPermission(methodsArray);
    PsiCFGClass cfgClazz = mScene.getPsiCFGClass(clazz);
    if (cfgClazz == null) {
      return;
    }

    for (PsiMethod currentMethod : methodsArray) {
      PsiCFGPartialMethodSignature signature =
        PsiCFGPartialMethodSignatureBuilder.buildFromPsiMethod(currentMethod);
      PsiCFGMethod cfgMethod = cfgClazz.getMethod(signature);
      if (cfgMethod != null) {
        targetMethodList.add(cfgMethod);
      }
    }
  }

  private void getTargetMethodsList() {
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(mProject);

    PsiClass locationClass = javaPsiFacade.findClass(
      LOCATION_MANAGER_CLASS_NAME, GlobalSearchScope.allScope(mProject));
    PsiClass googleMapsClass = javaPsiFacade.findClass(
      GOOGLE_MAPS_API_CLASS, GlobalSearchScope.allScope(mProject)
    );

    if (locationClass == null && googleMapsClass == null) {
      return;
    }

    if (locationClass != null) {
      getTargetMethodsListFromPsiClass(locationClass);
    }

    if (googleMapsClass != null) {
      getTargetMethodsListFromPsiClass(googleMapsClass);
    }
    
  }

  //TODO: Add annotaction check
  private PsiMethod[] removeMethodsRequireNoPermission(PsiMethod[] methodsArray) {
    return methodsArray;
  }

  private void tagTheResult() {
    InspectionManager iManager = InspectionManager.getInstance(mProject);
    for (Pair<PsiCFGMethod, PsiElement> invocationSite : invocationSiteCollection) {
      PsiElement element = invocationSite.getSecond();
      if (element instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCall = (PsiMethodCallExpression) element;
        PsiFile containingFile = methodCall.getContainingFile();
        if (containingFile != null) {
          System.out.println("FileName: " + containingFile.getName());
          ProblemsHolder holder = new ProblemsHolder(iManager, containingFile, false);

          holder.registerProblem(element, "Permission", ProblemHighlightType.ERROR, null);
        }
      }
    }

  }

}
