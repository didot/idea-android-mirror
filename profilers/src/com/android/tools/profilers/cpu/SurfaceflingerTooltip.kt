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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.model.AspectModel
import com.android.tools.adtui.model.DataSeries
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.Timeline
import com.android.tools.adtui.model.TooltipModel
import com.android.tools.profilers.cpu.atrace.SurfaceflingerEvent

class SurfaceflingerTooltip(val timeline: Timeline, private val surfaceflingerEvents: DataSeries<SurfaceflingerEvent>)
  : TooltipModel, AspectModel<SurfaceflingerTooltip.Aspect>() {
  enum class Aspect {
    /**
     * The hovering surfacefligner event changed.
     */
    EVENT_CHANGED,
  }

  var activeSurfaceflingerEvent: SurfaceflingerEvent? = null
    private set

  override fun dispose() {
    timeline.tooltipRange.removeDependencies(this)
  }

  private fun updateEvent() {
    val series = surfaceflingerEvents.getDataForRange(timeline.tooltipRange)
    val surfaceflingerEvent = if (series.isEmpty()) null else series[0].value
    if (surfaceflingerEvent != activeSurfaceflingerEvent) {
      activeSurfaceflingerEvent = surfaceflingerEvent
      changed(Aspect.EVENT_CHANGED)
    }
  }

  init {
    timeline.tooltipRange.addDependency(this).onChange(Range.Aspect.RANGE, this::updateEvent)
  }
}