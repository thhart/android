/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.home

import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.ProcessUtils.isProfileable
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.taskbased.home.StartTaskSelectionError.StartTaskSelectionErrorCode
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.TaskSupportUtils
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandler

object TaskSelectionVerificationUtils {

  private fun isTaskSelectionValid(taskType: ProfilerTaskType) = taskType != ProfilerTaskType.UNSPECIFIED

  private fun isProcessSelectionValid(process: Common.Process) = process != Common.Process.getDefaultInstance()

  private fun isProcessAlive(process: Common.Process) = process.state == Common.Process.State.ALIVE

  private fun isDeviceSelectionValid(device: ProcessListModel.ProfilerDeviceSelection?) = device != null

  private fun isDeviceSelectionOnline(device: ProcessListModel.ProfilerDeviceSelection) = device.device != Common.Device.getDefaultInstance()

  private fun isTaskSupportedByProcess(selectedTaskType: ProfilerTaskType,
                                       taskHandlers: Map<ProfilerTaskType, ProfilerTaskHandler>,
                                       selectedDevice: ProcessListModel.ProfilerDeviceSelection?,
                                       selectedProcess: Common.Process) = isTaskSelectionValid(selectedTaskType) && isDeviceSelectionValid(
    selectedDevice) && isDeviceSelectionOnline(selectedDevice!!) && taskHandlers[selectedTaskType]!!.checkSupportForDeviceAndProcess(
    selectedDevice.device, selectedProcess) == null

  fun isSelectedProcessPreferred(selectedProcess: Common.Process, profilers: StudioProfilers) =
    selectedProcess.name == profilers.preferredProcessName

  fun isProfileablePreferredButNotPresent(taskType: ProfilerTaskType,
                                          process: Common.Process,
                                          profilingProcessStartingPoint: TaskHomeTabModel.ProfilingProcessStartingPoint) =
    taskType.prefersProfileable && isProcessAlive(
      process) && !process.isProfileable() && profilingProcessStartingPoint == TaskHomeTabModel.ProfilingProcessStartingPoint.NOW

  /**
   * Determines if starting a task from process start is enabled. This method is utilized to determine whether the PROCESS_START option is
   * enabled and selectable in the task starting point dropdown. It is also used to determine whether the user is able to start the task
   * from process start.
   */
  fun isTaskStartFromProcessStartEnabled(selectedTaskType: ProfilerTaskType, selectedProcess: Common.Process, profilers: StudioProfilers) =
    isSelectedProcessPreferred(selectedProcess, profilers) && profilers.ideServices.isTaskSupportedOnStartup(selectedTaskType)

  /**
   * Determines if starting a task from now is enabled. This method is utilized to determine whether the NOW option is enabled and
   * selectable in the task starting point dropdown. It is also used to determine whether the user is able to start the task from now.
   */
  fun isTaskStartFromNowEnabled(selectedProcess: Common.Process) =
    isProcessSelectionValid(selectedProcess) && isProcessAlive(selectedProcess)

  /**
   * Determines whether the user can start the task from process start, considering if the PROCESS_START dropdown option is currently
   * enabled (see isTaskStartFromProcessStartEnabled), and if the user selected the preferred process and startup-capable task.
   *
   * Note: This method sets the criteria for enabling the start profiler task button and is invoked only if the user has selected
   * PROCESS_START from the task starting point dropdown. The criteria for enabling the PROCESS_START option
   * (see TaskHomeTabModel.isTaskStartFromProcessStartEnabled) is a subset of the criteria needed to enable the start profiler task button.
   * Thus, the user can have the PROCESS_START option enabled and/or selected, but the start profiler task button may be disabled.
   */
  fun canTaskStartFromProcessStart(selectedTaskType: ProfilerTaskType,
                                   selectedDevice: ProcessListModel.ProfilerDeviceSelection?,
                                   selectedProcess: Common.Process,
                                   profilers: StudioProfilers) =
    isTaskStartFromProcessStartEnabled(selectedTaskType, selectedProcess, profilers) &&
    selectedDevice?.let { TaskSupportUtils.doesDeviceSupportProfilingTaskFromProcessStart(selectedTaskType, it.featureLevel) } ?: false

  /**
   * Determines whether the user can start the task from now, considering if the NOW dropdown option is currently enabled (see
   * isTaskStartFromNowEnabled), and if the user selected a running device and device-compatible task.
   *
   * Note: This method sets the criteria for enabling the start profiler task button and is invoked only if the user has selected NOW from
   * the task starting point dropdown. The criteria for enabling the NOW option (see TaskHomeTabModel.isTaskStartFromNowEnabled) is a
   * subset of the criteria needed to enable the start profiler task button. Thus, the user can have the NOW option enabled and/or selected,
   * but the start profiler task button may be disabled.
   */
  fun canTaskStartFromNow(selectedTaskType: ProfilerTaskType,
                          selectedDevice: ProcessListModel.ProfilerDeviceSelection?,
                          selectedProcess: Common.Process,
                          taskHandlers: Map<ProfilerTaskType, ProfilerTaskHandler>) =
    isTaskStartFromNowEnabled(selectedProcess) &&
    isTaskSupportedByProcess(selectedTaskType, taskHandlers, selectedDevice, selectedProcess)

  /**
   * Based on the user's task starting point dropdown selection, returns whether the task can start. This method controls the enablement
   * of the start profiler task button.
   */
  fun canStartTask(selectedTaskType: ProfilerTaskType,
                   selectedDevice: ProcessListModel.ProfilerDeviceSelection?,
                   selectedProcess: Common.Process,
                   profilingProcessStartingPoint: TaskHomeTabModel.ProfilingProcessStartingPoint,
                   profilers: StudioProfilers): Boolean =
    when (profilingProcessStartingPoint) {
      TaskHomeTabModel.ProfilingProcessStartingPoint.NOW -> canTaskStartFromNow(selectedTaskType, selectedDevice, selectedProcess,
                                                                                profilers.taskHandlers)

      TaskHomeTabModel.ProfilingProcessStartingPoint.PROCESS_START -> canTaskStartFromProcessStart(selectedTaskType, selectedDevice,
                                                                                                   selectedProcess, profilers)

      else -> false
    }

  private fun areSelectionsValid(selectedTaskType: ProfilerTaskType,
                                 selectedDevice: ProcessListModel.ProfilerDeviceSelection?,
                                 selectedProcess: Common.Process) = isDeviceSelectionValid(selectedDevice) && isProcessSelectionValid(
    selectedProcess) && isTaskSelectionValid(selectedTaskType)

  /**
   * Assuming an error with starting a task from 'process start', this method returns an error indicating which selection is causing such
   * failure. If the reason is not caught by the conditions outlined, null is returned, allowing the caller to handle such result.
   *
   * Also assumes/asserts that by the time this method is called, all selections (i.e. device, process, and task type) made are valid.
   */
  private fun getStartTaskFromProcessStartError(selectedTaskType: ProfilerTaskType,
                                                selectedDevice: ProcessListModel.ProfilerDeviceSelection?,
                                                selectedProcess: Common.Process,
                                                profilers: StudioProfilers): StartTaskSelectionError {
    assert(!canTaskStartFromProcessStart(selectedTaskType, selectedDevice, selectedProcess, profilers))
    assert(areSelectionsValid(selectedTaskType, selectedDevice, selectedProcess))
    return if (!isSelectedProcessPreferred(selectedProcess, profilers)) {
      StartTaskSelectionError(StartTaskSelectionErrorCode.PREFERRED_PROCESS_NOT_SELECTED_FOR_STARTUP_TASK)
    }
    else if (!profilers.ideServices.isTaskSupportedOnStartup(selectedTaskType)) {
      StartTaskSelectionError(StartTaskSelectionErrorCode.TASK_UNSUPPORTED_ON_STARTUP)
    }
    else if (!TaskSupportUtils.doesDeviceSupportProfilingTaskFromProcessStart(selectedTaskType, selectedDevice!!.featureLevel)) {
      return StartTaskSelectionError(StartTaskSelectionErrorCode.TASK_FROM_PROCESS_START_USING_API_BELOW_MIN,
                                     getMinApiStartTaskErrorMessage(TaskSupportUtils.getProcessStartMinApi(selectedTaskType)))
    }
    else {
      StartTaskSelectionError(StartTaskSelectionErrorCode.GENERAL_ERROR)
    }
  }

  /**
   * Assuming an error with starting a task from 'now', this method returns an error indicating which selection is causing such
   * failure. If the reason is not caught by the conditions outlined, null is returned, allowing the caller to handle such result.
   *
   * Also assumes/asserts that by the time this method is called, all selections (i.e. device, process, and task type) made are valid.
   */
  private fun getStartTaskFromNowStartError(selectedTaskType: ProfilerTaskType,
                                            selectedDevice: ProcessListModel.ProfilerDeviceSelection?,
                                            selectedProcess: Common.Process,
                                            taskHandlers: Map<ProfilerTaskType, ProfilerTaskHandler>): StartTaskSelectionError {
    assert(!canTaskStartFromNow(selectedTaskType, selectedDevice, selectedProcess, taskHandlers))
    assert(areSelectionsValid(selectedTaskType, selectedDevice, selectedProcess))
    if (!isDeviceSelectionOnline(selectedDevice!!)) {
      return StartTaskSelectionError(StartTaskSelectionErrorCode.DEVICE_SELECTION_IS_OFFLINE)
    }

    val supportsDeviceAndProcess = taskHandlers[selectedTaskType]!!.checkSupportForDeviceAndProcess(selectedDevice.device, selectedProcess)
    if (supportsDeviceAndProcess != null) {
      return supportsDeviceAndProcess
    }

    if (selectedProcess.state == Common.Process.State.DEAD) {
      return StartTaskSelectionError(StartTaskSelectionErrorCode.TASK_FROM_NOW_USING_DEAD_PROCESS)
    }

    return StartTaskSelectionError(StartTaskSelectionErrorCode.GENERAL_ERROR)
  }

  /**
   * This method assumes that the user cannot start the task and thus should only be called when an error is certain. That said, this
   * method inspects the user's selections and returns a respective an error enum.
   *
   * It is possible to have multiple errors (e.g. user does not have a process or task selected), but this method only returns one error as
   * the UI can only display one error at a time.
   */
  fun getStartTaskError(selectedTaskType: ProfilerTaskType,
                        selectedDevice: ProcessListModel.ProfilerDeviceSelection?,
                        selectedProcess: Common.Process,
                        profilingProcessStartingPoint: TaskHomeTabModel.ProfilingProcessStartingPoint,
                        profilers: StudioProfilers): StartTaskSelectionError {
    // The rest of the code can now assume there is some error.
    assert(!canStartTask(selectedTaskType, selectedDevice, selectedProcess, profilingProcessStartingPoint, profilers))
    return if (!isDeviceSelectionValid(selectedDevice)) {
      StartTaskSelectionError(StartTaskSelectionErrorCode.INVALID_DEVICE)
    }
    else if (!isProcessSelectionValid(selectedProcess)) {
      StartTaskSelectionError(StartTaskSelectionErrorCode.INVALID_PROCESS)
    }
    else if (!isTaskSelectionValid(selectedTaskType)) {
      StartTaskSelectionError(StartTaskSelectionErrorCode.INVALID_TASK)
    }
    // Errors when attempting to perform a startup task.
    else if (profilingProcessStartingPoint == TaskHomeTabModel.ProfilingProcessStartingPoint.PROCESS_START) {
      getStartTaskFromProcessStartError(selectedTaskType, selectedDevice, selectedProcess, profilers)
    }
    // Errors when attempting to perform a task from "now".
    else if (profilingProcessStartingPoint == TaskHomeTabModel.ProfilingProcessStartingPoint.NOW) {
      getStartTaskFromNowStartError(selectedTaskType, selectedDevice, selectedProcess, profilers.taskHandlers)
    }
    else if (profilingProcessStartingPoint == TaskHomeTabModel.ProfilingProcessStartingPoint.UNSPECIFIED) {
      StartTaskSelectionError(StartTaskSelectionErrorCode.NO_STARTING_POINT_SELECTED)
    }
    else {
      StartTaskSelectionError(StartTaskSelectionErrorCode.GENERAL_ERROR)
    }
  }

  fun getMinApiStartTaskErrorMessage(minApi: Int) = "Selected task requires API $minApi or higher"

  val STARTUP_TASK_ERRORS = listOf(StartTaskSelectionErrorCode.PREFERRED_PROCESS_NOT_SELECTED_FOR_STARTUP_TASK,
                                   StartTaskSelectionErrorCode.TASK_UNSUPPORTED_ON_STARTUP,
                                   StartTaskSelectionErrorCode.TASK_FROM_PROCESS_START_USING_API_BELOW_MIN)
}

data class StartTaskSelectionError(
  val startTaskSelectionErrorCode: StartTaskSelectionErrorCode,
  val actionableInfo: String? = null
) {
  enum class StartTaskSelectionErrorCode {
    INVALID_DEVICE,
    INVALID_PROCESS,
    INVALID_TASK,
    PREFERRED_PROCESS_NOT_SELECTED_FOR_STARTUP_TASK,
    TASK_UNSUPPORTED_ON_STARTUP,
    TASK_FROM_PROCESS_START_USING_API_BELOW_MIN,
    TASK_FROM_NOW_USING_API_BELOW_MIN,
    TASK_FROM_NOW_USING_DEAD_PROCESS,
    DEVICE_SELECTION_IS_OFFLINE,
    TASK_REQUIRES_DEBUGGABLE_PROCESS,
    NO_STARTING_POINT_SELECTED,
    // Generalized error to cover the rest of task start errors.
    GENERAL_ERROR;
  }
}