/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.platform

import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.SdkVersionInfo.getCodeName
import com.android.sdklib.internal.androidTarget.MockAddonTarget
import com.android.sdklib.internal.androidTarget.MockPlatformTarget
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AndroidVersionsInfoTest {

  /**
   * For versions without an Android target, the Build API should be the highest known stable API
   */
  @Test
  fun stableVersion() {
    val versionItem = AndroidVersionsInfo.VersionItem.fromStableVersion(OLDER_VERSION)
    assertEquals(OLDER_VERSION, versionItem.minApiLevel)
    assertEquals(OLDER_VERSION.toString(), versionItem.minApiLevelStr)
    assertEquals(NPW_CURRENT_VERSION, versionItem.buildApiLevel)
    assertEquals(NPW_CURRENT_VERSION.toString(), versionItem.buildApiLevelStr)
    assertEquals(NPW_CURRENT_VERSION, versionItem.targetApiLevel)
    assertEquals(NPW_CURRENT_VERSION.toString(), versionItem.targetApiLevelStr)
    assertNull(versionItem.androidTarget)
  }

  /** For preview Android target versions, the Build API should be the same as the preview */
  @Test
  fun previewVersion() {
    val version = AndroidVersion(FUTURE_VERSION - 1, "TEST_CODENAME")
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidVersion(version)
    assertEquals("API TEST_CODENAME Preview", versionItem.label)
    assertEquals(FUTURE_VERSION, versionItem.minApiLevel)
    assertEquals("TEST_CODENAME", versionItem.minApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.buildApiLevel)
    assertEquals("android-TEST_CODENAME", versionItem.buildApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.targetApiLevel)
    assertEquals("TEST_CODENAME", versionItem.targetApiLevelStr)
    assertNull(versionItem.androidTarget)
  }

  /**
   * For versions without an Android target, the Build API should be the highest known stable API
   */
  @Test
  fun stableAndroidTarget() {
    val androidTarget: MockPlatformTarget =
      object : MockPlatformTarget(OLDER_VERSION, 0) {
        override fun getVersion(): AndroidVersion = AndroidVersion(OLDER_VERSION)
      }
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidTarget(androidTarget)
    assertEquals(OLDER_VERSION, versionItem.minApiLevel)
    assertEquals(OLDER_VERSION.toString(), versionItem.minApiLevelStr)
    assertEquals(NPW_CURRENT_VERSION, versionItem.buildApiLevel)
    assertEquals(NPW_CURRENT_VERSION.toString(), versionItem.buildApiLevelStr)
    assertEquals(NPW_CURRENT_VERSION, versionItem.targetApiLevel)
    assertEquals(NPW_CURRENT_VERSION.toString(), versionItem.targetApiLevelStr)
    assertNull(versionItem.androidTarget)
  }

  /** For preview Android target versions, the Build API should be the same as the preview */
  @Test
  fun withPreviewAndroidTarget() {
    val androidTarget: MockPlatformTarget =
      object : MockPlatformTarget(FUTURE_VERSION, 0) {
        override fun getVersion(): AndroidVersion =
          AndroidVersion(FUTURE_VERSION - 1, "TEST_CODENAME")
      }
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidTarget(androidTarget)
    assertEquals("API TEST_CODENAME Preview", versionItem.label)
    assertEquals(FUTURE_VERSION, versionItem.minApiLevel)
    assertEquals("TEST_CODENAME", versionItem.minApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.buildApiLevel)
    assertEquals("android-TEST_CODENAME", versionItem.buildApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.targetApiLevel)
    assertEquals("TEST_CODENAME", versionItem.targetApiLevelStr)
    assertSame(androidTarget, versionItem.androidTarget)
  }

  /** For addon Android target versions, the Build API should be the same as the platform target */
  @Test
  fun withAddonAndroidTarget() {
    val baseTarget = MockPlatformTarget(26, 0)
    val projectTarget = MockAddonTarget("google", baseTarget, 1)
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidTarget(projectTarget)
    assertEquals("vendor 26:google:26", versionItem.label)
    assertEquals(26, versionItem.minApiLevel)
    assertEquals("26", versionItem.minApiLevelStr)
    assertEquals(26, versionItem.buildApiLevel)
    assertEquals("vendor 26:google:26", versionItem.buildApiLevelStr)
    assertEquals(26, versionItem.targetApiLevel)
    assertEquals("26", versionItem.targetApiLevelStr)
    assertSame(projectTarget, versionItem.androidTarget)
  }

  /** For future Android target versions, the Build API should be updated too */
  @Test
  @Ignore("Waiting for minor version support b/398938512")
  fun futureAndroidVersion() {
    val androidTarget = MockPlatformTarget(FUTURE_VERSION, 0)
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidTarget(androidTarget)
    assertEquals(FUTURE_VERSION, versionItem.minApiLevel)
    assertEquals(FUTURE_VERSION.toString(), versionItem.minApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.buildApiLevel)
    assertEquals(FUTURE_VERSION.toString(), versionItem.buildApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.targetApiLevel)
    assertEquals(FUTURE_VERSION.toString(), versionItem.targetApiLevelStr)
    assertNull(versionItem.androidTarget)
  }

  @Test
  fun previewTargetShouldReturnPreviewInLabel() {
    val androidVersion = AndroidVersion(NPW_CURRENT_VERSION, "PREVIEW_CODE_NAME")
    val androidTarget: IAndroidTarget = mock<IAndroidTarget>()
    whenever(androidTarget.version).thenReturn(androidVersion)
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidTarget(androidTarget)
    assertThat(versionItem.toString()).contains("PREVIEW_CODE_NAME")
  }

  @Test
  fun platformTargetShouldReturnAndroidDesertNameInLabel() {
    val androidVersion = AndroidVersion(NPW_CURRENT_VERSION, null)
    val androidTarget: IAndroidTarget = mock<IAndroidTarget>()
    whenever(androidTarget.version).thenReturn(androidVersion)
    whenever(androidTarget.isPlatform).thenReturn(true)
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidTarget(androidTarget)
    assertThat(versionItem.toString()).contains(getCodeName(NPW_CURRENT_VERSION))
  }

  /**
   * If an Android Target is not an Android Platform, then its an Android SDK add-on, and it should
   * be displayed using the add-on Vendor/Name values instead of the Android Target name (if add-on
   * description is missing).
   */
  @Test
  fun nonPlatformTargetShouldReturnAddonNameInLabel() {
    val androidVersion = AndroidVersion(NPW_CURRENT_VERSION, null /*codename*/)
    val androidTarget = mock<IAndroidTarget>()
    whenever(androidTarget.version).thenReturn(androidVersion)
    whenever(androidTarget.isPlatform).thenReturn(false)
    whenever(androidTarget.vendor).thenReturn("AddonVendor")
    whenever(androidTarget.name).thenReturn("AddonName")
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidTarget(androidTarget)
    assertThat(versionItem.toString())
      .isEqualTo(AndroidTargetHash.getAddonHashString("AddonVendor", "AddonName", androidVersion))
  }

  @Test
  fun `mobile format has no minimum sdk limit`() {
    val androidVersionsInfo = AndroidVersionsInfo { arrayOf(mockedPlatform(1000)) }
    androidVersionsInfo.loadLocalVersions()
    val targets = androidVersionsInfo.getKnownTargetVersions(FormFactor.MOBILE, 1)
    assertThat(targets.last().minApiLevel).isEqualTo(1000)
  }

  @Test
  fun `non-mobile formats have minimum sdk limit`() {
    val info = AndroidVersionsInfo { arrayOf(mockedPlatform(1000)) }
    info.loadLocalVersions()
    val nonMobileFormats = FormFactor.entries - FormFactor.MOBILE
    for (format in nonMobileFormats) {
      val targets = info.getKnownTargetVersions(format, 1)
      assertThat(targets.last().minApiLevel).isNotEqualTo(1000)
    }
  }

  private fun mockedPlatform(api: Int): IAndroidTarget =
    mock<IAndroidTarget> {
      on { version } doReturn AndroidVersion(api)
      on { isPlatform } doReturn true
    }
}

private val NPW_CURRENT_VERSION: Int = StudioFlags.NPW_COMPILE_SDK_VERSION.get()
private val OLDER_VERSION = NPW_CURRENT_VERSION - 1
private val FUTURE_VERSION = NPW_CURRENT_VERSION + 1
