/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.ui.screenrecording

import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.shellAsText
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.adblib.AdbLibApplicationService
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.ui.AndroidAdbUiBundle
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.android.tools.idea.ui.DISPLAY_ID_KEY
import com.android.tools.idea.ui.DISPLAY_INFO_PROVIDER_KEY
import com.android.tools.idea.ui.DisplayInfoProvider
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import icons.StudioIcons
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.nio.file.Path
import java.time.Duration

/** An action that records the device screen. */
class ScreenRecorderAction : DumbAwareAction(
  AndroidAdbUiBundle.message("screenrecord.action.title"),
  AndroidAdbUiBundle.message("screenrecord.action.description"),
  StudioIcons.Common.VIDEO_CAPTURE
) {

  private val logger = thisLogger()

  /** Serial numbers of devices that are currently recording. */
  private val recordingInProgress = ConcurrentCollectionFactory.createConcurrentSet<String>()

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(event: AnActionEvent) {
    val params = event.getData(SCREEN_RECORDER_PARAMETERS_KEY)
    val project = event.project
    event.presentation.isEnabled =
        params != null && project != null && isRecordingSupported(params, project) && !recordingInProgress.contains(params.serialNumber)
  }

  override fun actionPerformed(event: AnActionEvent) {
    val params = event.getData(SCREEN_RECORDER_PARAMETERS_KEY) ?: return
    val displayId = event.getData(DISPLAY_ID_KEY) ?: 0
    val displayInfoProvider = event.getData(DISPLAY_INFO_PROVIDER_KEY)
    val project = event.project ?: return
    val avdFolder = params.avdFolder
    val dialog = ScreenRecorderOptionsDialog(project, avdFolder != null, params.featureLevel)
    if (dialog.showAndGet()) {
      val settings = DeviceScreenRecordingSettings.getInstance()
      val avdFolderForRecording = if (settings.useEmulatorRecordingWhenAvailable) avdFolder else null
      startRecordingAsync(settings, params, displayId, displayInfoProvider, avdFolderForRecording, project)
    }
  }

  private fun isRecordingSupported(params: ScreenRecordingParameters, project: Project): Boolean {
    return params.featureLevel >= 19 &&
           ScreenRecordingSupportedCache.getInstance(project).isScreenRecordingSupported(params.serialNumber)
  }

  @UiThread
  private fun startRecordingAsync(
      options: DeviceScreenRecordingSettings,
      params: ScreenRecordingParameters,
      displayId: Int,
      displayInfoProvider: DisplayInfoProvider?,
      avdFolder: Path?,
      project: Project) {
    val adbSession: AdbSession = AdbLibApplicationService.instance.session
    val serialNumber = params.serialNumber
    recordingInProgress.add(serialNumber)

    val disposableParent = params.recordingLifetimeDisposable
    val coroutineScope = disposableParent.createCoroutineScope()
    val exceptionHandler = coroutineExceptionHandler(project, coroutineScope)
    coroutineScope.launch(exceptionHandler) {
      val showTouchEnabled = isShowTouchEnabled(adbSession, serialNumber)
      val size = displayInfoProvider?.getDisplaySize(displayId) ?: getDeviceScreenSize(adbSession, serialNumber, displayId)
      val emulatorRecordingFile = avdFolder?.resolve(EMU_TMP_FILENAME)
      val timeLimitSec = if (emulatorRecordingFile != null || params.featureLevel >= 34) MAX_RECORDING_DURATION_MINUTES * 60 else 0
      val recorderOptions = options.toScreenRecorderOptions(displayId, size, timeLimitSec)
      if (recorderOptions.showTouches != showTouchEnabled) {
        setShowTouch(adbSession, serialNumber, recorderOptions.showTouches)
      }
      try {
        val recodingProvider = when (emulatorRecordingFile) {
          null -> ShellCommandRecordingProvider(
            disposableParent,
            serialNumber,
            REMOTE_PATH.format(System.currentTimeMillis()),
            recorderOptions,
            adbSession)

          else -> EmulatorConsoleRecordingProvider(
            disposableParent,
            serialNumber,
            emulatorRecordingFile,
            recorderOptions,
            adbSession)
        }
        val timeLimit = if (timeLimitSec > 0) timeLimitSec else MAX_RECORDING_DURATION_MINUTES_LEGACY * 60
        val recorder = ScreenRecorder(project, recodingProvider, params.deviceName)
        recorder.recordScreen(timeLimit)
      }
      finally {
        recordingInProgress.remove(serialNumber)
        ActivityTracker.getInstance().inc()
        if (recorderOptions.showTouches != showTouchEnabled) {
          setShowTouch(adbSession, serialNumber, showTouchEnabled)
        }
      }
    }
  }

  private suspend fun getDeviceScreenSize(adbSession: AdbSession, serialNumber: String, displayId: Int): Dimension? {
    try {
      //TODO: Check for `stderr` and `exitCode` to report errors
      val displaySelector = if (displayId == 0) "" else " -d $displayId"
      val out = execute(adbSession, serialNumber, "wm size$displaySelector")
      val matchResult = WM_SIZE_OUTPUT_REGEX.find(out)
      if (matchResult == null) {
        logger.warn("Unexpected output from 'wm size': $out")
        return null
      }
      val width = matchResult.groups["width"]
      val height = matchResult.groups["height"]
      if (width == null || height == null) {
        logger.warn("Unexpected output from 'wm size': $out")
        return null
      }
      return Dimension(width.value.toInt(), height.value.toInt())
    }
    catch (e: Exception) {
      logger.warn("Failed to get device screen size.", e)
    }
    return null
  }

  private suspend fun execute(adbSession: AdbSession, serialNumber: String, command: String): String =
    //TODO: Check for `stderr` and `exitCode` to report errors
    adbSession.deviceServices.shellAsText(DeviceSelector.fromSerialNumber(serialNumber), command, commandTimeout = COMMAND_TIMEOUT).stdout

  private suspend fun setShowTouch(adbSession: AdbSession, serialNumber: String, isEnabled: Boolean) {
    val value = if (isEnabled) 1 else 0
    try {
      //TODO: Check for `stderr` and `exitCode` to report errors
      execute(adbSession, serialNumber, "settings put system show_touches $value")
    }
    catch (e: Exception) {
      logger.warn("Failed to set show taps to $isEnabled", e)
    }
  }

  private suspend fun isShowTouchEnabled(adbSession: AdbSession, serialNumber: String): Boolean {
    //TODO: Check for `stderr` and `exitCode` to report errors
    val out = execute(adbSession, serialNumber, "settings get system show_touches")
    return out.trim() == "1"
  }

  private fun coroutineExceptionHandler(project: Project, coroutineScope: CoroutineScope) = CoroutineExceptionHandler { _, throwable ->
    logger.warn("Failed to record screen", throwable)
    coroutineScope.launch(Dispatchers.EDT) {
      Messages.showErrorDialog(
        project,
        AndroidAdbUiBundle.message("screenrecord.error.exception", throwable),
        AndroidAdbUiBundle.message("screenrecord.action.title"))
    }
  }

  companion object {
    @JvmStatic
    val SCREEN_RECORDER_PARAMETERS_KEY = DataKey.create<ScreenRecordingParameters>("ScreenRecordingParameters")

    const val MAX_RECORDING_DURATION_MINUTES = 30 // Emulator or Android 14+.
    const val MAX_RECORDING_DURATION_MINUTES_LEGACY = 3

    private const val REMOTE_PATH = "/sdcard/screen-recording-%d.mp4"
    private val WM_SIZE_OUTPUT_REGEX = Regex("(?<width>\\d+)x(?<height>\\d+)")
    private const val EMU_TMP_FILENAME = "tmp.webm"
    private val COMMAND_TIMEOUT = Duration.ofSeconds(2)
  }
}

data class ScreenRecordingParameters(
  val serialNumber: String,
  val deviceName: String,
  val featureLevel: Int,
  val recordingLifetimeDisposable: Disposable,
  val avdFolder: Path?, // Only for AVD, otherwise null.
)
