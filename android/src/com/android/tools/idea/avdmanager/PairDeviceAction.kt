/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.avdmanager

import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.repository.targets.SystemImage
import com.android.tools.idea.wearpairing.WearDevicePairingWizard
import icons.StudioIcons
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.event.ActionEvent

class PairDeviceAction(avdInfoProvider: AvdInfoProvider) : AvdUiAction(avdInfoProvider,
                                                                       "Pair device",
                                                                       getDescription(avdInfoProvider.avdInfo),
                                                                       StudioIcons.LayoutEditor.Toolbar.INSERT_HORIZ_CHAIN
) {
  override fun actionPerformed(actionEvent: ActionEvent) {
    val project = myAvdInfoProvider.project ?: return
    // TODO: Propagate deviceID and implement single panel changes
    WearDevicePairingWizard().show(project)
  }

  override fun isEnabled(): Boolean {
    val avd = avdInfo ?: return false
    return avd.tag == SystemImage.WEAR_TAG || (avd.androidVersion.apiLevel >= 30 && avd.hasPlayStore())
  }
}

private fun getDescription(avdInfo: AvdInfo?): String {
  avdInfo ?: return ""
  val isWearDevice = avdInfo.tag == SystemImage.WEAR_TAG
  return when {
    !isWearDevice && avdInfo.androidVersion.apiLevel < 30 -> message("wear.assistant.device.list.tooltip.requires.api")
    !isWearDevice && !avdInfo.hasPlayStore() -> message("wear.assistant.device.list.tooltip.requires.play")
    else -> message("wear.assistant.device.list.tooltip.ok")
  }
}