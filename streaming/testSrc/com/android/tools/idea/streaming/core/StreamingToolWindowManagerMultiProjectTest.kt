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
package com.android.tools.idea.streaming.core

import com.android.testutils.waitForCondition
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.android.tools.idea.streaming.FakeToolWindow
import com.android.tools.idea.streaming.MirroringManager
import com.android.tools.idea.streaming.MirroringState
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.createFakeToolWindow
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.content.ContentManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

/** Tests for [StreamingToolWindowManager] involving two projects. */
@RunsInEdt
class StreamingToolWindowManagerMultiProjectTest {

  private val agentRule = FakeScreenSharingAgentRule()
  private val project2Rule = ProjectRule()
  private val androidExecutorsRule = AndroidExecutorsRule(workerThreadExecutor = Executors.newCachedThreadPool())
  @get:Rule
  val ruleChain = RuleChain(agentRule, project2Rule, androidExecutorsRule, EdtRule())

  private val windowFactory: StreamingToolWindowFactory by lazy { StreamingToolWindowFactory() }
  private val toolWindow1: FakeToolWindow
      by lazy { createFakeToolWindow(windowFactory, RUNNING_DEVICES_TOOL_WINDOW_ID, project1, testRootDisposable) }
  private val toolWindow2: FakeToolWindow
      by lazy { createFakeToolWindow(windowFactory, RUNNING_DEVICES_TOOL_WINDOW_ID, project2, testRootDisposable) }
  private val contentManager1: ContentManager by lazy { toolWindow1.contentManager }
  private val contentManager2: ContentManager by lazy { toolWindow2.contentManager }

  private val deviceMirroringSettings: DeviceMirroringSettings by lazy { DeviceMirroringSettings.getInstance() }

  private val project1: Project get() = agentRule.project
  private val project2: Project get() = project2Rule.project
  private val mirroringManager1 get() = project1.service<MirroringManager>()
  private val mirroringManager2 get() = project2.service<MirroringManager>()
  private val deviceProvisioner1 get() = project1.service<DeviceProvisionerService>().deviceProvisioner
  private val deviceProvisioner2 get() = project2.service<DeviceProvisionerService>().deviceProvisioner

  private val testRootDisposable get() = agentRule.disposable

  @Before
  fun setUp() {
    deviceMirroringSettings.confirmationDialogShown = true
  }

  @After
  fun tearDown() {
    Disposer.dispose(toolWindow1.disposable)
    dispatchAllEventsInIdeEventQueue() // Finish asynchronous processing triggered by hiding the tool window.
    waitForCondition(2.seconds) { EmptyStatePanel.asyncActivityCount?.get() == 0 }
    deviceMirroringSettings.loadState(DeviceMirroringSettings()) // Reset device mirroring settings to defaults.
    service<DeviceClientRegistry>().clear()
  }

  @Test
  fun testCrossProjectMirroringSynchronization() {
    assertThat(toolWindow1.isVisible).isFalse()
    assertThat(contentManager1.contents).isEmpty()

    assertThat(mirroringManager1.mirroringHandles.value).isEmpty()

    agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    agentRule.connectDevice("Pixel 7", 33, Dimension(1080, 2400))
    waitForCondition(5.seconds) { deviceProvisioner1.devices.value.size == 2 }
    val pixel4Handle1 = deviceProvisioner1.devices.value.find { it.state.properties.model == "Pixel 4" }!!
    val pixel7Handle1 = deviceProvisioner1.devices.value.find { it.state.properties.model == "Pixel 7" }!!
    waitForCondition(2.seconds) { mirroringManager1.mirroringHandles.value.size == 2 }
    assertThat(mirroringManager1.mirroringHandles.value[pixel4Handle1]?.mirroringState).isEqualTo(MirroringState.INACTIVE)
    assertThat(mirroringManager1.mirroringHandles.value[pixel7Handle1]?.mirroringState).isEqualTo(MirroringState.INACTIVE)

    runBlocking { mirroringManager1.mirroringHandles.value[pixel4Handle1]?.toggleMirroring() }
    waitForCondition(2.seconds) { contentManager1.contents.size == 1 && contentManager1.contents[0].displayName != null }
    assertThat(contentManager1.contents[0].displayName).contains(pixel4Handle1.state.properties.model)
    assertThat(contentManager1.selectedContent?.displayName).contains(pixel4Handle1.state.properties.model)
    assertThat(mirroringManager1.mirroringHandles.value[pixel4Handle1]?.mirroringState).isEqualTo(MirroringState.ACTIVE)
    assertThat(mirroringManager1.mirroringHandles.value[pixel7Handle1]?.mirroringState).isEqualTo(MirroringState.INACTIVE)
    assertThat(toolWindow1.isVisible).isTrue()

    runBlocking { mirroringManager1.mirroringHandles.value[pixel7Handle1]?.toggleMirroring() }
    waitForCondition(2.seconds) { contentManager1.contents.size == 2 }
    assertThat(contentManager1.contents[1].displayName).contains(pixel7Handle1.state.properties.model)
    assertThat(contentManager1.selectedContent?.displayName).contains(pixel7Handle1.state.properties.model)
    assertThat(mirroringManager1.mirroringHandles.value[pixel7Handle1]?.mirroringState).isEqualTo(MirroringState.ACTIVE)
    assertThat(mirroringManager1.mirroringHandles.value[pixel4Handle1]?.mirroringState).isEqualTo(MirroringState.ACTIVE)

    assertThat(toolWindow2.isVisible).isFalse()
    assertThat(contentManager2.contents).isEmpty()

    toolWindow2.show()
    // The same devices are shown in the second tool window.
    waitForCondition(5.seconds) { deviceProvisioner2.devices.value.size == 2 }
    val pixel4Handle2 = deviceProvisioner2.devices.value.find { it.state.properties.model == "Pixel 4" }!!
    val pixel7Handle2 = deviceProvisioner2.devices.value.find { it.state.properties.model == "Pixel 7" }!!
    waitForCondition(2.seconds) { contentManager2.contents.size == 2 }
    assertThat(contentManager2.contents.map { it.displayName }).containsExactly("Pixel 4 API 30", "Pixel 7 API 33")
    waitForCondition(1.seconds) { mirroringManager2.mirroringHandles.value.size == 2 }
    assertThat(mirroringManager2.mirroringHandles.value[pixel7Handle2]?.mirroringState).isEqualTo(MirroringState.ACTIVE)
    assertThat(mirroringManager2.mirroringHandles.value[pixel4Handle2]?.mirroringState).isEqualTo(MirroringState.ACTIVE)

    // Stop mirroring of Pixel 4 from the second project and check that it is reflected in both projects.
    runBlocking { mirroringManager2.mirroringHandles.value[pixel4Handle2]?.toggleMirroring() }
    waitForCondition(2.seconds) { contentManager2.contents.size == 1 }
    assertThat(mirroringManager2.mirroringHandles.value[pixel4Handle2]?.mirroringState).isEqualTo(MirroringState.INACTIVE)
    assertThat(mirroringManager2.mirroringHandles.value[pixel7Handle2]?.mirroringState).isEqualTo(MirroringState.ACTIVE)
    waitForCondition(2.seconds) { contentManager1.contents.size == 1 }
    assertThat(mirroringManager1.mirroringHandles.value[pixel4Handle1]?.mirroringState).isEqualTo(MirroringState.INACTIVE)
    assertThat(mirroringManager1.mirroringHandles.value[pixel7Handle1]?.mirroringState).isEqualTo(MirroringState.ACTIVE)

    // Start mirroring of Pixel 4 from the first project and check that it is reflected in both projects.
    runBlocking { mirroringManager1.mirroringHandles.value[pixel4Handle1]?.toggleMirroring() }
    waitForCondition(2.seconds) { contentManager1.contents.size == 2 } // The same devices are shown in the second tool window.
    assertThat(mirroringManager1.mirroringHandles.value[pixel4Handle1]?.mirroringState).isEqualTo(MirroringState.ACTIVE)
    assertThat(mirroringManager1.mirroringHandles.value[pixel7Handle1]?.mirroringState).isEqualTo(MirroringState.ACTIVE)
    waitForCondition(2.seconds) { contentManager2.contents.size == 2 } // The same devices are shown in the second tool window.
    assertThat(mirroringManager2.mirroringHandles.value[pixel4Handle2]?.mirroringState).isEqualTo(MirroringState.ACTIVE)
    assertThat(mirroringManager2.mirroringHandles.value[pixel7Handle2]?.mirroringState).isEqualTo(MirroringState.ACTIVE)
  }
}
