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
package com.android.tools.idea.updater.configure;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.tools.idea.npw.WizardUtils;
import com.android.tools.idea.npw.WizardUtils.ValidationResult;
import com.android.tools.idea.npw.WizardUtils.WritableCheckMode;
import com.android.tools.idea.sdk.*;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.remote.RemoteSdk;
import com.android.tools.idea.sdk.remote.UpdatablePkgInfo;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSources;
import com.android.tools.idea.stats.UsageTracker;
import com.android.tools.idea.ui.Colors;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults;
import com.android.tools.idea.welcome.wizard.ConsolidatedProgressStep;
import com.android.tools.idea.welcome.wizard.InstallComponentsPath;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.DialogWrapperHost;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardHost;
import com.android.tools.idea.wizard.dynamic.SingleStepPath;
import com.android.utils.ILogger;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.updateSettings.impl.UpdateSettingsConfigurable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.table.SelectionProvider;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.CausedFocusEvent;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Main panel for {@link SdkUpdaterConfigurable}
 */
public class SdkUpdaterConfigPanel {
  private JPanel myRootPane;
  private JTextField mySdkLocation;
  private PlatformComponentsPanel myPlatformComponentsPanel;
  private ToolComponentsPanel myToolComponentsPanel;
  private UpdateSitesPanel myUpdateSitesPanel;
  private HyperlinkLabel myLaunchStandaloneLink;
  private HyperlinkLabel myChannelLink;
  private HyperlinkLabel myEditSdkLink;
  private JBTabbedPane myTabPane;
  @SuppressWarnings("unused") private JPanel mySdkLocationPanel;
  private JBLabel mySdkLocationLabel;
  private JBLabel mySdkErrorLabel;
  private final SdkSources mySdkSources;
  private final Runnable mySourcesChangeListener = new DispatchRunnable() {
    @Override
    public void doRun() {
      refresh();
    }
  };

  private SdkState mySdkState;
  private boolean myHasPreview;
  private boolean myIncludePreview;

  private final SdkLoadedCallback myUpdater = new SdkLoadedCallback(true) {
    @Override
    public void doRun(@NotNull SdkPackages packages) {
      updateItems(packages);
    }
  };

  public interface MultiStateRow {
    void cycleState();
  }

  public SdkUpdaterConfigPanel(SdkState sdkState, final Runnable channelChangedCallback) {
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_SDK_MANAGER, UsageTracker.ACTION_SDK_MANAGER_LOADED, null, null);

    mySdkState = sdkState;
    ILogger logger = new LogWrapper(Logger.getInstance(getClass()));
    mySdkSources = mySdkState.getRemoteSdk().fetchSources(RemoteSdk.DEFAULT_EXPIRATION_PERIOD_MS, logger);
    mySdkSources.addChangeListener(mySourcesChangeListener);
    myUpdateSitesPanel.setSdkState(sdkState);
    myLaunchStandaloneLink.setHyperlinkText("Launch Standalone SDK Manager");
    myLaunchStandaloneLink.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        File sdkDirectory = IdeSdks.getAndroidSdkPath();
        assert sdkDirectory != null;

        RunAndroidSdkManagerAction.runSpecificSdkManager(null, sdkDirectory);
      }
    });
    myChannelLink.setHyperlinkText("Preview packages available! ", "Switch", " to Preview Channel to see them");
    myChannelLink.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        UpdateSettingsConfigurable settings = new UpdateSettingsConfigurable(false);
        ShowSettingsUtil.getInstance().editConfigurable(getComponent(), settings);
        channelChangedCallback.run();
      }
    });
    myEditSdkLink.setHyperlinkText("Edit");
    myEditSdkLink.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        DynamicWizard wizard = new SdkSetupWizard();

        wizard.init();
        wizard.show();
      }
    });
    mySdkLocation.setEditable(false);
  }

  private final class SdkSetupWizard extends DynamicWizard {
    private SdkSetupWizard() {
      super(null, null, "SDK Setup", new DialogWrapperHost(null));
    }

    @Override
    public void init() {
      DownloadingComponentsStep step = new DownloadingComponentsStep(myHost.getDisposable(), myHost);

      String sdkDirectoryPath = mySdkLocation.getText();
      File sdkDirectory;

      if (Strings.isNullOrEmpty(sdkDirectoryPath)) {
        sdkDirectory = FirstRunWizardDefaults.getInitialSdkLocation(FirstRunWizardMode.MISSING_SDK);
      }
      else {
        sdkDirectory = new File(sdkDirectoryPath);
      }

      Multimap<PkgType, RemotePkgInfo> packages = mySdkState.getPackages().getRemotePkgInfos();
      InstallComponentsPath path = new InstallComponentsPath(step, FirstRunWizardMode.MISSING_SDK, sdkDirectory, packages, false);

      step.setInstallComponentsPath(path);

      addPath(path);
      addPath(new SingleStepPath(step));

      super.init();
    }

    @Override
    public void performFinishingActions() {
      File sdkLocation = IdeSdks.getAndroidSdkPath();

      if (sdkLocation == null) {
        return;
      }

      String stateSdkLocationPath = myState.get(WizardConstants.KEY_SDK_INSTALL_LOCATION);
      assert stateSdkLocationPath != null;

      File stateSdkLocation = new File(stateSdkLocationPath);

      if (!FileUtil.filesEqual(sdkLocation, stateSdkLocation)) {
        setAndroidSdkLocation(stateSdkLocation);
        sdkLocation = stateSdkLocation;
      }

      mySdkState = SdkState.getInstance(AndroidSdkData.getSdkData(sdkLocation));
      setAndroidSdkLocationText(sdkLocation.getAbsolutePath());
    }

    private void setAndroidSdkLocationText(final String text) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          mySdkLocation.setText(text);
          refresh();
        }
      });
    }

    @NotNull
    @Override
    protected String getProgressTitle() {
      return "Setting up SDK...";
    }

    @Override
    protected String getWizardActionDescription() {
      return "Setting up SDK...";
    }
  }

  private static final class DownloadingComponentsStep extends ConsolidatedProgressStep {
    private InstallComponentsPath myInstallComponentsPath;

    private DownloadingComponentsStep(@NotNull Disposable disposable, @NotNull DynamicWizardHost host) {
      super(disposable, host);
    }

    private void setInstallComponentsPath(InstallComponentsPath installComponentsPath) {
      setPaths(Collections.singletonList(installComponentsPath));
      myInstallComponentsPath = installComponentsPath;
    }

    @Override
    public boolean isStepVisible() {
      return myInstallComponentsPath.shouldDownloadingComponentsStepBeShown();
    }
  }

  private static void setAndroidSdkLocation(final File sdkLocation) {
    Runnable writeAction = new Runnable() {
      @Override
      public void run() {
        IdeSdks.setAndroidSdkPath(sdkLocation, null);
      }
    };

    invokeWriteActionAndWait(writeAction, ApplicationManager.getApplication().getAnyModalityState());
  }

  private static void invokeWriteActionAndWait(final Runnable writeAction, ModalityState state) {
    final Application application = ApplicationManager.getApplication();

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        application.runWriteAction(writeAction);
      }
    };

    application.invokeAndWait(runnable, state);
  }

  public void setIncludePreview(boolean includePreview) {
    myIncludePreview = includePreview;
    myChannelLink.setVisible(myHasPreview && !myIncludePreview);
    myPlatformComponentsPanel.setIncludePreview(includePreview);
    myToolComponentsPanel.setIncludePreview(includePreview);
    loadPackages(mySdkState.getPackages());
  }

  public JComponent getComponent() {
    return myRootPane;
  }

  public boolean isModified() {
    return myPlatformComponentsPanel.isModified() || myToolComponentsPanel.isModified() || myUpdateSitesPanel.isModified();
  }

  static void setTreeTableProperties(final TreeTableView tt, UpdaterTreeNode.Renderer renderer, final ChangeListener listener) {
    tt.setTreeCellRenderer(renderer);
    new CheckboxClickListener(tt, renderer).installOn(tt);
    TreeUtil.installActions(tt.getTree());

    tt.getTree().setToggleClickCount(0);
    tt.getTree().setShowsRootHandles(true);

    setTableProperties(tt, listener);
  }

  static void setTableProperties(@NotNull final JTable table, @Nullable final ChangeListener listener) {
    assert table instanceof SelectionProvider;
    ActionMap am = table.getActionMap();
    final CycleAction forwardAction = new CycleAction(false);
    final CycleAction backwardAction = new CycleAction(true);
    am.put("selectPreviousColumnCell", backwardAction);
    am.put("selectNextColumnCell", forwardAction);

    table.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        if (e.getKeyChar() == KeyEvent.VK_ENTER || e.getKeyChar() == KeyEvent.VK_SPACE) {
          @SuppressWarnings("unchecked") Iterable<MultiStateRow> selection =
            (Iterable<MultiStateRow>)((SelectionProvider)table).getSelection();

          for (MultiStateRow node : selection) {
            node.cycleState();
            table.repaint();
            if (listener != null) {
              listener.stateChanged(new ChangeEvent(node));
            }
          }
        }
      }
    });
    table.addFocusListener(new FocusListener() {
      @Override
      public void focusLost(FocusEvent e) {
        if (e.getOppositeComponent() != null) {
          table.getSelectionModel().clearSelection();
        }
      }

      @Override
      public void focusGained(FocusEvent e) {
        JTable table = (JTable)e.getSource();
        if (table.getSelectionModel().getMinSelectionIndex() != -1) {
          return;
        }
        if (e instanceof CausedFocusEvent && ((CausedFocusEvent)e).getCause() == CausedFocusEvent.Cause.TRAVERSAL_BACKWARD) {
          backwardAction.doAction(table);
        }
        else {
          forwardAction.doAction(table);
        }
      }
    });
  }

  protected static void resizeColumnsToFit(JTable table) {
    TableColumnModel columnModel = table.getColumnModel();
    for (int column = 1; column < table.getColumnCount(); column++) {
      int width = 50;
      for (int row = 0; row < table.getRowCount(); row++) {
        TableCellRenderer renderer = table.getCellRenderer(row, column);
        Component comp = table.prepareRenderer(renderer, row, column);
        width = Math.max(comp.getPreferredSize().width + 1, width);
      }
      columnModel.getColumn(column).setPreferredWidth(width);
    }
  }

  public void refresh() {
    validate();

    myPlatformComponentsPanel.startLoading();
    myToolComponentsPanel.startLoading();
    myUpdateSitesPanel.startLoading();

    SdkLoadedCallback remoteComplete = new SdkLoadedCallback(true) {
      @Override
      public void doRun(@NotNull SdkPackages packages) {
        updateItems(packages);
        myPlatformComponentsPanel.finishLoading();
        myToolComponentsPanel.finishLoading();
        myUpdateSitesPanel.finishLoading();
      }
    };
    mySdkState.loadAsync(SdkState.DEFAULT_EXPIRATION_PERIOD_MS, false, myUpdater, remoteComplete, null, true);
  }

  private void validate() {
    AndroidSdkData data = mySdkState.getSdkData();
    String sdkDirectoryPath = data == null ? null : data.getLocation().getAbsolutePath();

    ValidationResult result =
      WizardUtils.validateLocation(sdkDirectoryPath, "Android SDK location", false, WritableCheckMode.NOT_WRITABLE_IS_WARNING);

    switch (result.getStatus()) {
      case OK:
        mySdkLocationLabel.setForeground(JBColor.foreground());
        mySdkErrorLabel.setVisible(false);
        myPlatformComponentsPanel.setEnabled(true);
        myTabPane.setEnabled(true);

        break;
      case WARN:
        mySdkLocationLabel.setForeground(Colors.WARNING);

        mySdkErrorLabel.setForeground(Colors.WARNING);
        mySdkErrorLabel.setIcon(AllIcons.General.BalloonWarning);
        mySdkErrorLabel.setText(result.getFormattedMessage());
        mySdkErrorLabel.setVisible(true);

        myPlatformComponentsPanel.setEnabled(false);
        myTabPane.setEnabled(false);

        break;
      case ERROR:
        mySdkLocationLabel.setForeground(Colors.ERROR);

        mySdkErrorLabel.setForeground(Colors.ERROR);
        mySdkErrorLabel.setIcon(AllIcons.General.BalloonError);
        mySdkErrorLabel.setText(result.getFormattedMessage());
        mySdkErrorLabel.setVisible(true);

        myPlatformComponentsPanel.setEnabled(false);
        myTabPane.setEnabled(false);

        break;
    }
  }

  private void loadPackages(SdkPackages packages) {
    Multimap<AndroidVersion, UpdatablePkgInfo> platformPackages = TreeMultimap.create();
    Set<UpdatablePkgInfo> buildToolsPackages = Sets.newTreeSet();
    Set<UpdatablePkgInfo> toolsPackages = Sets.newTreeSet();
    for (UpdatablePkgInfo info : packages.getConsolidatedPkgs().values()) {
      IPkgDesc desc = info.getPkgDesc(myIncludePreview);
      if (desc == null) {
        // We're not looking for previews, and this only has a preview available.
        continue;
      }
      AndroidVersion version = desc.getAndroidVersion();
      PkgType type = info.getPkgDesc(myIncludePreview).getType();
      if (type == PkgType.PKG_SAMPLE) {
        continue;
      }
      if (version != null && type != PkgType.PKG_DOC) {
        platformPackages.put(version, info);
      }
      else if (type == PkgType.PKG_BUILD_TOOLS) {
        buildToolsPackages.add(info);
      }
      else {
        toolsPackages.add(info);
      }
      if (info.hasPreview()) {
        myHasPreview = true;
      }
    }
    myChannelLink.setVisible(myHasPreview && !myIncludePreview);
    myPlatformComponentsPanel.setPackages(platformPackages);
    myToolComponentsPanel.setPackages(toolsPackages, buildToolsPackages);
  }

  private void updateItems(SdkPackages packages) {
    myPlatformComponentsPanel.clearState();
    myToolComponentsPanel.clearState();
    loadPackages(packages);
  }

  public Collection<NodeStateHolder> getStates() {
    List<NodeStateHolder> result = Lists.newArrayList();
    result.addAll(myPlatformComponentsPanel.myStates);
    result.addAll(myToolComponentsPanel.myStates);
    return result;
  }

  public void reset() {
    refresh();
    File path = IdeSdks.getAndroidSdkPath();
    if (path != null) {
      mySdkLocation.setText(path.getPath());
    }
    myPlatformComponentsPanel.reset();
    myToolComponentsPanel.reset();
    myUpdateSitesPanel.reset();
  }

  public void disposeUIResources() {
    mySdkSources.removeChangeListener(mySourcesChangeListener);
  }

  public void saveSources() {
    myUpdateSitesPanel.save();
  }

  private static class CycleAction extends AbstractAction {
    private final boolean myBackward;

    CycleAction(boolean backward) {
      myBackward = backward;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
      doAction((JTable)evt.getSource());
    }

    public void doAction(JTable table) {
      KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      ListSelectionModel selectionModel = table.getSelectionModel();
      int row = myBackward ? selectionModel.getMinSelectionIndex() : selectionModel.getMaxSelectionIndex();

      if (row == -1) {
        if (myBackward) {
          row = table.getRowCount();
        }
      }
      row += myBackward ? -1 : 1;
      if (row < 0) {
        manager.focusPreviousComponent(table);
      }
      else if (row >= table.getRowCount()) {
        manager.focusNextComponent(table);
      }
      else {
        selectionModel.setSelectionInterval(row, row);
        table.setColumnSelectionInterval(1, 1);
        table.scrollRectToVisible(table.getCellRect(row, 1, true));
      }
      table.repaint();
    }
  }
}
