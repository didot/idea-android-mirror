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
package com.android.tools.swing.ui;

import javax.swing.*;

/**
 * Class that implements a label that supports action listeners
 */
public class ClickableLabel extends JButton {
  public ClickableLabel(String text, Icon icon, int horizontalAlignment) {
    super(text, icon);

    setFocusPainted(false);
    setBorderPainted(false);
    setContentAreaFilled(false);
    setBorder(BorderFactory.createEmptyBorder());
    setHorizontalAlignment(horizontalAlignment);
  }

  public ClickableLabel(String text) {
    this(text, null, SwingConstants.LEADING);
  }

  public ClickableLabel() {
    this(null);
  }
}
