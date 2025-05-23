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
package com.android.tools.idea.streaming.emulator

import com.android.emulator.control.ThemingStyle
import com.android.sdklib.internal.avd.AvdInfo
import com.intellij.ide.ui.LafManager

/**
 * Returns the emulator UI theme matching the current IDE theme.
 */
internal fun getEmulatorUiTheme(lafManager: LafManager): ThemingStyle.Style {
  val themeName = lafManager.currentUIThemeLookAndFeel.name
  return when {
    themeName.contains("High contrast", ignoreCase = true) -> ThemingStyle.Style.CONTRAST
    themeName.contains("Light", ignoreCase = true) -> ThemingStyle.Style.LIGHT
    else -> ThemingStyle.Style.DARK // Darcula and custom themes that are based on Darcula.
  }
}

/** Returns the AVD name with the appended " API NN" suffix if the AVD name doesn't contain it already. */
internal val AvdInfo.displayNameWithApi: String
  get() {
    val displayName = displayName
    if (displayName.contains(" API ")) {
      return displayName;
    }
    return "$displayName API ${androidVersion.apiStringWithoutExtension}";
  }
