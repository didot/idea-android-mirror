/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.compose.formatting


import com.android.tools.compose.ComposeLibraryNamespace
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings


/**
 * Test for [ComposePostFormatProcessor].
 */
class ComposePostFormatProcessorTest : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    StudioFlags.COMPOSE_EDITOR_SUPPORT.override(true)
    myFixture.stubComposableAnnotation()
    myFixture.addFileToProject(
      "src/${ComposeLibraryNamespace.ANDROIDX_COMPOSE.packageName.replace(".", "/")}/Modifier.kt",
      // language=kotlin
      """
    package ${ComposeLibraryNamespace.ANDROIDX_COMPOSE.packageName}

    interface Modifier {
      fun adjust():Modifier
      companion object : Modifier {
        fun adjust():Modifier {}
      }
    }

    fun Modifier.extentionFunction():Modifier { return this}
    """.trimIndent()
    )

    val settings = KotlinCodeStyleSettings.getInstance(project)
    settings.CONTINUATION_INDENT_FOR_CHAINED_CALLS = false
  }

  fun testWrapModifierChain() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen() {
          val m = Modifier.adjust().adjust()
      }
      """.trimIndent()
    )

    WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
      CodeStyleManager.getInstance(project).reformatText(myFixture.file, listOf(myFixture.file.textRange))
    }

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen() {
          val m = Modifier
              .adjust()
              .adjust()
      }
      """.trimIndent()
    )
  }

  fun testDontWrapShortModifierChain() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen() {
          val m = Modifier.adjust()
      }
      """.trimIndent()
    )

    WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
      CodeStyleManager.getInstance(project).reformatText(myFixture.file, listOf(myFixture.file.textRange))
    }

    myFixture.checkResult(
      """
      package com.example

      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun HomeScreen() {
          val m = Modifier.adjust()
      }
      """.trimIndent()
    )
  }

}