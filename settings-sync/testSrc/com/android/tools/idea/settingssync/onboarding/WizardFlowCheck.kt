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

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.flags.junit.FlagRule
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.settingssync.FakeCommunicatorProvider
import com.android.tools.idea.settingssync.FakeRemoteCommunicator
import com.android.tools.idea.settingssync.PushResult
import com.google.common.truth.Truth.assertThat
import com.google.gct.login2.LoginFeature
import com.google.gct.login2.PreferredUser
import com.google.gct.login2.ui.onboarding.compose.GoogleSignInWizard
import com.google.gct.wizard.FakeController
import com.google.gct.wizard.WizardPage
import com.google.gct.wizard.WizardState
import com.intellij.openapi.components.SettingsCategory
import com.intellij.settingsSync.core.ServerState
import com.intellij.settingsSync.core.SettingsSyncLocalSettings
import com.intellij.settingsSync.core.SettingsSyncSettings
import com.intellij.settingsSync.core.SettingsSyncSettings.State
import com.intellij.settingsSync.core.communicator.SettingsSyncCommunicatorProvider
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class WizardFlowCheck {
  private val applicationRule = ApplicationRule()
  private val disposableRule = DisposableRule()
  private val composeTestRule = createStudioComposeTestRule()
  private val flagRule = FlagRule(StudioFlags.SETTINGS_SYNC_ENABLED, true)

  @get:Rule
  val rules =
    RuleChain.outerRule(EdtRule())
      .around(applicationRule)
      .around(flagRule)
      .around(disposableRule)
      .around(composeTestRule)

  private lateinit var communicator: FakeRemoteCommunicator
  private lateinit var communicatorProvider: FakeCommunicatorProvider

  private val step1 = EnableOrSkipStepPage()
  private val step3 = ChooseCategoriesStepPage()
  private val pages = listOf(step1, step3)

  @Before
  fun setup() {
    communicator = FakeRemoteCommunicator(USER_EMAIL)
    communicatorProvider = FakeCommunicatorProvider(communicator)

    ExtensionTestUtil.maskExtensions(
      SettingsSyncCommunicatorProvider.Companion.PROVIDER_EP,
      listOf(communicatorProvider),
      disposableRule.disposable,
      false,
    )

    ExtensionTestUtil.maskExtensions(
      LoginFeature.Companion.EP_NAME,
      listOf(feature),
      disposableRule.disposable,
      false,
    )

    initCommunicatorFromClean()

    // Force the initial sync state.
    SettingsSyncSettings.getInstance().syncEnabled = false
  }

  private fun initCommunicatorFromClean() {
    SettingsSyncLocalSettings.getInstance().providerCode = communicatorProvider.providerCode
    SettingsSyncLocalSettings.getInstance().userId = USER_EMAIL

    val status: ServerState = communicator.checkServerState()
    assertThat(status).isEqualTo(ServerState.FileNotExists)
  }

  private fun initWizard(
    pages: List<WizardPage>,
    state: WizardState,
    expectedInitialPage: WizardPage,
  ) {
    val controller = FakeController(pages, state)

    composeTestRule.setContent { controller.CurrentComposablePage() }
    assertThat(controller.currentPage).isEqualTo(expectedInitialPage)
  }

  // This covers the onboarding flow: step3 only
  @Test
  fun `test sync categories selection wizard page, default selection`() {
    // Prepare
    val wizardState =
      WizardState().apply {
        // Make sure we won't skip the page
        getOrCreateState { GoogleSignInWizard.SignInState() }
          .apply { signedInUser = PreferredUser.User(email = USER_EMAIL) }
      }
    initWizard(pages, wizardState, expectedInitialPage = step3)

    // Ensure status
    assertThat(SettingsSyncSettings.getInstance().syncEnabled).isFalse()

    // Action
    val pushResult: PushResult =
      communicator.awaitForPush {
        composeTestRule.onNodeWithText("Finish").assertIsDisplayed().performClick()
      }

    // Verify
    // 1. check locally stored data
    assertThat(SettingsSyncSettings.getInstance().syncEnabled).isTrue()
    assertThat(SettingsSyncSettings.getInstance().state)
      .isEqualTo(
        wizardState
          .getOrCreateState { SyncConfigurationState() }
          .syncCategoryStates
          .toLocallyStoredState(syncEnabled = true)
      )
    assertThat(SettingsSyncSettings.getInstance().state.disabledCategories).isEmpty()
    assertThat(SettingsSyncSettings.getInstance().state.disabledSubcategories).isEmpty()

    assertThat(SettingsSyncLocalSettings.getInstance().providerCode)
      .isEqualTo(communicatorProvider.providerCode)
    assertThat(SettingsSyncLocalSettings.getInstance().userId).isEqualTo(USER_EMAIL)
    assertThat(SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled).isFalse()
    // 2. check push result
    assertThat(communicator.checkServerState())
      .isEqualTo(ServerState.UpdateNeeded) // TODO: this doesn't seem right, confirming with JB.
    assertThat(pushResult.result.toString())
      .isEqualTo("SUCCESS") // TODO: check version id when available.
  }

  // This covers the onboarding flow: step3 only
  // @Test (b/410589934)
  fun `test sync categories selection wizard page, disable plugins`() {
    // Prepare
    val wizardState =
      WizardState().apply {
        // Make sure we won't skip the page
        getOrCreateState { GoogleSignInWizard.SignInState() }
          .apply { signedInUser = PreferredUser.User(email = USER_EMAIL) }
      }
    initWizard(pages, wizardState, expectedInitialPage = step3)

    // Ensure status
    assertThat(SettingsSyncSettings.getInstance().syncEnabled).isFalse()

    // Action
    composeTestRule.onNodeWithText("Plugins").assertIsDisplayed().performClick()
    val pushResult: PushResult =
      communicator.awaitForPush {
        composeTestRule.onNodeWithText("Finish").assertIsDisplayed().performClick()
      }

    // Verify
    // 1. check locally stored data
    assertThat(SettingsSyncSettings.getInstance().syncEnabled).isTrue()
    assertThat(SettingsSyncSettings.getInstance().state)
      .isEqualTo(
        wizardState
          .getOrCreateState { SyncConfigurationState() }
          .syncCategoryStates
          .toLocallyStoredState(syncEnabled = true)
      )
    assertThat(SettingsSyncSettings.getInstance().state.disabledCategories)
      .containsExactly(SettingsCategory.PLUGINS)
    assertThat(SettingsSyncSettings.getInstance().state.disabledSubcategories.values)
      .containsExactly(listOf("bundled"))

    assertThat(SettingsSyncLocalSettings.getInstance().providerCode)
      .isEqualTo(communicatorProvider.providerCode)
    assertThat(SettingsSyncLocalSettings.getInstance().userId).isEqualTo(USER_EMAIL)
    assertThat(SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled).isFalse()
    // 2. check push result
    assertThat(communicator.checkServerState())
      .isEqualTo(ServerState.UpdateNeeded) // TODO: this doesn't seem right, confirming with JB.
    assertThat(pushResult.result.toString())
      .isEqualTo("SUCCESS") // TODO: check version id when available.
  }

  // This covers the onboarding flow: step1 only
  @Test
  fun `can skip configuration`() {
    val activeSyncUser = "active_sync_user@test.com"
    // Prepare
    SettingsSyncSettings.getInstance().syncEnabled = true
    SettingsSyncLocalSettings.getInstance().userId = activeSyncUser
    val wizardState =
      WizardState().apply {
        // Make sure we won't skip the page
        getOrCreateState { GoogleSignInWizard.SignInState() }
          .apply { signedInUser = PreferredUser.User(email = USER_EMAIL) }
      }
    initWizard(pages, wizardState, expectedInitialPage = step1)

    // Action
    // 1. Select to stay with the current configuration.
    composeTestRule
      .onNodeWithText("Do not enable Backup & Sync for this account.", useUnmergedTree = true)
      .assertIsDisplayed()
      .performClick()
    // 2. Click "Finish" button.
    composeTestRule.onNodeWithText("Finish").assertIsDisplayed().performClick()

    // Verify
    // 1. check locally stored data
    assertThat(SettingsSyncLocalSettings.getInstance().userId).isEqualTo(activeSyncUser)
    assertThat(SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled).isFalse()
    // 2. check wizard state
    assertThat(wizardState.getOrCreateState { SyncConfigurationState() }.configurationOption)
      .isEqualTo(SyncConfigurationOption.USE_EXISTING_SETTINGS)
  }

  // This covers the onboarding flow: step1 -> step3
  @Test
  fun `do not skip configuration`() {
    val activeSyncUser = "active_sync_user@test.com"
    // Prepare
    SettingsSyncSettings.getInstance().syncEnabled = true
    SettingsSyncLocalSettings.getInstance().userId = activeSyncUser
    val wizardState =
      WizardState().apply {
        // Make sure we won't skip the page
        getOrCreateState { GoogleSignInWizard.SignInState() }
          .apply { signedInUser = PreferredUser.User(email = USER_EMAIL) }
      }
    initWizard(pages, wizardState, expectedInitialPage = step1)

    // Action
    // 1. Select to configure using the new account.
    composeTestRule
      .onNodeWithText(
        "stop syncing settings to the previously signed-in account ($activeSyncUser)",
        substring = true,
        useUnmergedTree = true,
      )
      .assertIsDisplayed()
      .performClick()
    // 2. Click "Next" button.
    composeTestRule.onNodeWithText("Next").assertIsDisplayed().performClick()

    // Verify
    assertThat(wizardState.getOrCreateState { SyncConfigurationState() }.configurationOption)
      .isEqualTo(SyncConfigurationOption.CONFIGURE_NEW_ACCOUNT)
  }

  private fun List<CheckboxNode>.toLocallyStoredState(syncEnabled: Boolean): State {
    return with(toSettingsSyncState(syncEnabled)) {
      State(
        disabledCategories,
        disabledSubcategories,
        migrationFromOldStorageChecked,
        this.syncEnabled,
      )
    }
  }
}
