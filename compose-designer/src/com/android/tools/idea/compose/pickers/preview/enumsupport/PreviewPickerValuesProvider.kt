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
package com.android.tools.idea.compose.pickers.preview.enumsupport

import com.android.ide.common.resources.Locale
import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device
import com.android.tools.idea.compose.pickers.base.enumsupport.EnumSupportValuesProvider
import com.android.tools.idea.compose.pickers.base.enumsupport.EnumValuesProvider
import com.android.tools.idea.compose.pickers.common.enumsupport.PsiCallEnumSupportValuesProvider
import com.android.tools.idea.compose.pickers.preview.enumsupport.devices.DeviceEnumValueBuilder
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.configurations.CanonicalDeviceType
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.DeviceGroup
import com.android.tools.idea.configurations.groupDevices
import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.idea.preview.util.getSdkDevices
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.layoutlib.isLayoutLibTarget
import com.android.tools.preview.config.PARAMETER_API_LEVEL
import com.android.tools.preview.config.PARAMETER_GROUP
import com.android.tools.preview.config.PARAMETER_HARDWARE_DEVICE
import com.android.tools.preview.config.PARAMETER_LOCALE
import com.android.tools.preview.config.PARAMETER_UI_MODE
import com.android.tools.preview.config.PARAMETER_WALLPAPER
import com.android.tools.property.panel.api.EnumValue
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.text.nullize
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
internal fun IAndroidTarget.displayName(): String =
  "${version.apiLevel}${versionName?.let { " (Android $it)" } ?: ""}"

object PreviewPickerValuesProvider {
  @JvmStatic
  fun createPreviewValuesProvider(
    module: Module,
    containingFile: VirtualFile?,
  ): EnumSupportValuesProvider {
    val providersMap = mutableMapOf<String, EnumValuesProvider>()

    providersMap[PARAMETER_UI_MODE] = createUiModeEnumProvider()

    providersMap[PARAMETER_API_LEVEL] = createApiLevelEnumProvider(module)

    containingFile?.let {
      providersMap[PARAMETER_GROUP] = createGroupEnumProvider(module, containingFile)
    }

    providersMap[PARAMETER_LOCALE] = createLocaleEnumProvider(module)

    providersMap[PARAMETER_HARDWARE_DEVICE] = createDeviceEnumProvider(module)

    providersMap[PARAMETER_WALLPAPER] = { Wallpaper.values().toList() }

    return PsiCallEnumSupportValuesProvider(providersMap)
  }
}

private fun createDeviceEnumProvider(module: Module): EnumValuesProvider = {
  val devicesEnumValueBuilder = DeviceEnumValueBuilder()
  val groupedDevices = getGroupedDevices(module)
  groupedDevices.forEach { (group, devices) ->
    when (group) {
      DeviceGroup.NEXUS,
      DeviceGroup.NEXUS_XL -> devices.forEach(devicesEnumValueBuilder::addPhone)
      DeviceGroup.CANONICAL_DEVICE -> {
        devices
          .firstOrNull { it.id == CanonicalDeviceType.SMALL_PHONE.id }
          ?.let { devicesEnumValueBuilder::addPhone }
        devices
          .firstOrNull { it.id == CanonicalDeviceType.MEDIUM_PHONE.id }
          ?.let { devicesEnumValueBuilder::addPhone }
        devices
          .firstOrNull { it.id == CanonicalDeviceType.MEDIUM_TABLET.id }
          ?.let { devicesEnumValueBuilder::addTablet }
      }
      DeviceGroup.NEXUS_TABLET -> devices.forEach(devicesEnumValueBuilder::addTablet)
      DeviceGroup.OTHER, // Group other with generic to guarantee all devices are available
      DeviceGroup.GENERIC -> devices.forEach(devicesEnumValueBuilder::addGeneric)
      DeviceGroup.WEAR -> devices.forEach(devicesEnumValueBuilder::addWear)
      DeviceGroup.TV -> devices.forEach(devicesEnumValueBuilder::addTv)
      DeviceGroup.AUTOMOTIVE -> devices.forEach(devicesEnumValueBuilder::addAuto)
      DeviceGroup.DESKTOP -> devices.forEach(devicesEnumValueBuilder::addDesktop)
      DeviceGroup.XR -> devices.forEach(devicesEnumValueBuilder::addXr)
      DeviceGroup.ADDITIONAL_DEVICE -> {}
    }
  }
  devicesEnumValueBuilder.includeDefaultsAndBuild()
}

/** Returns grouped devices from the DeviceManager. */
private fun getGroupedDevices(module: Module): Map<DeviceGroup, List<Device>> {
  val studioDevices = getSdkDevices(module)
  return groupDevices(studioDevices)
}

private fun createUiModeEnumProvider(): EnumValuesProvider = uiModeProvider@{
  return@uiModeProvider listOf(
    UiModeWithNightMaskEnumValue.UndefinedEnumValue,
    EnumValue.header(message("picker.preview.uimode.header.notnight")),
    UiModeWithNightMaskEnumValue.NormalNotNightEnumValue,
    *UiModeWithNightMaskEnumValue.uiModeNoNightValues.toTypedArray(),
    EnumValue.header(message("picker.preview.uimode.header.night")),
    UiModeWithNightMaskEnumValue.NormalNightEnumValue,
    *UiModeWithNightMaskEnumValue.uiModeNightValues.toTypedArray(),
  )
}

/**
 * Provides a list of targets within the appropriate range (by minimum sdk) and that are valid for
 * rendering.
 */
private fun createApiLevelEnumProvider(module: Module): EnumValuesProvider = {
  val configurationManager = ConfigurationManager.findExistingInstance(module)
  val minTargetSdk =
    StudioAndroidModuleInfo.getInstance(module)?.minSdkVersion?.apiLevel
      ?: AndroidVersion.VersionCodes.BASE
  configurationManager
    ?.targets
    ?.filter { it.isLayoutLibTarget && it.version.apiLevel >= minTargetSdk }
    ?.distinctBy { it.version.apiLevel }
    ?.map { target -> EnumValue.item(target.version.apiLevel.toString(), target.displayName()) }
    ?: emptyList()
}

private fun createGroupEnumProvider(
  module: Module,
  containingFile: VirtualFile,
): EnumValuesProvider = {
  runBlocking {
      AnnotationFilePreviewElementFinder.findPreviewElements(module.project, containingFile)
    }
    .mapNotNull { previewElement -> previewElement.displaySettings.group }
    .distinct()
    .map { group -> EnumValue.Companion.item(group) }
}

private fun createLocaleEnumProvider(module: Module): EnumValuesProvider = localesProvider@{
  val enumValueLocales = mutableListOf<EnumValue>(EnumValue.empty("Default (en-US)"))
  StudioResourceRepositoryManager.getInstance(module)
    ?.localesInProject
    ?.sortedWith(Locale.LANGUAGE_CODE_COMPARATOR)
    ?.forEach { locale ->
      locale.qualifier.full.nullize()?.let {
        enumValueLocales.add(EnumValue.Companion.item(it, locale.toLocaleId()))
      }
    }
  return@localesProvider enumValueLocales
}
