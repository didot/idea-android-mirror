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
package com.android.tools.idea.welcome;

import com.android.tools.idea.wizard.DynamicWizard;
import com.android.tools.idea.wizard.DynamicWizardHost;
import com.android.tools.idea.wizard.WizardConstants;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Atomics;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.OptionAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hosts dynamic wizards in the welcome frame.
 */
public class WelcomeScreenHost extends JPanel implements WelcomeScreen, DynamicWizardHost {
  private static final Insets BUTTON_MARGINS = new Insets(2, 16, 2, 16);
  @NotNull private final FirstRunWizardMode myMode;

  // Action References. myCancelAction and myHelpAction are inherited
  private Action myPreviousAction = new PreviousAction();
  private Action myNextAction = new NextAction();
  private FinishAction myFinishAction = new FinishAction();

  private FirstRunWizard myWizard;
  private JFrame myFrame;
  private String myTitle;
  private Dimension myPreferredWindowSize = new Dimension(800, 600);
  private Map<Action, JButton> myActionToButtonMap = Maps.newHashMap();
  private AtomicReference<ProgressIndicator> myCurrentProgressIndicator = Atomics.newReference();

  public WelcomeScreenHost(@NotNull FirstRunWizardMode mode) {
    super(new BorderLayout());
    myMode = mode;
    add(createSouthPanel(), BorderLayout.SOUTH);
  }

  private static void setMargin(JButton button) {
    // Aqua LnF does a good job of setting proper margin between buttons. Setting them specifically causes them be 'square' style instead of
    // 'rounded', which is expected by apple users.
    if (!SystemInfo.isMac && BUTTON_MARGINS != null) {
      button.setMargin(BUTTON_MARGINS);
    }
  }

  @Override
  public JComponent getWelcomePanel() {
    if (myWizard == null) {
      setupWizard();
    }
    assert myWizard != null;
    return this;
  }

  private void setupWizard() {
    DynamicWizard wizard = new FirstRunWizard(this, myMode);
    wizard.init();
    add(wizard.getContentPane(), BorderLayout.CENTER);
  }

  @Override
  public void setupFrame(JFrame frame) {
    myFrame = frame;
    if (myTitle != null) {
      frame.setTitle(myTitle);
    }
    frame.setSize(myPreferredWindowSize);
    Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
    int x = (size.width - myPreferredWindowSize.width) / 2;
    int y = (size.height - myPreferredWindowSize.height) / 2;
    frame.setLocation(x, y);
    JButton defaultButton = myActionToButtonMap.get(myFinishAction);
    if (!defaultButton.isEnabled()) {
      defaultButton = myActionToButtonMap.get(myNextAction);
    }
    setDefaultButton(defaultButton);
  }

  @Override
  public void dispose() {
    // Nothing
  }

  @NotNull
  @Override
  public Disposable getDisposable() {
    return this;
  }

  @Override
  public void init(@NotNull DynamicWizard wizard) {
    myWizard = (FirstRunWizard)wizard;
  }

  @Override
  public boolean showAndGet() {
    return false;
  }

  @Override
  public void close(boolean canceled) {
    if (canceled) {
      myFrame.setVisible(false);
      myFrame.dispose();
    }
    else {
      setDefaultButton(null);
      NewWelcomeScreen welcomeScreen = new NewWelcomeScreen();
      Disposer.register(getDisposable(), welcomeScreen);
      myFrame.setContentPane(welcomeScreen.getWelcomePanel());
      welcomeScreen.setupFrame(myFrame);
    }
  }

  @Override
  public void shakeWindow() {
    // Do nothing
  }

  @Override
  public void updateButtons(boolean canGoPrev, boolean canGoNext, boolean canFinish) {
    setButtonEnabled(myPreviousAction, canGoPrev);
    setButtonEnabled(myNextAction, canGoNext);
    setButtonEnabled(myFinishAction, canFinish);
    if (myFrame != null) {
      setDefaultButton(myActionToButtonMap.get(canFinish ? myFinishAction : myNextAction));
    }
  }

  private void setButtonEnabled(Action action, boolean enabled) {
    JButton button = myActionToButtonMap.get(action);
    if (button != null) {
      button.setEnabled(enabled);
    }
  }

  @Override
  public void setTitle(String title) {
    myTitle = title;
    if (myFrame != null) {
      myFrame.setTitle(title);
    }
  }

  @Override
  public void setIcon(@Nullable Icon icon) {

  }

  @Override
  public void runSensitiveOperation(@NotNull ProgressIndicator progressIndicator,
                                    boolean cancellable,
                                    @NotNull final Runnable operation) {
    final Application application = ApplicationManager.getApplication();
    application.assertIsDispatchThread();
    if (!myCurrentProgressIndicator.compareAndSet(null, progressIndicator)) {
      throw new IllegalStateException("Submitting an operation while another is in progress.");
    }
    final JRootPane rootPane = myFrame.getRootPane();
    final JButton defaultButton = rootPane.getDefaultButton();
    rootPane.setDefaultButton(null);
    updateButtons(false, false, true);
    myFinishAction.putValue(Action.NAME, IdeBundle.message("button.cancel"));
    final WindowListener removed = removeCloseListener();
    Task.Backgroundable task = new LongRunningOperationWrapper(operation, cancellable, defaultButton, removed);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, progressIndicator);
  }

  /**
   * Remove the listener that causes application to quit if the user closes the welcome frame.
   *
   * I was unable to find proper API in IntelliJ to do this without forking quite a few classes.
   */
  @Nullable
  private WindowListener removeCloseListener() {
    WindowListener[] listeners = myFrame.getListeners(WindowListener.class);
    for (WindowListener listener : listeners) {
      // The listener in question is an anonymous class nested in WelcomeFrame
      if (listener.getClass().getName().startsWith(WelcomeFrame.class.getName())) {
        myFrame.removeWindowListener(listener);
        return listener;
      }
    }
    return null;
  }

  /**
   * Creates panel located at the south of the content pane. By default that
   * panel contains dialog's buttons. This default implementation uses <code>createActions()</code>
   * and <code>createJButtonForAction(Action)</code> methods to construct the panel.
   *
   * @return south panel
   */
  @NotNull
  private JComponent createSouthPanel() {
    Action[] actions = createActions();
    List<JButton> buttons = new ArrayList<JButton>();

    JPanel panel = new JPanel(new BorderLayout());
    final JPanel lrButtonsPanel = new JPanel(new GridBagLayout());
    final Insets insets = SystemInfo.isMacOSLeopard ? new Insets(0, 0, 0, 0) : new Insets(8, 0, 0, 0);

    if (actions.length > 0) {
      int gridX = 0;
      lrButtonsPanel.add(Box.createHorizontalGlue(),    // left strut
                         new GridBagConstraints(gridX++, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, insets, 0,
                                                0));
      if (actions.length > 0) {
        JPanel buttonsPanel = createButtons(actions, buttons);
        lrButtonsPanel.add(buttonsPanel,
                           new GridBagConstraints(gridX, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, insets, 0, 0));
      }
    }

    panel.add(lrButtonsPanel, BorderLayout.CENTER);

    panel.setBorder(IdeBorderFactory.createEmptyBorder(WizardConstants.STUDIO_WIZARD_INSETS));

    return panel;
  }

  private Action[] createActions() {
    return new Action[]{myPreviousAction, myNextAction, myFinishAction};
  }

  @NotNull
  private JPanel createButtons(@NotNull Action[] actions, @NotNull List<JButton> buttons) {
    if (!UISettings.getShadowInstance().ALLOW_MERGE_BUTTONS) {
      final List<Action> actionList = new ArrayList<Action>();
      for (Action action : actions) {
        actionList.add(action);
        if (action instanceof OptionAction) {
          final Action[] options = ((OptionAction)action).getOptions();
          actionList.addAll(Arrays.asList(options));
        }
      }
      if (actionList.size() != actions.length) {
        actions = actionList.toArray(actionList.toArray(new Action[actionList.size()]));
      }
    }

    JPanel buttonsPanel = new JPanel(new GridLayout(1, actions.length, SystemInfo.isMacOSLeopard ? 0 : 5, 0));
    for (final Action action : actions) {
      JButton button = createJButtonForAction(action);
      final Object value = action.getValue(Action.MNEMONIC_KEY);
      if (value instanceof Integer) {
        final int mnemonic = ((Integer)value).intValue();
        button.setMnemonic(mnemonic);
      }
      buttons.add(button);
      buttonsPanel.add(button);
    }
    return buttonsPanel;
  }

  /**
   * Creates <code>JButton</code> for the specified action. If the button has not <code>null</code>
   * value for <code>DialogWrapper.DEFAULT_ACTION</code> key then the created button will be the
   * default one for the dialog.
   *
   * @param action action for the button
   * @return button with action specified
   * @see com.intellij.openapi.ui.DialogWrapper#DEFAULT_ACTION
   */
  protected JButton createJButtonForAction(Action action) {
    JButton button = new JButton(action);
    String text = button.getText();

    if (SystemInfo.isMac) {
      button.putClientProperty("JButton.buttonType", "text");
    }

    if (text != null) {
      int mnemonic = 0;
      StringBuilder plainText = new StringBuilder();
      for (int i = 0; i < text.length(); i++) {
        char ch = text.charAt(i);
        if (ch == '_' || ch == '&') {
          if (i >= text.length()) {
            break;
          }
          ch = text.charAt(i);
          if (ch != '_' && ch != '&') {
            // Mnemonic is case insensitive.
            int vk = ch;
            if (vk >= 'a' && vk <= 'z') {
              vk -= 'a' - 'A';
            }
            mnemonic = vk;
          }
        }
        plainText.append(ch);
      }
      button.setText(plainText.toString());
      button.setMnemonic(mnemonic);
      setMargin(button);
    }
    myActionToButtonMap.put(action, button);
    return button;
  }

  @Override
  public void setPreferredWindowSize(Dimension preferredWindowSize) {
    myPreferredWindowSize = preferredWindowSize;
    if (myFrame != null) {
      myFrame.setSize(preferredWindowSize);
    }
  }

  private void setDefaultButton(@Nullable JButton button) {
    JRootPane rootPane = getRootPane();
    if (rootPane != null) {
      rootPane.setDefaultButton(button);
    }
  }

  protected class NextAction extends AbstractAction {
    protected NextAction() {
      putValue(NAME, IdeBundle.message("button.wizard.next"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myWizard.doNextAction();
    }
  }

  protected class PreviousAction extends AbstractAction {
    protected PreviousAction() {
      putValue(NAME, IdeBundle.message("button.wizard.previous"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myWizard.doPreviousAction();
    }
  }

  protected class FinishAction extends AbstractAction {
    protected FinishAction() {
      putValue(NAME, IdeBundle.message("button.finish"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      ProgressIndicator indicator = myCurrentProgressIndicator.get();
      if (indicator == null) {
        myWizard.doFinishAction();
      }
      else {
        indicator.cancel();
        setButtonEnabled(this, false);
      }
    }
  }

  private class LongRunningOperationWrapper extends Task.Backgroundable {
    private final Runnable myOperation;
    private final JButton myDefaultButton;
    private final WindowListener myRemoved;

    public LongRunningOperationWrapper(Runnable operation,
                                       boolean cancellable,
                                       JButton defaultButton,
                                       @Nullable WindowListener suspendedListener) {
      super(null, WelcomeScreenHost.this.myWizard.getWizardActionDescription(), cancellable);
      myOperation = operation;
      myDefaultButton = defaultButton;
      myRemoved = suspendedListener;
    }

    @Override
    public void onSuccess() {
      myCurrentProgressIndicator.set(null);
      myFinishAction.putValue(Action.NAME, IdeBundle.message("button.finish"));
      updateButtons(false, false, true);
      myFrame.getRootPane().setDefaultButton(myDefaultButton);
      if (myRemoved != null) {
        myFrame.addWindowListener(myRemoved);
      }
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      myOperation.run();
    }

    @Override
    public void onCancel() {
      onSuccess();
    }
  }
}
