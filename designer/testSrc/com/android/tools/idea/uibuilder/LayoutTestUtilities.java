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
package com.android.tools.idea.uibuilder;

import android.view.View;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.fixtures.DropTargetDragEventBuilder;
import com.android.tools.idea.uibuilder.fixtures.DropTargetDropEventBuilder;
import com.android.tools.idea.uibuilder.fixtures.MouseEventBuilder;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.InteractionManager;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DropTargetContext;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.IOException;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class LayoutTestUtilities {
  public static void moveMouse(InteractionManager manager, int x1, int y1, int x2, int y2, int modifiers) {
    Object listener = manager.getListener();
    assertTrue(listener instanceof MouseMotionListener);
    MouseMotionListener mouseListener = (MouseMotionListener)listener;
    int frames = 5;
    double x = x1;
    double y = y1;
    double xSlope = (x2 - x) / frames;
    double ySlope = (y2 - y) / frames;

    JComponent layeredPane = manager.getSurface().getLayeredPane();
    for (int i = 0; i < frames + 1; i++) {
      MouseEvent event = new MouseEventBuilder((int)x, (int)y).withSource(layeredPane).withMask(modifiers).build();
      mouseListener.mouseMoved(
        event);
      x += xSlope;
      y += ySlope;
    }
  }

  public static void pressMouse(InteractionManager manager, int button, int x, int y, int modifiers) {
    Object listener = manager.getListener();
    assertTrue(listener instanceof MouseListener);
    MouseListener mouseListener = (MouseListener)listener;
    JComponent layeredPane = manager.getSurface().getLayeredPane();
    mouseListener.mousePressed(new MouseEventBuilder(x, y).withSource(layeredPane).withMask(modifiers).build());
  }

  public static void releaseMouse(InteractionManager manager, int button, int x, int y, int modifiers) {
    Object listener = manager.getListener();
    assertTrue(listener instanceof MouseListener);
    MouseListener mouseListener = (MouseListener)listener;
    JComponent layeredPane = manager.getSurface().getLayeredPane();
    mouseListener.mousePressed(new MouseEventBuilder(x, y).withSource(layeredPane).withMask(modifiers).build());
  }

  public static void clickMouse(InteractionManager manager, int button, int count, int x, int y, int modifiers) {
    JComponent layeredPane = manager.getSurface().getLayeredPane();
    for (int i = 0; i < count; i++) {
      pressMouse(manager, button, x, y, modifiers);
      releaseMouse(manager, button, x, y, modifiers);

      Object listener = manager.getListener();
      assertTrue(listener instanceof MouseListener);
      MouseListener mouseListener = (MouseListener)listener;
      MouseEvent event =
        new MouseEventBuilder(x, y).withSource(layeredPane).withButton(button).withMask(modifiers).withClickCount(i).build();
      mouseListener.mouseClicked(event);
    }
  }

  public static void dragDrop(InteractionManager manager, int x1, int y1, int x2, int y2, Transferable transferable) {
    Object listener = manager.getListener();
    assertTrue(listener instanceof DropTargetListener);
    DropTargetListener dropListener = (DropTargetListener)listener;
    int frames = 5;
    double x = x1;
    double y = y1;
    double xSlope = (x2 - x) / frames;
    double ySlope = (y2 - y) / frames;

    DropTargetContext context = createDropTargetContext();
    dropListener.dragEnter(new DropTargetDragEventBuilder(context, (int)x, (int)y, transferable).build());
    for (int i = 0; i < frames + 1; i++) {
      dropListener.dragOver(new DropTargetDragEventBuilder(context, (int)x, (int)y, transferable).build());
      x += xSlope;
      y += ySlope;
    }

    DropTargetDropEvent dropEvent = new DropTargetDropEventBuilder(context, (int)x, (int)y, transferable).build();
    dropListener.drop(dropEvent);

    verify(dropEvent, times(1)).acceptDrop(anyInt());
    verify(dropEvent, times(1)).dropComplete(true);
  }

  public static NlModel createModel(NlDesignSurface surface, AndroidFacet facet, XmlFile xmlFile) {
    NlModel model = SyncNlModel.create(surface, xmlFile.getProject(), facet, xmlFile);
    model.notifyModified(NlModel.ChangeType.UPDATE_HIERARCHY);
    return model;
  }

  public static ScreenView createScreen(NlDesignSurface surface, NlModel model, SelectionModel selectionModel) {
    return createScreen(surface, model, selectionModel, 1, 0, 0);
  }

  public static ScreenView createScreen(NlDesignSurface surface, NlModel model, SelectionModel selectionModel, double scale,
                                        @SwingCoordinate int x, @SwingCoordinate int y) {
    ScreenView screenView = mock(ScreenView.class);
    when(screenView.getConfiguration()).thenReturn(model.getConfiguration());
    when(screenView.getModel()).thenReturn(model);
    when(screenView.getScale()).thenReturn(scale);
    when(screenView.getSelectionModel()).thenReturn(selectionModel);
    when(screenView.getSize()).thenReturn(new Dimension());
    when(screenView.getSurface()).thenReturn(surface);
    when(screenView.getX()).thenReturn(x);
    when(screenView.getY()).thenReturn(y);

    when(surface.getSceneView(anyInt(), anyInt())).thenReturn(screenView);
    return screenView;
  }

  public static NlDesignSurface createSurface() {
    JComponent layeredPane = new JPanel();
    NlDesignSurface surface = mock(NlDesignSurface.class);
    when(surface.getLayeredPane()).thenReturn(layeredPane);
    return surface;
  }

  public static InteractionManager createManager(DesignSurface surface) {
    InteractionManager manager = new InteractionManager(surface);
    manager.registerListeners();
    return manager;
  }

  public static DropTargetContext createDropTargetContext() {
    return mock(DropTargetContext.class);
  }

  public static Transferable createTransferable(DataFlavor flavor, Object data) throws IOException, UnsupportedFlavorException {
    Transferable transferable = mock(Transferable.class);

    when(transferable.getTransferDataFlavors()).thenReturn(new DataFlavor[] { flavor });
    when(transferable.getTransferData(eq(flavor))).thenReturn(data);
    when(transferable.isDataFlavorSupported(eq(flavor))).thenReturn(true);

    return transferable;
  }

  public static View mockViewWithBaseline(int baseline) {
    View view = mock(View.class);
    when(view.getBaseline()).thenReturn(baseline);
    return view;
  }

  @Nullable
  public static AnAction findActionForKey(@NotNull JComponent component, int keyCode, int modifiers) {
    Shortcut shortcutToFind = new KeyboardShortcut(KeyStroke.getKeyStroke(keyCode, modifiers), null);
    java.util.List<AnAction> actions = ActionUtil.getActions(component);
    for (AnAction action : actions) {
      for (Shortcut shortcut : action.getShortcutSet().getShortcuts()) {
        if (shortcut.equals(shortcutToFind)) {
          return action;
        }
      }
    }
    return null;
  }
}
