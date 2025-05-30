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
package com.android.tools.idea.projectsystem.gradle

import org.junit.Test

class SdkIndexPsdWithoutVersionsTest : SdkIndexTestBase() {
  @Test
  fun `Snapshot used by PSD without versions`() {
    system.installation.addVmOption("-Didea.log.debug.categories=#com.android.tools.idea.gradle.structure.daemon.PsAnalyzerDaemon")
    system.installation.addVmOption("-Dgoogle.play.sdk.index.show.sdk.index.notes=true")
    system.installation.addVmOption("-Dgoogle.play.sdk.index.show.sdk.index.recommended.versions=false")
    system.installation.addVmOption("-Dgoogle.play.sdk.index.show.sdk.index.deprecation.issues=true")
    verifySdkIndexIsInitializedAndUsedWhen(
      showFunction = { studio, _ ->
        openAndClosePSD(studio)
      },
      beforeClose = {
        verifyPsdIssues(numErrors = 7, numWarnings = 4)
      },
      expectedIssues = listOf(
        // Error
        listOf(
          "**[Prevents app release in Google Play Console]** com.google.android.gms:play-services-ads-lite version 19.4.0 has been reported as problematic by its author and will block publishing of your app to Play Console.",
          "**Note:** As of June 30th 2023, this version is sunset. For more information, please visit https://developers.google.com/admob/android/deprecation.",
        ),
        // Error
        listOf(
          "**[Prevents app release in Google Play Console]** com.startapp:inapp-sdk version 3.9.1 has Permissions policy issues that will block publishing of your app to Play Console",
        ),
        // Error
        listOf(
          "**[Prevents app release in Google Play Console]** com.startapp:inapp-sdk version 3.9.1 has been reported as problematic by its author and will block publishing of your app to Play Console.",
          "**Note:** Critical issue has been identified which causes intensive battery consumption.",
        ),
        // Error
        listOf(
          "**[Prevents app release in Google Play Console]** com.startapp:inapp-sdk version 3.9.1 has been reported as outdated by its author and will block publishing of your app to Play Console",
        ),
        // Error
        listOf(
          "com.startapp:inapp-sdk version 3.9.1 contains unsafe unzipping patterns.",
        ),
        // Error
        listOf(
          "**[Prevents app release in Google Play Console]** com.google.android.play:core version 1.10.3 has been reported as problematic by its author and will block publishing of your app to Play Console.",
          // Yes, there is a space at the end, the message comes like that from the SDK Index snapshot
          "**Note:** Update your Play Core Maven dependency to an Android 14 compatible version! ",
          "Your current Play Core library is incompatible with targetSdkVersion 34 (Android 14), which introduces a " +
          "backwards-incompatible change to broadcast receivers to improve user security. As a reminder, from August 31, Google Play " +
          "requires all new app releases to target Android 14. Update to the latest Play Core library version dependency to avoid app " +
          "crashes: https://developer.android.com/guide/playcore#playcore-migration"
        ),
        // Error
        listOf(
          "Google Play Core (com.google.android.play:core) has been deprecated by its developer. Consider updating to an alternative SDK before publishing a new release.",
          "The developer has recommended these alternatives:",
          "```",
          " - Google Play Feature Delivery (com.google.android.play:feature-delivery)",
          " - Google Play Asset Delivery (com.google.android.play:asset-delivery)",
          " - Google Play In-App Updates (com.google.android.play:app-update)",
          " - Google Play In-App Reviews (com.google.android.play:review)",
          "```",
        ),
        // Warning
        listOf(
          "androidx.annotation:annotation version 1.0.0 has been reported as outdated by its author",
        ),
        // Warning
        listOf(
          "com.google.ads.interactivemedia.v3:interactivemedia version 3.18.2 has been reported as outdated by its author",
        ),
        // Warning
        listOf(
          "io.objectbox:objectbox-android version 2.5.1 has been reported as outdated by its author",
        ),
        // Warning
        listOf(
          "com.paypal.android.sdk:data-collector version 3.20.0 has Permissions policy issues that will block publishing of your app to Play Console in the future",
        ),
        // Info
        listOf(
          "com.google.android.gms:play-services-safetynet version 10.0.0 has an associated message from its author.",
          "**Note:** The SafetyNet Attestation API is being discontinued and replaced by the new Play Integrity API. Begin migration as soon as possible to avoid user disruption. The Play Integrity API includes all the integrity signals that SafetyNet Attestation offers and more, like Google Play licensing and better error messaging. Learn more and start migrating at https://developer.android.com/training/safetynet/deprecation-timeline",
        ),
        // Info
        listOf(
          "io.objectbox:objectbox-android version 2.5.1 has an associated message from its author.",
          "**Note:** This version has known issues that have been fixed in more recent versions. We also have made improvements to the API, performance and compatibility with devices. See https://docs.objectbox.io for details. Please upgrade to version 3.3.1 or newer (compatible with Android Plugin 3.4.0 or newer and Gradle 6.1 or newer). Note that newer versions are available on Maven Central.",
        ),
      )
    )
  }
}
