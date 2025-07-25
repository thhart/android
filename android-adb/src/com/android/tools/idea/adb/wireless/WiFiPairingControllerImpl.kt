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
import com.android.tools.idea.concurrency.coroutineScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UiThread
class WiFiPairingControllerImpl(
  private val project: Project,
  parentDisposable: Disposable,
  private val pairingService: WiFiPairingService,
  private val notificationService: WiFiPairingNotificationService,
  private val view: WiFiPairingView,
  private val pairingCodePairingControllerFactory:
    (PairingMdnsService) -> PairingCodePairingController =
    {
      createPairingCodePairingController(project, pairingService, notificationService, it)
    },
  mdnsServiceUnderPairing: TrackingMdnsService?,
) : WiFiPairingController {
  companion object {
    fun createPairingCodePairingController(
      project: Project,
      pairingService: WiFiPairingService,
      notificationService: WiFiPairingNotificationService,
      pairingMdnsService: PairingMdnsService,
    ): PairingCodePairingController {
      val model = PairingCodePairingModel(pairingMdnsService)
      val view = PairingCodePairingViewImpl(project, notificationService, model)
      return PairingCodePairingController(project.coroutineScope, pairingService, view)
    }
  }

  private val qrCodeScanningController =
    QrCodeScanningController(pairingService, view, this, mdnsServiceUnderPairing)

  private val viewListener = MyViewListener(this)

  init {
    // Ensure we are disposed when the project closes
    Disposer.register(parentDisposable, this)

    // Ensure we are disposed when the view closes
    view.addListener(viewListener)
  }

  override fun dispose() {
    view.removeListener(viewListener)
  }

  override fun showDialog() {
    view.startMdnsCheck()

    // Check ADB is valid and mDNS is supported on this platform
    project.coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      val supportState = pairingService.checkMdnsSupport()
      when (supportState) {
        MdnsSupportState.Supported -> {
          view.showMdnsCheckSuccess()
          qrCodeScanningController.startPairingProcess()
        }
        MdnsSupportState.NotSupported -> {
          view.showMdnsNotSupportedError()
        }
        MdnsSupportState.AdbVersionTooLow -> {
          view.showMdnsNotSupportedByAdbError()
        }
        MdnsSupportState.AdbInvocationError -> {
          view.showMdnsCheckError()
        }
        MdnsSupportState.AdbMacEnvironmentBroken -> {
          view.showMacMdnsEnvironmentIsBroken()
        }
        MdnsSupportState.AdbDisabled -> {
          view.showMdnsDisabledOnAdbServer()
        }
      }
    }

    // Note: This call is blocking and returns only when the dialog is closed
    view.showDialog()
  }

  @UiThread
  inner class MyViewListener(private val parentDisposable: Disposable) : WiFiPairingView.Listener {
    override fun onScanAnotherQrCodeDeviceAction() {
      // Ignore
    }

    override fun onPairingCodePairAction(pairingMdnsService: PairingMdnsService) {
      pairingCodePairingControllerFactory.invoke(pairingMdnsService).showDialog()
    }

    override fun onClose() {
      Disposer.dispose(parentDisposable)
    }
  }
}
