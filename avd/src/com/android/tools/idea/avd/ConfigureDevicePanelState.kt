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
package com.android.tools.idea.avd

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.sdklib.ISystemImage
import com.android.tools.idea.adddevicedialog.TableSelectionState
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.collections.contains
import kotlin.math.max
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.toImmutableList

internal class ConfigureDevicePanelState(
  val device: VirtualDevice,
  skins: ImmutableCollection<Skin>,
  val deviceNameValidator: DeviceNameValidator,
  fileSystem: FileSystem = FileSystems.getDefault(),
  val maxCpuCoreCount: Int = max(1, Runtime.getRuntime().availableProcessors() / 2),
) {
  private var skins by mutableStateOf(skins)
  val systemImageTableSelectionState =
    object : TableSelectionState<ISystemImage> {
      override var selection: ISystemImage? by device::image
    }

  val storageGroupState = StorageGroupState(device, fileSystem)
  val emulatedPerformanceGroupState = EmulatedPerformanceGroupState(device)

  var isSystemImageTableSelectionValid by mutableStateOf(true)

  val isPreferredAbiValid by derivedStateOf {
    // Most changes to the system image selection will not affect whether the Preferred ABI is
    // valid, so use derivedStateOf to minimize unnecessary recomposition.
    device.preferredAbi == null ||
      systemImageTableSelectionState.selection == null ||
      systemImageTableSelectionState.selection.allAbiTypes().contains(device.preferredAbi)
  }

  val isValid
    get() =
      device.isValid &&
        deviceNameError == null &&
        isSystemImageTableSelectionValid &&
        isPreferredAbiValid

  fun hasPlayStore(): Boolean {
    val image = systemImageTableSelectionState.selection
    return if (image == null) false else device.hasPlayStore(image)
  }

  val deviceNameError by derivedStateOf { deviceNameValidator.validate(this.device.name) }

  fun setSystemImageSelection(systemImage: ISystemImage) {
    systemImageTableSelectionState.selection = systemImage
  }

  fun initDeviceSkins(path: Path) {
    val skin = getSkin(path)
    device.skin = skin
    device.defaultSkin = skin
  }

  fun initDefaultSkin(path: Path) {
    device.defaultSkin = getSkin(path)
  }

  fun setSkin(path: Path) {
    val skin = getSkin(path)
    device.skin = if (skin !in skins()) device.defaultSkin else skin
  }

  fun skins(): Iterable<Skin> =
    if (hasPlayStore()) setOf(NoSkin.INSTANCE, device.defaultSkin) else skins

  private fun getSkin(path: Path): Skin {
    var skin = skins.firstOrNull { it.path() == path }

    if (skin == null) {
      skin = DefaultSkin(path)
      skins = (skins + skin).sorted().toImmutableList()
    }

    return skin
  }
}
