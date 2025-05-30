/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.compose.preview.actions

import android.view.View
import android.view.ViewGroup
import com.android.flags.junit.FlagRule
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.Density
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.configurations.Configuration
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.model.NlDataProvider
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.LayoutScannerConfiguration
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.compose.preview.ConfigurationResizeListener
import com.android.tools.idea.compose.preview.PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.compose.preview.analytics.ComposeResizeToolingUsageTracker
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.modes.CommonPreviewModeManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneRenderConfiguration
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.ResizeComposePreviewEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestActionEvent
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.intellij.lang.annotations.Language
import org.jetbrains.android.compose.ComposeProjectRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class RevertToOriginalSizeActionTest {

  @get:Rule val projectRule = ComposeProjectRule(AndroidProjectRule.withAndroidModel())

  @get:Rule val studioFlagRule = FlagRule(StudioFlags.COMPOSE_PREVIEW_RESIZING, true)

  @get:Rule val usageTrackerRule = UsageTrackerRule()

  private val sceneManager: LayoutlibSceneManager = mock()
  private val designSurface: NlDesignSurface = mock()
  private val model: NlModel = mock()
  private lateinit var modeManager: PreviewModeManager

  @Before
  fun setup() {
    modeManager = CommonPreviewModeManager()
    `when`(designSurface.sceneManagers).thenReturn(listOf(sceneManager))
    `when`(sceneManager.model).thenReturn(model)
  }

  @After
  fun tearDown() {
    Disposer.dispose(sceneManager)
  }

  @Test
  fun `update isVisible and isEnabled when scene manager is resized and mode is Focus`() {
    `when`(sceneManager.isResized).thenReturn(true)
    modeManager.setMode(PreviewMode.Focus(mock()))
    val dataContext = createDataContext()
    val event = TestActionEvent.createTestEvent(dataContext)
    val action = RevertToOriginalSizeAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun `update isVisible and isEnabled when preview mode is not focus`() {
    `when`(sceneManager.isResized).thenReturn(true)
    val dataContext = createDataContext()
    modeManager.setMode(PreviewMode.AnimationInspection(mock()))
    val event = TestActionEvent.createTestEvent(dataContext)

    val action = RevertToOriginalSizeAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun `update isVisible and isEnabled when resizing is disabled`() {
    `when`(sceneManager.isResized).thenReturn(true)
    val dataContext = createDataContext()
    val event = TestActionEvent.createTestEvent(dataContext)
    val action = RevertToOriginalSizeAction()

    StudioFlags.COMPOSE_PREVIEW_RESIZING.override(false)
    action.update(event)
    StudioFlags.COMPOSE_PREVIEW_RESIZING.clearOverride()

    assertThat(event.presentation.isVisible).isFalse()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun `update isVisible and isEnabled when scene manager is null`() {
    val dataContext = createDataContext()
    val event = TestActionEvent.createTestEvent(dataContext)
    val action = RevertToOriginalSizeAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun `update isVisible and isEnabled when scene manager is not resized`() {
    `when`(sceneManager.isResized).thenReturn(false)
    val dataContext = createDataContext()
    val event = TestActionEvent.createTestEvent(dataContext)
    val action = RevertToOriginalSizeAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun `send statistics on revert`() = runTest {
    ComposeResizeToolingUsageTracker.forceEnableForUnitTests = true

    val configuration =
      Configuration.create(
        ConfigurationManager.getOrCreateInstance(projectRule.module),
        FolderConfiguration.createDefault(),
      )
    val previewElement = createPreviewElement()
    modeManager.setMode(PreviewMode.Focus(previewElement))

    `when`(model.configuration).thenReturn(configuration)
    `when`(model.dataProvider)
      .thenReturn(
        object : NlDataProvider(PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE) {
          override fun getData(dataId: String) =
            previewElement.takeIf { dataId == PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name }
        }
      )
    `when`(sceneManager.isResized).thenReturn(true)
    val sceneRenderConfiguration =
      LayoutlibSceneRenderConfiguration(model, mock(), LayoutScannerConfiguration.DISABLED)
    `when`(sceneManager.sceneRenderConfiguration).thenReturn(sceneRenderConfiguration)

    val dataContext = createDataContext()

    val event = TestActionEvent.createTestEvent(dataContext)
    val action = RevertToOriginalSizeAction()

    action.actionPerformed(event)

    val eventAnalytics =
      usageTrackerRule.usages
        .find {
          it.studioEvent.resizeComposePreviewEvent.eventType ==
            ResizeComposePreviewEvent.EventType.RESIZE_REVERTED
        }!!
        .studioEvent
        .resizeComposePreviewEvent
    assertThat(eventAnalytics.resizeMode)
      .isEqualTo(ResizeComposePreviewEvent.ResizeMode.COMPOSABLE_RESIZE)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `triggers configuration listener, updates sizes and set clearOverrideRenderSize to true`() =
    runTest {
      val widthDp = 100
      val heightDp = 200

      val previewElement = createPreviewElement(widthDp, heightDp)
      `when`(model.dataProvider)
        .thenReturn(
          object : NlDataProvider(PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE) {
            override fun getData(dataId: String) =
              previewElement.takeIf { dataId == PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name }
          }
        )
      modeManager.setMode(PreviewMode.Focus(previewElement))

      val configuration =
        Configuration.create(
          ConfigurationManager.getOrCreateInstance(projectRule.module),
          FolderConfiguration.createDefault(),
        )
      `when`(model.configuration).thenReturn(configuration)
      configuration.addListener(
        ConfigurationResizeListener(
          sceneManager,
          configuration,
          StandardTestDispatcher(testScheduler),
        )
      )
      advanceUntilIdle()

      `when`(sceneManager.isResized).thenReturn(true)
      val sceneRenderConfiguration =
        LayoutlibSceneRenderConfiguration(model, mock(), LayoutScannerConfiguration.DISABLED)
      `when`(sceneManager.sceneRenderConfiguration).thenReturn(sceneRenderConfiguration)
      whenever(sceneManager.executeInRenderSessionAsync(any(), any(), any())).then {
        val callback = it.getArgument(0, Runnable::class.java)
        callback.run()
        CompletableFuture.completedFuture(null)
      }
      val testView = org.mockito.kotlin.mock<View>()
      val layoutParams = ViewGroup.LayoutParams(500, 600)
      whenever(testView.layoutParams).thenReturn(layoutParams)
      whenever(sceneManager.viewObject).thenReturn(testView)

      val dataContext = createDataContext()

      val event = TestActionEvent.createTestEvent(dataContext)
      val action = RevertToOriginalSizeAction()

      action.actionPerformed(event)

      val widthInPx =
        (widthDp * (1.0 * configuration.density.dpiValue / Density.DEFAULT_DENSITY)).toInt()
      val heightPx =
        (heightDp * (1.0 * configuration.density.dpiValue / Density.DEFAULT_DENSITY)).toInt()

      advanceUntilIdle()

      verify(sceneManager).requestRenderWithNewSize(widthInPx, heightPx)
      assertEquals(widthInPx, testView.layoutParams.width) // Check width changed by reflection
      assertEquals(heightPx, testView.layoutParams.height) // Check height changed by reflection
      assertThat(sceneRenderConfiguration.clearOverrideRenderSize).isTrue()
    }

  private fun createDataContext(): DataContext {
    val builder =
      SimpleDataContext.builder()
        .add(DESIGN_SURFACE, designSurface)
        .add(PreviewModeManager.KEY, modeManager)
        .add(CommonDataKeys.PROJECT, projectRule.project)
    return builder.build()
  }

  private suspend fun createPreviewElement(
    widthDp: Int? = null,
    heightDp: Int? = null,
  ): PsiComposePreviewElementInstance {
    val width = widthDp?.let { "widthDp = $it, " } ?: ""
    val height = heightDp?.let { "heightDp = $it, " } ?: ""
    @Language("kotlin")
    val composeTest =
      projectRule.fixture.addFileToProject(
        "src/Test.kt",
        """
            import androidx.compose.ui.tooling.preview.Preview
            import androidx.compose.runtime.Composable

            @Preview(name = "MyPreview", $width$height)
            @Composable
            fun MyComposable() {
            }
            """
          .trimIndent(),
      )

    return AnnotationFilePreviewElementFinder.findPreviewElements(
        projectRule.project,
        composeTest.virtualFile,
      )
      .first() as PsiComposePreviewElementInstance
  }
}
