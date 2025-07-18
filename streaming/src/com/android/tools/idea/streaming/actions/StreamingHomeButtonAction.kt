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

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.streaming.device.actions.DeviceAllAppsButtonAction
import com.android.tools.idea.streaming.device.actions.DeviceHomeButtonAction
import com.android.tools.idea.streaming.emulator.actions.EmulatorAllAppsButtonAction
import com.android.tools.idea.streaming.emulator.actions.EmulatorHomeButtonAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/** Simulates pressing the Home button on an Android device. */
internal class StreamingHomeButtonAction : DelegatingPushButtonAction(HomeButtonAction(), AllAppsButtonAction()), DumbAware {

  override fun getDelegate(event: AnActionEvent): AnAction =
    delegates[if (getDeviceType(event) == DeviceType.XR) 1 else 0]
}

private class HomeButtonAction : StreamingPushButtonAction(EmulatorHomeButtonAction(), DeviceHomeButtonAction())

private class AllAppsButtonAction : StreamingPushButtonAction(EmulatorAllAppsButtonAction(), DeviceAllAppsButtonAction())
