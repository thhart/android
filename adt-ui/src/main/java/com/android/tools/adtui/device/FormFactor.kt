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
package com.android.tools.adtui.device

import com.android.sdklib.AndroidVersion.VersionCodes
import com.android.sdklib.AndroidVersion.VersionCodes.KITKAT_WATCH
import com.android.sdklib.AndroidVersion.VersionCodes.LOLLIPOP
import com.android.sdklib.AndroidVersion.VersionCodes.R
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API_AUTO
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API_TV
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API_WEAR
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API_XR
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
import com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API
import com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API_TV
import com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API_WEAR
import com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API_XR
import com.android.sdklib.SdkVersionInfo.RECOMMENDED_MIN_SDK_VERSION
import com.android.sdklib.SystemImageTags.ANDROID_TV_TAG
import com.android.sdklib.SystemImageTags.AUTOMOTIVE_PLAY_STORE_TAG
import com.android.sdklib.SystemImageTags.AUTOMOTIVE_TAG
import com.android.sdklib.SystemImageTags.DEFAULT_TAG
import com.android.sdklib.SystemImageTags.GOOGLE_APIS_TAG
import com.android.sdklib.SystemImageTags.GOOGLE_APIS_X86_TAG
import com.android.sdklib.SystemImageTags.GOOGLE_TV_TAG
import com.android.sdklib.SystemImageTags.WEAR_TAG
import com.android.sdklib.SystemImageTags.XR_TAG
import com.android.sdklib.repository.IdDisplay
import icons.StudioIllustrations.FormFactors
import javax.swing.Icon

/** Representations of all Android hardware devices we can target when building an app. */
enum class FormFactor(
  val id: String,
  val displayName: String,
  val defaultApi: Int,
  /**
   * The minimum API level supported by this form factor, as known at compile time. Only used if
   * offline; if we are online, we will query SDK Manager for available system images.
   */
  val minOfflineApiLevel: Int,
  /**
   * The maximum API level supported by this form factor, as known at compile time. Only used if
   * offline; if we are online, we will query SDK Manager for available system images.
   *
   * If the form factor [hasUpperLimitForMinimumSdkSelection], this value is also used to clamp the
   * known target versions, whether online or offline. That list is used to populate the minimum SDK
   * dropdown in new module wizards.
   */
  val maxOfflineApiLevel: Int,
  val icon: Icon,
  val largeIcon: Icon,
) {
  MOBILE(
    "Mobile",
    "Phone and Tablet",
    RECOMMENDED_MIN_SDK_VERSION,
    LOWEST_ACTIVE_API,
    HIGHEST_KNOWN_STABLE_API,
    FormFactors.MOBILE,
    FormFactors.MOBILE_LARGE,
  ),
  WEAR(
    "Wear",
    "Wear OS",
    R,
    LOWEST_ACTIVE_API_WEAR,
    HIGHEST_KNOWN_API_WEAR,
    FormFactors.WEAR,
    FormFactors.WEAR_LARGE,
  ),
  TV(
    "TV",
    "Television",
    LOLLIPOP,
    LOWEST_ACTIVE_API_TV,
    HIGHEST_KNOWN_API_TV,
    FormFactors.TV,
    FormFactors.TV_LARGE,
  ),
  AUTOMOTIVE(
    "Automotive",
    "Automotive",
    VersionCodes.P,
    VersionCodes.P,
    HIGHEST_KNOWN_API_AUTO,
    FormFactors.CAR,
    FormFactors.CAR_LARGE,
  ),
  XR(
    "XR",
    "XR",
    LOWEST_ACTIVE_API_XR,
    LOWEST_ACTIVE_API_XR,
    HIGHEST_KNOWN_API_XR,
    FormFactors.MOBILE,
    FormFactors.MOBILE_LARGE,
  );

  override fun toString(): String = displayName

  fun isSupported(targetSdkLevel: Int): Boolean =
    !(this == MOBILE && targetSdkLevel == KITKAT_WATCH)

  // We want to expose new SDKs when creating new mobile projects.
  val hasUpperLimitForMinimumSdkSelection: Boolean
    get() = this != MOBILE
}
