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
package com.android.tools.idea.devicemanagerv2

import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.tools.adtui.categorytable.IconButton
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.deviceprovisioner.launchCatchingDeviceActionException
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ActivateTemplateButton(scope: CoroutineScope, private val template: DeviceTemplate) :
  IconButton(StudioIcons.Avd.RUN) {

  init {
    addActionListener {
      template.launchCatchingDeviceActionException(
        scope,
        projectFromComponentContext(this@ActivateTemplateButton),
      ) {
        activationAction.activate()
      }
    }

    scope.launch(AndroidDispatchers.uiThread) { trackActionPresentation(template.activationAction) }
  }
}
