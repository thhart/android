/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.idea.apk.viewer.testing

import com.android.tools.apk.analyzer.AndroidApplicationInfo
import com.android.tools.apk.analyzer.ArchiveEntry
import com.android.tools.idea.apk.viewer.AndroidApplicationInfoProvider
import com.android.tools.idea.apk.viewer.ApkParser
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * A [AndroidApplicationInfo] for tests.
 */
internal class FakeAndroidApplicationInfoProvider(
  private val packageId: String = "com.app",
  private val versionName: String = "version",
  private val versionCode: Long = 1L,
  private val extractNativeLibs: Boolean = false
) : AndroidApplicationInfoProvider {
  override fun getApplicationInfo(apkParser: ApkParser, entry: ArchiveEntry): ListenableFuture<AndroidApplicationInfo> =
    Futures.immediateFuture(AndroidApplicationInfo(packageId, versionName, versionCode, extractNativeLibs))
}