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
package com.android.tools.idea.adb.wireless

import com.android.annotations.concurrency.UiThread
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo

@UiThread
class PinCodePairingViewImpl(project: Project, override val model: PinCodePairingModel) : PinCodePairingView {
  private val dlg = PinCodePairingDialog(project)
  private val listeners = ArrayList<PinCodePairingView.Listener>()
  private var allowPairAction = true

  override fun showDialog() {
    dlg.setDevice(model.service)
    dlg.validationHandler = {
      if (dlg.isPinCodeValid) {
        null
      }
      else {
        ValidationInfo("PIN code must be exactly 6 digits", dlg.pinCodeComponent)
      }
    }
    dlg.okButtonHandler = {
      if (allowPairAction) {
        model.pinCode = dlg.currentPinCode
        listeners.forEach { it.onPairInvoked() }
         true
      } else {
        false
      }
    }
    dlg.show()
  }

  override fun addListener(listener: PinCodePairingView.Listener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: PinCodePairingView.Listener) {
    listeners.remove(listener)
  }

  override fun showPairingInProgress() {
    dlg.showPairingInProgress("Pairing with device...")
    allowPairAction = false
  }

  override fun showWaitingForDeviceProgress(pairingResult: PairingResult) {
    dlg.showPairingInProgress("Gathering device information...")
    allowPairAction = false
  }

  override fun showPairingSuccess(service: MdnsService, device: AdbOnlineDevice) {
    dlg.showPairingSuccess(device)
    allowPairAction = false
  }

  override fun showPairingError(service: MdnsService, error: Throwable) {
    dlg.showPairingError()
    allowPairAction = true
  }
}