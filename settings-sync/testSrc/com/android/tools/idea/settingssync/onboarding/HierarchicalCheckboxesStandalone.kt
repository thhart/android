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
package com.android.tools.idea.settingssync.onboarding

import com.android.tools.adtui.compose.standaloneSingleWindowApplication
import com.intellij.settingsSync.core.SettingsSyncStateHolder
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

/** Test the sync category UI */
fun main() {
  val nodes = populateCheckboxNodes(SettingsSyncStateHolder())

  standaloneSingleWindowApplication(title = "Test HierarchicalCheckboxes") {
    IntUiTheme { HierarchicalCheckboxes(nodes) }
  }
}
