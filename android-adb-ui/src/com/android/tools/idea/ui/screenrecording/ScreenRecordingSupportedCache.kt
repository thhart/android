/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * A cache of mapping of a device to a boolean indicating if it supports screen recording.
 */
interface ScreenRecordingSupportedCache {
  fun isScreenRecordingSupported(serialNumber: String): Boolean

  companion object {
    fun getInstance(project: Project): ScreenRecordingSupportedCache = project.service<ScreenRecordingSupportedCache>()
  }
}
