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
package com.android.tools.profilers.sessions;

import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.protobuf3jarjar.GeneratedMessageV3;
import com.android.tools.profilers.StudioProfilers;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A SessionArtifact is any session-related entity that should show up in the sessions panel as its own row. (e.g. A session, a memory
 * heap dump, a CPU capture, etc).
 */
public interface SessionArtifact<T extends GeneratedMessageV3> extends Updatable {

  @VisibleForTesting String CAPTURING_SUBTITLE = "Capturing...";

  /**
   * @return the {@link StudioProfilers} instance.
   */
  @NotNull
  StudioProfilers getProfilers();

  /**
   * @return the {@link Common.Session} instance that this artifact belongs to.
   */
  @NotNull
  Common.Session getSession();

  /**
   * @return the proto object that backs this artifact. Note - the mapping between a proto and its {@link SessionArtifact} is 1:1, so we
   * can use this object to check if the artifact itself is up to date.
   */
  @NotNull
  T getArtifactProto();

  /**
   * @return the {@link Common.Session} instance that this artifact belongs to.
   */
  @NotNull
  Common.SessionMetaData getSessionMetaData();

  /**
   * @return the name used for display.
   */
  @NotNull
  String getName();

  /**
   * @return the timestamp relative to the session's start time when this artifact was created/took place.
   */
  long getTimestampNs();

  /**
   * The {@link SessionArtifact} has been selected. Perform the corresponding navigation and selection change in the model.
   */
  void onSelect();

  @Override
  default void update(long elapseNs) {
  }

  @NotNull
  static String getDisplayTime(long timeMs) {
    DateFormat timeFormat = new SimpleDateFormat("MM/dd/yyyy, hh:mm a");
    return timeFormat.format(new Date(timeMs));
  }
}
