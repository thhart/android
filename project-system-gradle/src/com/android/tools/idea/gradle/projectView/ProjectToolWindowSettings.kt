/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.projectView

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil


@com.intellij.openapi.components.State(
  name = "ProjectToolWindow",
  storages = [Storage("projectToolWindow.xml", roamingType = RoamingType.LOCAL)],
)
class ProjectToolWindowSettings: PersistentStateComponent<ProjectToolWindowSettings> {
  var showBuildFilesInModule = false

  companion object {
    @JvmStatic
    fun getInstance(): ProjectToolWindowSettings {
      return ApplicationManager.getApplication().getService(ProjectToolWindowSettings::class.java)
    }

    @JvmStatic
    fun getInstanceIfCreated(): ProjectToolWindowSettings? {
      return ApplicationManager.getApplication().getServiceIfCreated(ProjectToolWindowSettings::class.java)
    }
  }

  override fun getState(): ProjectToolWindowSettings {
    return this
  }

  override fun loadState(state: ProjectToolWindowSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }
}