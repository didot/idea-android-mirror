@file:JvmName("AndroidRefactoringUtil")

package org.jetbrains.android.refactoring

import com.android.tools.idea.actions.ExportProjectZip
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMigration
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.migration.MigrationUtil
import org.jetbrains.android.dom.resources.Style
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.android.util.ErrorReporter
import javax.swing.JCheckBox


internal fun getParentStyle(style: Style): StyleRefData? {
  val parentStyleRefValue = style.parentStyle.value

  return if (parentStyleRefValue != null) {
    parentStyleRefValue.resourceName?.let { StyleRefData(it, parentStyleRefValue.`package`) }
  } else {
    style.name.stringValue
      ?.takeIf { it.indexOf('.') > 0 }
      ?.let { StyleRefData(it.substringBeforeLast('.'), null) }
  }
}

internal fun computeAttributeMap(
  style: Style,
  errorReporter: ErrorReporter,
  errorReportTitle: String
): Map<AndroidAttributeInfo, String>? {
  val attributeValues = mutableMapOf<AndroidAttributeInfo, String>()

  for (item in style.items) {
    val attributeName = item.name.stringValue
    val attributeValue = item.stringValue

    if (attributeName == null || attributeName.isEmpty() || attributeValue == null) {
      continue
    }
    val localName = attributeName.substringAfterLast(':')
    val nsPrefix = attributeName.substringBefore(':', missingDelimiterValue = "")

    if (nsPrefix.isNotEmpty()) {
      if (AndroidUtils.SYSTEM_RESOURCE_PACKAGE != nsPrefix) {
        errorReporter.report(
          RefactoringBundle.getCannotRefactorMessage("Unknown XML attribute prefix '$nsPrefix:'"),
          errorReportTitle
        )
        return null
      }
    }
    else {
      errorReporter.report(
        RefactoringBundle.getCannotRefactorMessage("The style contains attribute without 'android' prefix."),
        errorReportTitle
      )
      return null
    }
    attributeValues[AndroidAttributeInfo(localName, nsPrefix)] = attributeValue
  }
  return attributeValues
}

/**
 * Public version of the same method from [MigrationUtil].
 */
@JvmOverloads
fun findOrCreateClass(
  project: Project,
  migration: PsiMigration,
  qName: String,
  scope: GlobalSearchScope = GlobalSearchScope.allScope(project)
): PsiClass {
  return JavaPsiFacade.getInstance(project).findClass(qName, scope) ?: runWriteAction { migration.createClass(qName) }
}

/**
 * Public version of the same method from [MigrationUtil].
 */
fun findOrCreatePackage(project: Project, migration: PsiMigration, qName: String): PsiPackage {
  return JavaPsiFacade.getInstance(project).findPackage(qName) ?: runWriteAction { migration.createPackage(qName) }
}

private const val RESULT_MIGRATE_WITH_BACKUP = 0
private const val RESULT_MIGRATE = 1
private const val RESULT_CANCEL = 2

private const val ACTION_WARNING_TEXT = """
Before proceeding, we recommend that you make a backup of your project.

Depending on your project dependencies, you might need to manually fix
some errors after the refactoring in order to successfully compile your project.

Do you want to proceed with the migration?
"""

/**
 * Shows a dialog offering to create a zip file with the project contents.
 *
 * Depending on the user action choice it then may run refactoring, optionally invoking [ExportProjectZip] first.
 */
fun offerToCreateBackupAndRun(project: Project, title: String, runRefactoring: () -> Unit) {
  val okCancelResult = Messages.showCheckboxMessageDialog(
    ACTION_WARNING_TEXT.trim(),
    title,
    arrayOf("Migrate", "Cancel"),
    "Backup project as Zip file",
    true,
    0, 0,
    Messages.getWarningIcon()
  ) { index: Int, checkbox: JCheckBox ->
    when {
      index != 0 -> RESULT_CANCEL
      checkbox.isSelected -> RESULT_MIGRATE_WITH_BACKUP
      else -> RESULT_MIGRATE
    }
  }

  when (okCancelResult) {
    RESULT_CANCEL -> return
    RESULT_MIGRATE_WITH_BACKUP -> {
      val exportZip = ExportProjectZip()
      ActionUtil.invokeAction(
        exportZip,
        SimpleDataContext.getProjectContext(project),
        title,
        null,
        Runnable(runRefactoring)
      )
    }
    else -> runRefactoring()
  }
}

/**
 * Requests a sync to be performed after [BaseRefactoringProcessor.performRefactoring] is done but before
 * [BaseRefactoringProcessor.performPsiSpoilingRefactoring] runs and before the refactoring action is finished.
 *
 * This means that organizing imports and shortening references is done after the sync. We rely on the fact that
 * [BaseRefactoringProcessor.doRefactoring] drains the dumb mode tasks queue after running [BaseRefactoringProcessor.performRefactoring].
 */
fun syncBeforeFinishingRefactoring(project: Project, trigger: GradleSyncStats.Trigger) {
  assert(ApplicationManager.getApplication().isDispatchThread)

  GradleSyncInvoker.getInstance().requestProjectSync(
    project,
    GradleSyncInvoker.Request(trigger)
  )
}
