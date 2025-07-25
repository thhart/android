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
package com.android.tools.idea.npw.module.recipes.androidModule.res.values_night

import com.android.sdklib.AndroidMajorVersion
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.DARK_ACTION_BAR_APPCOMPAT
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.DARK_ACTION_BAR_MATERIAL_COMPONENTS
import com.android.tools.idea.wizard.template.MaterialColor.BLACK
import com.android.tools.idea.wizard.template.MaterialColor.PURPLE_200
import com.android.tools.idea.wizard.template.MaterialColor.PURPLE_700
import com.android.tools.idea.wizard.template.MaterialColor.TEAL_200

fun androidModuleThemesMaterial3(themeName: String) =
  // When the contents are modified, need to modify
  // com.android.tools.idea.wizard.template.impl.activities.common.generateMaterial3Themes
  """<resources xmlns:tools="http://schemas.android.com/tools">
  <!-- Base application theme. -->
  <style name="Base.${themeName}" parent="Theme.Material3.DayNight.NoActionBar">
    <!-- Customize your dark theme here. -->
    <!-- <item name="colorPrimary">@color/my_dark_primary</item> -->
  </style>
</resources>"""

fun androidModuleThemes(
  useAndroidX: Boolean,
  minSdk: AndroidMajorVersion,
  themeName: String = "Theme.App",
) =
  if (useAndroidX)
    """<resources xmlns:tools="http://schemas.android.com/tools">
  <!-- Base application theme. -->
  <style name="$themeName" parent="$DARK_ACTION_BAR_MATERIAL_COMPONENTS">
      <!-- Primary brand color. -->
      <item name="colorPrimary">@color/${PURPLE_200.colorName}</item>
      <item name="colorPrimaryVariant">@color/${PURPLE_700.colorName}</item>
      <item name="colorOnPrimary">@color/${BLACK.colorName}</item>
      <!-- Secondary brand color. -->
      <item name="colorSecondary">@color/${TEAL_200.colorName}</item>
      <item name="colorSecondaryVariant">@color/${TEAL_200.colorName}</item>
      <item name="colorOnSecondary">@color/${BLACK.colorName}</item>
      <!-- Status bar color. -->
      <item name="android:statusBarColor"${if (minSdk.apiLevel < 21) " tools:targetApi=\"21\"" else ""}>?attr/colorPrimaryVariant</item>
      <!-- Customize your theme here. -->
  </style>
</resources>"""
  else
    """<resources xmlns:tools="http://schemas.android.com/tools">
  <!-- Base application theme. -->
  <style name="$themeName" parent="$DARK_ACTION_BAR_APPCOMPAT">
      <!-- Primary brand color. -->
      <item name="colorPrimary">@color/${PURPLE_200.colorName}</item>
      <item name="colorPrimaryDark">@color/${PURPLE_700.colorName}</item>
      <item name="colorAccent">@color/${TEAL_200.colorName}</item>
      <!-- Customize your theme here. -->
  </style>
</resources>"""
