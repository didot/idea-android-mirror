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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.util.PositionInFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NonNavigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.project.messages.SyncMessage.DEFAULT_GROUP;

class UnhandledIssuesReporter extends BaseSyncIssuesReporter {
  @Override
  int getSupportedIssueType() {
    //noinspection MagicConstant
    return -1; // This factory does not handle any particular issue type.
  }

  @Override
  void report(@NotNull SyncIssue syncIssue, @NotNull Module module, @Nullable VirtualFile buildFile) {
    String group = DEFAULT_GROUP;
    String text = syncIssue.getMessage();

    MessageType type = getMessageType(syncIssue);

    SyncMessage message;
    if (buildFile != null) {
      PositionInFile position = new PositionInFile(buildFile);
      message = new SyncMessage(module.getProject(), group, type, position, text);
    }
    else {
      message = new SyncMessage(group, type, NonNavigatable.INSTANCE, text);
    }

    getSyncMessages(module).report(message);
  }
}
