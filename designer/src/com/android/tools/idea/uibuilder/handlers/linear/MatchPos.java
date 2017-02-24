/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.linear;

import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;

/**
 * A possible match position
 */
class MatchPos {
  /**
   * The dp distance
   */
  @AndroidDpCoordinate private final int myDistance;
  /**
   * The position among siblings
   */
  private final int myPosition;

  public MatchPos(@AndroidDpCoordinate int distance, int position) {
    myDistance = distance;
    myPosition = position;
  }

  @AndroidDpCoordinate
  int getDistance() {
    return myDistance;
  }

  int getPosition() {
    return myPosition;
  }
}
