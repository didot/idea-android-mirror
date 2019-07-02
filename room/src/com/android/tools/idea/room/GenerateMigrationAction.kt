/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.room

import com.android.tools.idea.room.bundle.SchemaBundle
import com.android.tools.idea.room.generators.JavaMigrationClassGenerator
import com.android.tools.idea.room.update.DatabaseUpdate
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiManager

class GenerateRoomMigrationAction : AnAction("Generate a Room migration") {

  override fun actionPerformed(e: AnActionEvent) {
    Messages.showInfoMessage(e.project, "Generating migration", "Room migration generator")
    val project = e.project ?: return
    val fileArray = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
    if (fileArray.size == 2 && fileArray[0] != null && fileArray[1] != null) {
      val module = ModuleUtilCore.findModuleForFile(fileArray[0], project) ?: return
      val directory = PsiManager.getInstance(project).findDirectory(module.rootManager.sourceRoots[0]) ?: return
      val oldSchema = SchemaBundle.deserialize(fileArray[0].inputStream)
      val newSchema = SchemaBundle.deserialize(fileArray[1].inputStream)
      WriteCommandAction.runWriteCommandAction(project) {
        JavaMigrationClassGenerator.createMigrationClass(project, directory, DatabaseUpdate(oldSchema.database, newSchema.database))
      }
    }
  }
}