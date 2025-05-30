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

package com.android.tools.idea.wearpairing

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.streaming.EmulatorSettings
import com.android.tools.idea.wearpairing.AndroidWearPairingBundle.Companion.message
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.android.tools.idea.wizard.model.ModelWizardDialog.CancellationPolicy.ALWAYS_CAN_CANCEL
import com.android.tools.idea.wizard.ui.SimpleStudioWizardLayout
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType.IDE
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType.MODELESS
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation.Companion.cancellable
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.ui.JBUI
import java.net.URL
import kotlinx.coroutines.withContext

// Keep an instance, so if the wizard is already running, just bring it to the front
private var wizardDialog: ModelWizardDialog? = null

class WearDevicePairingWizard {
  @Synchronized
  private fun show(project: Project?, selectedDevice: PairingDevice?) {
    wizardDialog?.apply {
      window?.toFront() // We already have a dialog, just bring it to the front and return
      return@show
    }

    val wizardAction =
      object : WizardAction {
        override fun closeAndStartAvd(project: Project?) {
          wizardDialog?.close(CANCEL_EXIT_CODE)

          if (project == null) {
            val deviceManagerAction =
              ActionManager.getInstance().getAction("WelcomeScreen.RunDeviceManager2")
            val actionEvent =
              AnActionEvent.createEvent(
                DataContext.EMPTY_CONTEXT,
                null,
                ActionPlaces.UNKNOWN,
                ActionUiKind.NONE,
                null,
              )
            ActionUtil.invokeAction(deviceManagerAction, actionEvent, null)
          } else {
            // from com.android.tools.idea.devicemanagerv2.DeviceManager2Action
            val deviceManagerAction =
              ActionManager.getInstance().getAction("Android.DeviceManager2")
            val actionEvent =
              AnActionEvent.createEvent(
                SimpleDataContext.getProjectContext(project),
                null,
                ActionPlaces.UNKNOWN,
                ActionUiKind.NONE,
                null,
              )
            ActionUtil.invokeAction(deviceManagerAction, actionEvent, null)
          }
        }

        override fun restart(project: Project?) {
          wizardDialog?.close(CANCEL_EXIT_CODE)
          show(project, selectedDevice)
        }
      }

    val model = WearDevicePairingModel()
    when (selectedDevice?.isWearDevice) {
      true -> model.selectedWearDevice.setNullableValue(selectedDevice)
      false -> model.selectedPhoneDevice.setNullableValue(selectedDevice)
      null -> {}
    }
    val modelWizard =
      ModelWizard.Builder().addStep(DeviceListStep(model, project, wizardAction)).build()

    // Remove the dialog reference when the dialog is disposed (closed).
    Disposer.register(modelWizard) { wizardDialog = null }

    WearPairingManager.getInstance().setDeviceListListener(model, wizardAction)

    val modality = if (EmulatorSettings.getInstance().launchInToolWindow) MODELESS else IDE
    wizardDialog =
      StudioWizardDialogBuilder(modelWizard, "Wear OS emulator pairing assistant")
        .setProject(project)
        .setHelpUrl(URL(WEAR_DOCS_LINK))
        .setModalityType(modality)
        .setCancellationPolicy(ALWAYS_CAN_CANCEL)
        .setPreferredSize(JBUI.size(560, 420))
        .setMinimumSize(JBUI.size(400, 250))
        .build(SimpleStudioWizardLayout())

    wizardDialog?.show()
  }

  @UiThread
  fun show(project: Project?, selectedDeviceId: String?) {
    val owner = project?.let { ModalTaskOwner.project(project) } ?: ModalTaskOwner.guess()
    runWithModalProgressBlocking(
      owner,
      message("wear.assistant.device.connection.balloon.link"),
      cancellable(),
    ) {
      val selectedDevice =
        selectedDeviceId?.let { WearPairingManager.getInstance().findDevice(selectedDeviceId) }
      withContext(uiThread) { show(project, selectedDevice) }
    }
  }
}
