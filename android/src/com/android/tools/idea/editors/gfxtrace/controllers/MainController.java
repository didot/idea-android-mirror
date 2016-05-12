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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class MainController extends Controller {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new MainController(editor).myPanel;
  }

  @NotNull private JBPanel myPanel = new JBPanel(new BorderLayout());

  private MainController(@NotNull GfxTraceEditor editor) {
    super(editor);

    JBPanel top = new JBPanel(new GridLayout(2, 1));
    top.add(new JBLabel() {{
      setText("The GPU debugger is experimental software.");
      setIcon(AllIcons.General.BalloonWarning);
      setBackground(new JBColor(0xffee88, 0xa49152));
      setBorder(JBUI.Borders.empty(0, 10));
      setOpaque(true);
    }});
    top.add(ContextController.createUI(editor));
    myPanel.add(top, BorderLayout.NORTH);

    ThreeComponentsSplitter threePanes = new ThreeComponentsSplitter(true);
    myPanel.add(threePanes, BorderLayout.CENTER);
    threePanes.setDividerWidth(5);

    // Add the scrubber view to the top panel.
    threePanes.setFirstComponent(ScrubberController.createUI(editor));
    threePanes.setFirstSize(150);

    // Configure the image tabs.
    // we use RunnerLayoutUi to allow the user to drag the tabs out of the JBRunnerTabs
    RunnerLayoutUi layoutUi = RunnerLayoutUi.Factory.getInstance(editor.getProject()).create("gfx-runnerId", editor.getName(), editor.getSessionName(), this);
    addTab(layoutUi, FrameBufferController.createUI(editor), "Framebuffer");
    addTab(layoutUi, TexturesController.createUI(editor), "Textures");
    addTab(layoutUi, GeometryController.createUI(editor), "Geometry");

    // Now add the atom tree and buffer views to the middle pane in the main pane.
    final JBSplitter middleSplitter = new JBSplitter(false);
    middleSplitter.setMinimumSize(JBUI.size(100, 10));
    middleSplitter.setFirstComponent(AtomController.createUI(editor));
    middleSplitter.setSecondComponent(layoutUi.getComponent());
    middleSplitter.setProportion(0.3f);
    threePanes.setInnerComponent(middleSplitter);

    // Configure the bottom splitter.
    JBSplitter bottomSplitter = new JBSplitter(false);
    bottomSplitter.setMinimumSize(JBUI.size(100, 10));
    bottomSplitter.setFirstComponent(StateController.createUI(editor));
    bottomSplitter.setSecondComponent(MemoryController.createUI(editor));
    threePanes.setLastComponent(bottomSplitter);
    threePanes.setLastSize(300);

    // Make sure the bottom splitter honors minimum sizes.
    threePanes.setHonorComponentsMinimumSize(true);
    Disposer.register(this, threePanes);
  }

  private static void addTab(RunnerLayoutUi layoutUi, JComponent component, String name) {
    Content content = layoutUi.createContent(name + "-contentId", component, name, null, null);
    content.setCloseable(false);
    layoutUi.addContent(content);
  }

  @Override
  public void notifyPath(PathEvent event) {
  }

  @Override
  public void clear() {
    myPanel.removeAll();
  }
}
