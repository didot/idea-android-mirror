/*
 * Copyright (C) 2022 The Android Open Source Project
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
@file:JvmName("GradlePluginUpgrade")
package com.android.tools.idea.gradle.project.upgrade

import com.android.SdkConstants
import com.android.SdkConstants.GRADLE_PATH_SEPARATOR
import com.android.annotations.concurrency.Slow
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo.ARTIFACT_ID
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo.GROUP_ID
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.sync.hyperlink.SearchInBuildFilesHyperlink
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages
import com.android.tools.idea.gradle.project.sync.setup.post.TimeBasedReminder
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.AFTER_MAXIMUM
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.BEFORE_MINIMUM
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.COMPATIBLE
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.DIFFERENT_PREVIEW
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.FORCE
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.NO_UPGRADE
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.RECOMMEND
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.android.tools.idea.project.messages.MessageType.ERROR
import com.android.tools.idea.project.messages.SyncMessage
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState.NON_MODAL
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.util.SystemProperties
import com.jetbrains.rd.util.first
import org.jetbrains.android.util.AndroidBundle
import java.util.concurrent.TimeUnit

private val LOG = Logger.getInstance(LOG_CATEGORY)

// **************************************************************************
// ** Recommended upgrades
// **************************************************************************

class RecommendedUpgradeReminder(
  project: Project
) : TimeBasedReminder(project, "recommended.upgrade", TimeUnit.DAYS.toMillis(1)) {
  var doNotAskForVersion: String?
    get() =  PropertiesComponent.getInstance(project).getValue("$settingsPropertyRoot.do.not.ask.for.version")
    set(value) = PropertiesComponent.getInstance(project).setValue("$settingsPropertyRoot.do.not.ask.for.version", value)

  @Slow
  override fun shouldAsk(currentTime: Long): Boolean {
    val pluginInfo = project.findPluginInfo() ?: return false
    val gradleVersion = pluginInfo.pluginVersion ?: return false
    return doNotAskForVersion != gradleVersion.toString() && super.shouldAsk(currentTime)
  }
}

/**
 * Checks to see if we should be recommending an upgrade of the Android Gradle Plugin.
 *
 * Returns true if we should recommend an upgrade, false otherwise. We recommend an upgrade if any of the following conditions are met:
 * 1 - If the user has never been shown the upgrade (for that version) and the conditions in [shouldRecommendUpgrade] return true.
 * 2 - If the user picked "Remind me tomorrow" and a day has passed.
 *
 * [current] defaults to the value that is obtained from the [project], if it can't be found, false is returned.
 */
@Slow
fun shouldRecommendPluginUpgrade(project: Project): Boolean {
  // If we don't know the current plugin version then we don't upgrade.
  val current = project.findPluginInfo()?.pluginVersion ?: return false
  val latestKnown = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
  val published = IdeGoogleMavenRepository.getVersions("com.android.tools.build", "gradle")
  return shouldRecommendPluginUpgrade(project, current, latestKnown, published)
}

@JvmOverloads
fun shouldRecommendPluginUpgrade(
  project: Project,
  current: GradleVersion,
  latestKnown: GradleVersion,
  published: Set<GradleVersion> = setOf()
): Boolean {
  // Needed internally for development of Android support lib.
  if (SystemProperties.getBooleanProperty("studio.skip.agp.upgrade", false)) return false

  if (!RecommendedUpgradeReminder(project).shouldAsk()) return false
  return shouldRecommendUpgrade(current, latestKnown, published)
}

/**
 * Shows a notification balloon recommending that the user upgrade the version of the Android Gradle plugin.
 *
 * If they choose to accept this recommendation [performRecommendedPluginUpgrade] will show them a dialog and the option
 * to try and update the version automatically. If accepted this method will trigger a re-sync to pick up the new version.
 */
fun recommendPluginUpgrade(project: Project) {
  val existing = NotificationsManager
    .getNotificationsManager()
    .getNotificationsOfType(ProjectUpgradeNotification::class.java, project)

  if (existing.isEmpty()) {
    val listener = NotificationListener { notification, _ ->
      notification.expire()
      ApplicationManager.getApplication().executeOnPooledThread { performRecommendedPluginUpgrade(project) }
    }

    val notification = ProjectUpgradeNotification(
      AndroidBundle.message("project.upgrade.notification.title"), AndroidBundle.message("project.upgrade.notification.body"), listener)
    notification.notify(project)
  }
}

/**
 * Shows a [RecommendedPluginVersionUpgradeDialog] to the user prompting them to upgrade to a newer version of
 * the Android Gradle Plugin and Gradle. If the [currentVersion] is null this method always returns false, with
 * no action taken.
 *
 * If the user accepted the upgrade then the file are modified and the project is re-synced. This method uses
 * [AndroidPluginVersionUpdater] in order to perform these operations.
 *
 * Returns true if the project should be synced, false otherwise.
 *
 * Note: The [dialogFactory] argument should not be used outside of tests. It should only be used to mock the
 * result of the dialog.
 *
 */
@Slow
@JvmOverloads
fun performRecommendedPluginUpgrade(
  project: Project,
  currentVersion: GradleVersion? = project.findPluginInfo()?.pluginVersion,
  latestKnown: GradleVersion = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get()),
  dialogFactory: RecommendedPluginVersionUpgradeDialog.Factory = RecommendedPluginVersionUpgradeDialog.Factory()
) : Boolean {
  if (currentVersion == null) return false

  LOG.info("Gradle model version: $currentVersion, latest known version for IDE: $latestKnown")

  val published = IdeGoogleMavenRepository.getVersions("com.android.tools.build", "gradle")
  val state = computeGradlePluginUpgradeState(currentVersion, latestKnown, published)

  LOG.info("Gradle upgrade state: $state")
  if (state.importance != RECOMMEND) return false

  val userAccepted = invokeAndWaitIfNeeded(NON_MODAL) {
    val updateDialog = dialogFactory.create(project, currentVersion, state.target)
    updateDialog.showAndGet()
  }

  if (userAccepted) {
    // The user accepted the upgrade
    showAndInvokeAgpUpgradeRefactoringProcessor(project, currentVersion, state.target)
  }

  return false
}

// TODO(b/174543899): this is too weak; it doesn't catch modifications to:
//  - the root project's build.gradle[.kts]
//  - gradle-wrapper.properties
//  - gradle properties files
//  - build-adjacent files (e.g. proguard files, AndroidManifest.xml for the change namespacing R classes)
internal fun isCleanEnoughProject(project: Project): Boolean {
  ModuleManager.getInstance(project).modules.forEach { module ->
    val gradleFacet = GradleFacet.getInstance(module) ?: return@forEach
    val buildFile = gradleFacet.gradleModuleModel?.buildFile ?: return@forEach
    when (FileStatusManager.getInstance(project).getStatus(buildFile)) {
      FileStatus.NOT_CHANGED -> return@forEach
      else -> return false
    }
  }
  return true
}

@VisibleForTesting
@JvmOverloads
fun shouldRecommendUpgrade(current: GradleVersion, latestKnown: GradleVersion, published: Set<GradleVersion> = setOf()) : Boolean {
  return computeGradlePluginUpgradeState(current, latestKnown, published).importance == RECOMMEND
}

// **************************************************************************
// ** Forced upgrades
// **************************************************************************

/**
 * Returns whether, given the [current] version of AGP and the [latestKnown] version to Studio (which should be the
 * version returned by [LatestKnownPluginVersionProvider] except for tests), we should consider the AGP version
 * compatible with the running IDE.  If the versions are incompatible, we will have caused sync to fail; in most cases we
 * will attempt to offer an upgrade, but some cases (e.g. a newer [current] than [latestKnown]) the user will be responsible
 * for action to get the project to a working state.
 */
fun versionsAreIncompatible(
  current: GradleVersion,
  latestKnown: GradleVersion
) : Boolean {
  return computeAndroidGradlePluginCompatibility(current, latestKnown) != COMPATIBLE
}

/**
 * Called when the AGP and Android Studio versions are mutually incompatible (and the AGP version is not newer than the latest supported
 * version of AGP in this Android Studio).  Pops up a modal dialog to offer the user an upgrade (as minimal as possible) to the version
 * of AGP used by the project.  The user may dismiss the dialog in order to make changes manually; if they leave the modal upgrade
 * flow without completing an upgrade, we report a Sync message noting the existing incompatibility.  Returns when the modal flow is
 * complete: any upgrade will have been scheduled but might not have completed by the time this returns.
 */
@Slow
fun performForcedPluginUpgrade(
  project: Project,
  currentPluginVersion: GradleVersion,
  newPluginVersion: GradleVersion = computeGradlePluginUpgradeState(
    currentPluginVersion,
    GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get()),
    IdeGoogleMavenRepository.getVersions("com.android.tools.build", "gradle")
  ).target
) {
  val upgradeAccepted = invokeAndWaitIfNeeded(NON_MODAL) {
    ForcedPluginPreviewVersionUpgradeDialog(project, currentPluginVersion, newPluginVersion).showAndGet()
  }

  if (upgradeAccepted) {
    // The user accepted the upgrade: show upgrade details and offer the action.
    // Note: we retrieve a RefactoringProcessorInstantiator as a project service for the convenience of tests.
    val refactoringProcessorInstantiator = project.getService(RefactoringProcessorInstantiator::class.java)
    val processor = refactoringProcessorInstantiator.createProcessor(project, currentPluginVersion, newPluginVersion)
    // Enable only the minimum number of processors for a forced upgrade
    processor.componentRefactoringProcessors.forEach { component ->
      component.isEnabled = component.necessity().let { it == MANDATORY_CODEPENDENT || it == MANDATORY_INDEPENDENT }
    }
    val runProcessor = refactoringProcessorInstantiator.showAndGetAgpUpgradeDialog(processor)
    if (runProcessor) {
      DumbService.getInstance(project).smartInvokeLater { processor.run() }
      // upgrade refactoring scheduled
      return
    }
  }

  // The user has left the modal upgrade flow without completing an upgrade (maybe through cancel, maybe through preview).
  val syncMessage = SyncMessage(
    SyncMessage.DEFAULT_GROUP,
    ERROR,
    "The project is using an incompatible version of the ${AndroidPluginInfo.DESCRIPTION}.",
    "Please update your project to use version $newPluginVersion."
  )
  val pluginName = GROUP_ID + GRADLE_PATH_SEPARATOR + ARTIFACT_ID
  syncMessage.add(SearchInBuildFilesHyperlink(pluginName))

  GradleSyncMessages.getInstance(project).report(syncMessage)
}

data class GradlePluginUpgradeState(
  val importance: Importance,
  val target: GradleVersion,
) {
  enum class Importance {
    NO_UPGRADE,
    RECOMMEND,
    FORCE,
  }
}

fun computeGradlePluginUpgradeState(
  current: GradleVersion,
  latestKnown: GradleVersion,
  published: Set<GradleVersion>
): GradlePluginUpgradeState {
  when (computeAndroidGradlePluginCompatibility(current, latestKnown)) {
    BEFORE_MINIMUM -> {
      val minimum = GradleVersion.parse(SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION)
      val earliestStable = published
        .filter { !it.isPreview }
        .filter { it >= minimum }
        .filter { it <= latestKnown }
        .groupBy { GradleVersion(it.major, it.minor) }
        .minByOrNull { it.key }
        ?.value
        ?.maxOrNull()
      return GradlePluginUpgradeState(FORCE, earliestStable ?: latestKnown)
    }
    DIFFERENT_PREVIEW -> {
      val seriesAcceptableStable = published
        .filter { !it.isPreview }
        .filter { GradleVersion(it.major, it.minor) == GradleVersion(current.major, current.minor) }
        .filter { it <= latestKnown }
        .maxOrNull()
      // For the forced upgrade of a preview, we prefer the latest stable release in the same series as the preview, if one exists.  If
      // there is no such release, we have no option but to force an upgrade to the latest known version.  (This will happen, for example,
      // running a Canary Studio in series X+1 on a project using a Beta AGP from series X, until the Final AGP and Studio for series
      // X are released.)
      return GradlePluginUpgradeState(FORCE, seriesAcceptableStable ?: latestKnown)
    }
    AFTER_MAXIMUM -> return GradlePluginUpgradeState(FORCE, latestKnown)
    COMPATIBLE -> Unit
  }

  if (current >= latestKnown) return GradlePluginUpgradeState(NO_UPGRADE, current)
  if (!current.isPreview || current.previewType == "rc") {
    val acceptableStables = published
      .asSequence()
      .filter { !it.isPreview }
      .filter { it > current }
      .filter { it <= latestKnown }
      // We use the fact that groupBy preserves order both of keys and of entries in the list value.
      .sorted()
      .groupBy { GradleVersion(it.major, it.minor) }
      .asSequence()
      .groupBy { it.key.major }

    if (acceptableStables.isEmpty()) {
      // The first two cases here are unlikely, but theoretically possible, if somehow our published information is out of date
      return when {
        // If our latestKnown is stable, recommend it.
        !latestKnown.isPreview -> GradlePluginUpgradeState(RECOMMEND, latestKnown)
        latestKnown.previewType == "rc" -> GradlePluginUpgradeState(RECOMMEND, latestKnown)
        // Don't recommend upgrades from stable to preview.
        else -> GradlePluginUpgradeState(NO_UPGRADE, current)
      }
    }

    if (!acceptableStables.containsKey(current.major)) {
      // We can't upgrade to a new version of our current series, but there are upgrade targets (acceptableStables is not empty).  We
      // must be at the end of a major series, so recommend the latest compatible in the next major series.
      return GradlePluginUpgradeState(RECOMMEND, acceptableStables.first().value.last().value.last())
    }

    val currentSeriesCandidates = acceptableStables[current.major]!!
    val nextSeriesCandidates = acceptableStables.keys.firstOrNull { it > current.major }?.let { acceptableStables[it]!! }

    if (currentSeriesCandidates.maxOf { it.key } == GradleVersion(current.major, current.minor)) {
      // We have a version of the most recent series of our current major, though not the most up-to-date version of that.  If there's a
      // later stable series, recommend upgrading to that, otherwise recommend upgrading our point release.
      return GradlePluginUpgradeState(RECOMMEND, (nextSeriesCandidates ?: currentSeriesCandidates).last().value.last())
    }

    // Otherwise, we must have newer minor releases from our current major series.  Recommend upgrading to the latest minor release.
    return GradlePluginUpgradeState(RECOMMEND, currentSeriesCandidates.last().value.last())
  }
  else if (current.previewType == "alpha" || current.previewType == "beta") {
    if (latestKnown.isSnapshot) {
      // If latestKnown is -dev and current is in the same series, leave it alone.
      if (latestKnown.compareIgnoringQualifiers(current) == 0) return GradlePluginUpgradeState(NO_UPGRADE, current)
      // If latestKnown is -dev and current is a preview from an earlier series, recommend an upgrade.
      return GradlePluginUpgradeState(RECOMMEND, latestKnown)
    }
    throw IllegalStateException("Unreachable: handled by computeForcePluginUpgradeReason")
  }
  else {
    // Current is a snapshot.
    throw IllegalStateException("Unreachable: handled by computeForcePluginUpgradeReason")
  }
}

@Slow
fun Project.findPluginInfo() : AndroidPluginInfo? {
  val pluginInfo = AndroidPluginInfo.find(this)
  if (pluginInfo == null) {
    LOG.warn("Unable to obtain application's Android Project")
    return null
  }
  return pluginInfo
}

internal fun releaseNotesUrl(v: GradleVersion): String = when {
  v.isPreview -> "https://developer.android.com/studio/preview/features#android_gradle_plugin_${v.major}${v.minor}"
  else -> "https://developer.android.com/studio/releases/gradle-plugin#${v.major}-${v.minor}-0"
}