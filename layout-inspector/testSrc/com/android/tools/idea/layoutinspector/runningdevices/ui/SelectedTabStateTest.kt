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
package com.android.tools.idea.layoutinspector.runningdevices.ui

import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.FakeForegroundProcessDetection
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.model.VIEW4
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.runningdevices.actions.UiConfig
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.LayoutInspectorRenderer
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.StudioRendererPanel
import com.android.tools.idea.layoutinspector.runningdevices.verifyUiInjected
import com.android.tools.idea.layoutinspector.runningdevices.verifyUiRemoved
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.awt.Rectangle
import javax.swing.JPanel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

private class FakeLayoutInspectorRenderer : LayoutInspectorRenderer() {
  override var interceptClicks = false

  override fun dispose() {}
}

class SelectedTabStateTest {

  @get:Rule val edtRule = EdtRule()

  @get:Rule val displayViewRule = EmulatorViewRule()

  private lateinit var layoutInspector: LayoutInspector
  private lateinit var layoutInspectorRenderer: StudioRendererPanel
  private lateinit var emulatorView: EmulatorView

  @Before
  fun setUp() {
    val rectMap =
      mapOf(
        ROOT to Rectangle(0, 0, 100, 100),
        VIEW1 to Rectangle(0, 0, 100, 50),
        VIEW2 to Rectangle(0, 0, 100, 50),
        VIEW3 to Rectangle(0, 50, 100, 50),
        VIEW4 to Rectangle(40, 40, 20, 20),
      )

    val model =
      model(displayViewRule.disposable) {
        view(ROOT, rectMap[ROOT]) {
          view(VIEW1, rectMap[VIEW1]) { view(VIEW2, rectMap[VIEW2]) { image() } }
          view(VIEW3, rectMap[VIEW3])
          view(VIEW4, rectMap[VIEW4])
        }
      }

    val processModel = ProcessesModel(TestProcessDiscovery())
    val deviceModel = DeviceModel(displayViewRule.disposable, processModel)
    val notificationModel = NotificationModel(displayViewRule.project)
    emulatorView = displayViewRule.newEmulatorView()

    val coroutineScope = AndroidCoroutineScope(displayViewRule.disposable)
    val launcher =
      InspectorClientLauncher(
        processModel,
        emptyList(),
        displayViewRule.project,
        notificationModel,
        coroutineScope,
        displayViewRule.disposable,
        metrics = mock(),
      )

    val fakeForegroundProcessDetection = FakeForegroundProcessDetection()

    layoutInspector =
      LayoutInspector(
        coroutineScope = coroutineScope,
        processModel = processModel,
        deviceModel = deviceModel,
        foregroundProcessDetection = fakeForegroundProcessDetection,
        inspectorClientSettings = InspectorClientSettings(displayViewRule.project),
        launcher = launcher,
        layoutInspectorModel = model,
        notificationModel = notificationModel,
        treeSettings = FakeTreeSettings(),
      )

    layoutInspectorRenderer =
      StudioRendererPanel(
        disposable = displayViewRule.disposable,
        coroutineScope = AndroidCoroutineScope(displayViewRule.disposable),
        renderLogic = layoutInspector.renderLogic,
        renderModel = layoutInspector.renderModel,
        notificationModel = NotificationModel(displayViewRule.project),
        displayRectangleProvider = { Rectangle() },
        screenScaleProvider = { 1.0 },
        orientationQuadrantProvider = { 0 },
        navigateToSelectedViewOnDoubleClick = {},
      )
  }

  @After
  fun tearDown() {
    PropertiesComponent.getInstance().unsetValue(UI_CONFIGURATION_KEY)
  }

  @Test
  @RunsInEdt
  fun testHorizontalConfiguration() {
    testConfiguration(UiConfig.HORIZONTAL)
  }

  @Test
  @RunsInEdt
  fun testHorizontalSwapConfiguration() {
    testConfiguration(UiConfig.HORIZONTAL_SWAP)
  }

  @Test
  @RunsInEdt
  fun testVerticalConfiguration() {
    testConfiguration(UiConfig.VERTICAL)
  }

  @Test
  @RunsInEdt
  fun testVerticalSwapConfiguration() {
    testConfiguration(UiConfig.VERTICAL_SWAP)
  }

  @Test
  @RunsInEdt
  fun testLeftVerticalConfiguration() {
    testConfiguration(UiConfig.LEFT_VERTICAL)
  }

  @Test
  @RunsInEdt
  fun testLeftVerticalSwapConfiguration() {
    testConfiguration(UiConfig.LEFT_VERTICAL_SWAP)
  }

  @Test
  @RunsInEdt
  fun testRightVerticalConfiguration() {
    testConfiguration(UiConfig.RIGHT_VERTICAL)
  }

  @Test
  @RunsInEdt
  fun testRightVerticalSwapConfiguration() {
    testConfiguration(UiConfig.RIGHT_VERTICAL_SWAP)
  }

  @Test
  @RunsInEdt
  fun testUiConfigIsRestored() {
    val container = JPanel()
    val content = JPanel()
    container.add(content)
    val emulatorView = displayViewRule.newEmulatorView()

    val tabsComponents1 =
      TabComponents(displayViewRule.disposable, content, container, emulatorView)
    val selectedTabState1 =
      SelectedTabState(
        project = displayViewRule.project,
        deviceId = DeviceId.ofPhysicalDevice("tab"),
        tabComponents = tabsComponents1,
        layoutInspector = layoutInspector,
        rendererPanel = FakeLayoutInspectorRenderer(),
      )

    selectedTabState1.enableLayoutInspector(UiConfig.VERTICAL)

    verifyUiInjected<FakeLayoutInspectorRenderer>(
      UiConfig.VERTICAL,
      tabsComponents1.tabContentPanel,
      tabsComponents1.tabContentPanelContainer,
      emulatorView,
    )

    Disposer.dispose(tabsComponents1)

    verifyUiRemoved(
      tabsComponents1.tabContentPanel,
      tabsComponents1.tabContentPanelContainer,
      emulatorView,
    )

    val tabsComponents2 =
      TabComponents(displayViewRule.disposable, content, container, emulatorView)
    val selectedTabState2 =
      SelectedTabState(
        project = displayViewRule.project,
        deviceId = DeviceId.ofPhysicalDevice("tab"),
        tabComponents = tabsComponents2,
        layoutInspector = layoutInspector,
        rendererPanel = FakeLayoutInspectorRenderer(),
      )

    selectedTabState2.enableLayoutInspector()

    verifyUiInjected<FakeLayoutInspectorRenderer>(
      UiConfig.VERTICAL,
      tabsComponents2.tabContentPanel,
      tabsComponents2.tabContentPanelContainer,
      emulatorView,
    )
  }

  @Test
  @RunsInEdt
  fun testLayoutInspectorIsInDataContext() {
    val container = JPanel()
    val content = JPanel()
    container.add(content)

    val tabsComponents = TabComponents(displayViewRule.disposable, content, container, emulatorView)
    val selectedTabState =
      SelectedTabState(
        project = displayViewRule.project,
        deviceId = DeviceId.ofPhysicalDevice("tab"),
        tabComponents = tabsComponents,
        layoutInspector = layoutInspector,
        rendererPanel = FakeLayoutInspectorRenderer(),
      )

    val inspector1 =
      DataManager.getDataProvider(selectedTabState.rendererPanel)
        ?.getData(LAYOUT_INSPECTOR_DATA_KEY.name) as LayoutInspector
    assertThat(inspector1).isNotNull()

    Disposer.dispose(selectedTabState.tabComponents)

    val inspector2 =
      DataManager.getDataProvider(selectedTabState.rendererPanel)
        ?.getData(LAYOUT_INSPECTOR_DATA_KEY.name) as? LayoutInspector
    assertThat(inspector2).isNull()
  }

  private fun testConfiguration(uiConfig: UiConfig) {
    val selectedTabState = createSelectedTabState()

    selectedTabState.enableLayoutInspector(uiConfig)

    verifyUiInjected<FakeLayoutInspectorRenderer>(
      uiConfig,
      selectedTabState.tabComponents.tabContentPanel,
      selectedTabState.tabComponents.tabContentPanelContainer,
      emulatorView,
    )

    Disposer.dispose(selectedTabState.tabComponents)

    verifyUiRemoved(
      selectedTabState.tabComponents.tabContentPanel,
      selectedTabState.tabComponents.tabContentPanelContainer,
      emulatorView,
    )
  }

  private fun createSelectedTabState(): SelectedTabState {
    val container = JPanel()
    val content = JPanel()
    container.add(content)

    val tabsComponents = TabComponents(displayViewRule.disposable, content, container, emulatorView)
    return SelectedTabState(
      project = displayViewRule.project,
      deviceId = DeviceId.ofPhysicalDevice("tab"),
      tabComponents = tabsComponents,
      layoutInspector = layoutInspector,
      rendererPanel = FakeLayoutInspectorRenderer(),
    )
  }
}
