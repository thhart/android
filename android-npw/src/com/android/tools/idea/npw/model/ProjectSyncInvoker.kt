/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.model

import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason.Companion.PROJECT_MODIFIED
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.openapi.project.Project

interface ProjectSyncInvoker {
  /**
   * Triggers synchronizing the IDE model with the build system model of the project.
   */
  fun syncProject(project: Project)

  /**
   * Triggers synchronizing using [ProjectSyncInvoker].
   */
  class DefaultProjectSyncInvoker : ProjectSyncInvoker {
    override fun syncProject(project: Project) {
      project.getProjectSystem().getSyncManager().requestSyncProject(PROJECT_MODIFIED)
    }
  }
}