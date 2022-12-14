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
package com.android.tools.idea.logcat;

import static com.android.tools.idea.logcat.LogcatHeaderFormat.TimestampFormat.EPOCH;
import static com.android.tools.idea.logcat.LogcatHeaderFormat.TimestampFormat.NONE;

import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.idea.logcat.LogcatHeaderFormat.TimestampFormat;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.JBFont;
import java.awt.Font;
import java.time.Instant;
import java.time.ZoneId;
import javax.swing.AbstractButton;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.LayoutStyle.ComponentPlacement;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

final class ConfigureLogcatHeaderDialog extends DialogWrapper {
  private static final LogCatMessage SAMPLE = new LogCatMessage(
    new LogCatHeader(LogLevel.INFO, 123, 456, "com.android.sample", "SampleTag", Instant.ofEpochMilli(1_517_955_388_555L)),
    "This is a sample message");

  private final ZoneId myTimeZone;

  private final AbstractButton myShowDateAndTimeCheckBox;
  private final AbstractButton myShowAsSecondsSinceEpochCheckBox;
  private final AbstractButton myShowProcessAndThreadIdsCheckBox;
  private final AbstractButton myShowPackageNameCheckBox;
  private final AbstractButton myShowTagCheckBox;
  private final JLabel mySampleLabel;

  ConfigureLogcatHeaderDialog(@NotNull Project project, @NotNull AndroidLogcatPreferences preferences, @NotNull ZoneId timeZone) {
    super(project, false, IdeModalityType.PROJECT);
    myTimeZone = timeZone;

    LogcatHeaderFormat format = preferences.LOGCAT_HEADER_FORMAT;
    myShowDateAndTimeCheckBox = createShowDateAndTimeCheckBox(format);
    myShowAsSecondsSinceEpochCheckBox = createCheckBox("Show as seconds since epoch", format.getTimestampFormat() == EPOCH, 8);
    myShowProcessAndThreadIdsCheckBox = createCheckBox("Show process and thread IDs", format.getShowProcessId(), 5);
    myShowPackageNameCheckBox = createCheckBox("Show package name", format.getShowPackageName(), 13);
    myShowTagCheckBox = createCheckBox("Show tag", format.getShowTag(), 7);
    mySampleLabel = createSampleLabel();

    init();
    setTitle(AndroidBundle.message("android.configure.logcat.header.title"));
  }

  @NotNull
  private AbstractButton createShowDateAndTimeCheckBox(LogcatHeaderFormat format) {
    AbstractButton checkBox = new JCheckBox("Show date and time", format.getTimestampFormat() != NONE);
    checkBox.setDisplayedMnemonicIndex(14);

    checkBox.addItemListener(event -> {
      myShowAsSecondsSinceEpochCheckBox.setVisible(myShowDateAndTimeCheckBox.isSelected());
      mySampleLabel.setText(formatSample());
    });

    return checkBox;
  }

  @NotNull
  private AbstractButton createCheckBox(@NotNull String text, boolean selected, int displayedMnemonicIndex) {
    AbstractButton checkBox = new JCheckBox(text, selected);

    checkBox.setDisplayedMnemonicIndex(displayedMnemonicIndex);
    checkBox.addItemListener(event -> mySampleLabel.setText(formatSample()));

    return checkBox;
  }

  @NotNull
  private JLabel createSampleLabel() {
    JLabel label = new JLabel(formatSample());
    label.setFont(JBFont.create(new Font("Monospaced", Font.PLAIN, 15)));

    return label;
  }

  @NotNull
  private String formatSample() {
    AndroidLogcatPreferences preferences = new AndroidLogcatPreferences();
    preferences.LOGCAT_HEADER_FORMAT = getFormat();
    return new AndroidLogcatFormatter(myTimeZone, preferences).formatMessage(SAMPLE);
  }

  LogcatHeaderFormat getFormat() {
    final TimestampFormat timestampFormat;
    if (myShowDateAndTimeCheckBox.isSelected()) {
      timestampFormat = myShowAsSecondsSinceEpochCheckBox.isSelected() ? TimestampFormat.EPOCH : TimestampFormat.DATETIME;
    }
    else {
      timestampFormat = NONE;
    }
    return new LogcatHeaderFormat(
      timestampFormat,
      myShowProcessAndThreadIdsCheckBox.isSelected(),
      myShowPackageNameCheckBox.isSelected(),
      myShowTagCheckBox.isSelected());
  }

  @NotNull
  AbstractButton getShowDateAndTimeCheckBox() {
    return myShowDateAndTimeCheckBox;
  }

  @NotNull
  AbstractButton getShowAsSecondsSinceEpochCheckBox() {
    return myShowAsSecondsSinceEpochCheckBox;
  }

  @NotNull
  AbstractButton getShowProcessAndThreadIdsCheckBox() {
    return myShowProcessAndThreadIdsCheckBox;
  }

  @NotNull
  AbstractButton getShowPackageNameCheckBox() {
    return myShowPackageNameCheckBox;
  }

  @NotNull
  AbstractButton getShowTagCheckBox() {
    return myShowTagCheckBox;
  }

  @NotNull
  JLabel getSampleLabel() {
    return mySampleLabel;
  }

  @NotNull
  @Override
  protected JComponent createCenterPanel() {
    JComponent panel = new JPanel(null);
    GroupLayout layout = new GroupLayout(panel);

    Group horizontalGroup = layout.createParallelGroup()
      .addComponent(myShowDateAndTimeCheckBox)
      .addGroup(layout.createSequentialGroup()
                  .addPreferredGap(myShowDateAndTimeCheckBox, myShowAsSecondsSinceEpochCheckBox, ComponentPlacement.INDENT)
                  .addComponent(myShowAsSecondsSinceEpochCheckBox))
      .addComponent(myShowProcessAndThreadIdsCheckBox)
      .addComponent(myShowPackageNameCheckBox)
      .addComponent(myShowTagCheckBox)
      .addComponent(mySampleLabel);

    Group verticalGroup = layout.createSequentialGroup()
      .addComponent(myShowDateAndTimeCheckBox)
      .addComponent(myShowAsSecondsSinceEpochCheckBox)
      .addComponent(myShowProcessAndThreadIdsCheckBox)
      .addComponent(myShowPackageNameCheckBox)
      .addComponent(myShowTagCheckBox)
      .addPreferredGap(ComponentPlacement.UNRELATED)
      .addComponent(mySampleLabel);

    layout.setAutoCreateContainerGaps(true);
    layout.setAutoCreateGaps(true);
    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    panel.setLayout(layout);
    return panel;
  }
}
