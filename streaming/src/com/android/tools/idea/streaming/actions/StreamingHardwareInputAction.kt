/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.streaming.actions

import com.android.tools.idea.actions.enableRichTooltip
import com.android.tools.idea.streaming.core.DeviceId
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.util.containers.ContainerUtil.createConcurrentList

/**
 * ToggleAction for hardware input.
 *
 * When hardware input is enabled, Android Studio forwards unaltered mouse and keyboard events to
 * the device.
 */
internal class StreamingHardwareInputAction : ToggleAction(), DumbAware {

  override fun isSelected(event: AnActionEvent): Boolean {
    val displayView = getDisplayView(event) ?: return false
    return getHardwareInputStateStorage(event)?.isHardwareInputEnabled(displayView.deviceId) == true
  }

  override fun setSelected(event: AnActionEvent, selected: Boolean) {
    val displayView = getDisplayView(event) ?: return
    getHardwareInputStateStorage(event)?.setHardwareInputEnabled(displayView.deviceId, selected)
    displayView.hardwareInputStateChanged(event, selected)
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    val presentation = event.presentation
    if (getXrInputController(event)?.isMouseUsedForNavigation() == true) {
      presentation.isEnabled = false
      Toggleable.setSelected(presentation, false)
    }
    presentation.enableRichTooltip(this)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun getHardwareInputStateStorage(event: AnActionEvent): HardwareInputStateStorage? =
      event.project?.service<HardwareInputStateStorage>()

  companion object {
    const val ACTION_ID = "android.streaming.hardware.input"
  }
}

@Service(Service.Level.PROJECT)
internal class HardwareInputStateStorage {

  private val enabledDevices = createConcurrentList<String>()

  fun isHardwareInputEnabled(deviceId: DeviceId): Boolean =
      enabledDevices.contains(deviceId.storageKey)

  fun setHardwareInputEnabled(deviceId: DeviceId, enabled: Boolean) {
    if (enabled) {
      enabledDevices.addIfAbsent(deviceId.storageKey)
    } else {
      enabledDevices.remove(deviceId.storageKey)
    }
  }

  private val DeviceId.storageKey: String
    get() = when (this) {
      is DeviceId.EmulatorDeviceId -> emulatorId.avdId
      is DeviceId.PhysicalDeviceId -> serialNumber
    }
}
