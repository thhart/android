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
@file:Suppress("UnstableApiUsage")

package com.android.tools.idea.compose.gradle.preview

import com.android.testutils.delayUntilCondition
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.surface.SceneViewErrorsPanel
import com.android.tools.idea.common.surface.SceneViewPanel
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.android.tools.idea.common.surface.sceneview.SceneViewTopPanel
import com.android.tools.idea.common.util.ShowGroupUnderConditionWrapper
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.gradle.activateAndWaitForRender
import com.android.tools.idea.compose.gradle.waitForRender
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.compose.preview.waitForAllRefreshesToFinish
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.UiCheckInstance
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.scene.hasRenderErrors
import com.android.tools.idea.uibuilder.visual.visuallint.AtfAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.ButtonSizeAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.LongTextAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.TextFieldSizeAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService
import com.android.tools.preview.ComposePreviewElementInstance
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.ide.ui.IdeUiService
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndGet
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// SavePreviewInNewSize()
// EnableUiCheckAction(),
// AnimationInspectorAction(),
// EnableInteractiveAction(),
// DeployToDeviceAction()
// in wrappers
private const val EXPECTED_NUMBER_OF_ACTIONS = 5

class RenderErrorTest {

  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  private val project: Project
    get() = projectRule.project

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private val log = Logger.getInstance(RenderErrorTest::class.java)

  private lateinit var fakeUi: FakeUi
  private lateinit var composePreviewRepresentation: ComposePreviewRepresentation
  private lateinit var previewView: TestComposePreviewView

  private val panels: List<SceneViewPeerPanel>
    get() =
      fakeUi.findAllComponents<SceneViewPeerPanel>().also { panels ->
        panels.forEach { log.debug("Found SceneViewPeerPanel ${it.displayName}") }
        fakeUi.findAllComponents<SceneViewErrorsPanel>().forEach {
          log.debug("Found SceneViewErrorsPanel $it")
        }
      }

  private val VisualLintRenderIssue.location: String
    get() {
      val navigatable = this.components.firstOrNull()?.navigatable as? OpenFileDescriptor
      return "${navigatable?.file?.name}:${navigatable?.offset}"
    }

  @Before
  fun setup() {
    log.setLevel(LogLevel.ALL)
    Logger.getInstance(ComposePreviewRepresentation::class.java).setLevel(LogLevel.ALL)
    @Suppress("UnstableApiUsage")
    ToolWindowManager.getInstance(project)
      .registerToolWindow(RegisterToolWindowTask(ProblemsView.ID))

    val mainFile =
      project
        .guessProjectDir()!!
        .findFileByRelativePath(SimpleComposeAppPaths.APP_RENDER_ERROR.path)!!
    val psiMainFile = runReadAction { PsiManager.getInstance(project).findFile(mainFile)!! }

    previewView = TestComposePreviewView(fixture.testRootDisposable, project)
    composePreviewRepresentation =
      ComposePreviewRepresentation(psiMainFile, PreferredVisibility.SPLIT) { _, _, _, _, _, _ ->
        previewView
      }

    val visualLintInspections =
      arrayOf(
        ButtonSizeAnalyzerInspection(),
        LongTextAnalyzerInspection(),
        TextFieldSizeAnalyzerInspection(),
        AtfAnalyzerInspection(),
      )
    projectRule.fixture.enableInspections(*visualLintInspections)
    Disposer.register(fixture.testRootDisposable, composePreviewRepresentation)

    runBlocking {
      fakeUi =
        withContext(uiThread) {
          FakeUi(
              JPanel().apply {
                layout = BorderLayout()
                size = Dimension(1000, 800)
                add(previewView, BorderLayout.CENTER)
              },
              1.0,
              true,
            )
            .also { it.root.validate() }
        }
      composePreviewRepresentation.activateAndWaitForRender(fakeUi)
    }
  }

  @Test
  fun testSceneViewWithRenderErrors() =
    runBlocking(workerThread) {
      fakeUi.findComponent<SceneViewPanel>()?.setNoComposeHeadersForTests()
      startUiCheckForModel("PreviewWithRenderErrors")

      lateinit var sceneViewPanelWithErrors: SceneViewPeerPanel
      delayUntilCondition(delayPerIterationMs = 200, timeout = 30.seconds) {
        panels
          .singleOrNull { it.displayName == "Medium Phone - PreviewWithRenderErrors" }
          ?.takeIf { it.sceneView.hasRenderErrors() }
          ?.also { sceneViewPanelWithErrors = it } != null
      }

      val visibleErrorsPanel =
        TreeWalker(sceneViewPanelWithErrors)
          .descendants()
          .filterIsInstance<SceneViewErrorsPanel>()
          .single()
      assertFalse(visibleErrorsPanel.isVisible)

      val actions = sceneViewPanelWithErrors.getToolbarActions()
      assertEquals(EXPECTED_NUMBER_OF_ACTIONS, actions.size)

      // All actions should be invisible when there are render errors
      assertEquals(0, countVisibleActions(actions, sceneViewPanelWithErrors))
    }

  @Test
  fun testSceneViewWithoutRenderErrors() =
    runBlocking(workerThread) {
      fakeUi.findComponent<SceneViewPanel>()?.setNoComposeHeadersForTests()
      startUiCheckForModel("PreviewWithoutRenderErrors")

      lateinit var sceneViewPanelWithoutErrors: SceneViewPeerPanel

      delayUntilCondition(delayPerIterationMs = 200, timeout = 30.seconds) {
        panels
          .singleOrNull { it.displayName == "Medium Phone - PreviewWithoutRenderErrors" }
          ?.also { sceneViewPanelWithoutErrors = it } != null
      }

      assertFalse(sceneViewPanelWithoutErrors.sceneView.hasRenderErrors())

      val invisibleErrorsPanel =
        TreeWalker(sceneViewPanelWithoutErrors)
          .descendants()
          .filterIsInstance<SceneViewErrorsPanel>()
          .single()
      assertFalse(invisibleErrorsPanel.isVisible)

      val actions = sceneViewPanelWithoutErrors.getToolbarActions()
      assertEquals(EXPECTED_NUMBER_OF_ACTIONS, actions.size)

      // The animation preview action shouldn't be visible because the preview being used doesn't
      // contain animations, but the interactive, ui check and deploy to device actions should be
      // visible as there are no render errors.
      assertEquals(3, countVisibleActions(actions, sceneViewPanelWithoutErrors))
    }

  private suspend fun assertIssueIsGenerated(condition: (VisualLintRenderIssue) -> Boolean) =
    withTimeout(20.seconds) {
      visualLintRenderIssuesFlow()
        .distinctUntilChanged()
        .filter { it.any(condition) }
        .take(1)
        .collect()
    }

  @Test
  fun testAtfErrors() =
    runBlocking(workerThread) {
      startUiCheckForModel("PreviewWithContrastError")

      assertIssueIsGenerated { issue ->
        "${issue.summary} [${issue.location}]" ==
          "Insufficient text color contrast ratio [RenderError.kt:1667]"
      }
    }

  @Test
  fun testAtfErrorsOnSecondModel() =
    runBlocking(workerThread) {
      startUiCheckForModel("PreviewWithContrastErrorAgain")

      assertIssueIsGenerated { issue ->
        "${issue.summary} [${issue.location}]" ==
          "Insufficient text color contrast ratio [RenderError.kt:1817]"
      }
    }

  private suspend fun runVisualLintErrorsForModel(modelWithIssues: String) {
    startUiCheckForModel(modelWithIssues)

    assertIssueIsGenerated { issue ->
      issue.category == "Visual Lint Issue" &&
        (issue.components.firstOrNull()?.navigatable as? OpenFileDescriptor)?.file?.name ==
          "RenderError.kt"
    }

    stopUiCheck()
  }

  @Test
  fun testVisualLintErrorsForPreviewWithContrastError() = runBlocking {
    runVisualLintErrorsForModel("PreviewWithContrastError")
  }

  @Test
  fun testVisualLintErrorsForPreviewWithContrastErrorAgain() = runBlocking {
    runVisualLintErrorsForModel("PreviewWithContrastErrorAgain")
  }

  @Test
  fun testVisualLintErrorsForPreviewWithWideButton() = runBlocking {
    runVisualLintErrorsForModel("PreviewWithWideButton")
  }

  @Test
  fun testVisualLintErrorsForPreviewWithLongText() = runBlocking {
    runVisualLintErrorsForModel("PreviewWithLongText")
  }

  @Test
  fun testSwitchLayoutWithoutRenderErrors() =
    runBlocking(workerThread) {
      lateinit var sceneViewPanelWithoutErrors: SceneViewPeerPanel

      // We ensure we are starting from a non-Focus mode.
      assertTrue(composePreviewRepresentation.mode.value is PreviewMode.Default)

      // Render the Preview of the current mode.
      withContext(uiThread) {
        waitForRender(fakeUi.findAllComponents<SceneViewPeerPanel>().toSet(), timeout = 2.minutes)
        fakeUi.root.validate()
      }

      // We ensure there are no render errors for the preview we want to open in Focus mode
      val previewToOpenInFocusMode = "PreviewWithoutRenderErrors"
      delayUntilCondition(delayPerIterationMs = 200, timeout = 30.seconds) {
        panels
          .firstOrNull { it.displayName == previewToOpenInFocusMode }
          ?.also { sceneViewPanelWithoutErrors = it } != null
      }
      assertFalse(sceneViewPanelWithoutErrors.sceneView.hasRenderErrors())

      // We can now switch to Focus mode selecting [previewToOpenInFocusMode] and waiting for
      // render and refresh.
      setPreviewModeAndWaitForRefresh(previewToOpenInFocusMode) { PreviewMode.Focus(it) }

      // Wait to render the selected preview that is now in Focus mode.
      // Notice Focus Mode shows only one item per tab and we shouldn't have more than one item.
      withContext(uiThread) {
        waitForRender(setOf(sceneViewPanelWithoutErrors), timeout = 2.minutes)
        fakeUi.root.validate()
      }

      // Update the sceneViewPanel.
      delayUntilCondition(delayPerIterationMs = 200, timeout = 30.seconds) {
        panels
          .singleOrNull { it.displayName == previewToOpenInFocusMode }
          ?.also { sceneViewPanelWithoutErrors = it } != null
      }

      // Ensure we are in Focus mode
      assertTrue(composePreviewRepresentation.mode.value is PreviewMode.Focus)

      // The selected sceneViewPanel shouldn't have render errors.
      assertFalse(sceneViewPanelWithoutErrors.sceneView.hasRenderErrors())
      delayUntilCondition(delayPerIterationMs = 200, timeout = 30.seconds) {
        !composePreviewRepresentation.status().hasRenderErrors
      }
      assertFalse(composePreviewRepresentation.status().hasRenderErrors)
    }

  private fun countVisibleActions(
    actions: List<AnAction>,
    sceneViewPeerPanel: SceneViewPeerPanel,
  ): Int {
    val dataContext = runInEdtAndGet {
      IdeUiService.getInstance().createUiDataContext(sceneViewPeerPanel)
    }
    val visibleActions =
      Utils.expandActionGroup(
        DefaultActionGroup(actions),
        PresentationFactory(),
        dataContext,
        ActionPlaces.UNKNOWN,
        ActionUiKind.TOOLBAR,
      )
    return visibleActions.size
  }

  private fun SceneViewPeerPanel.getToolbarActions(): List<AnAction> {
    val showToolbarActionsActionGroup =
      (sceneViewTopPanel.components.filterIsInstance<ActionToolbarImpl>().single().actionGroup
          as DefaultActionGroup)
        .childActionsOrStubs
        .single() as SceneViewTopPanel.ShowActionGroupInPopupAction
    return showToolbarActionsActionGroup.actionGroup.childActionsOrStubs
      .filterIsInstance<ShowGroupUnderConditionWrapper>()
      .single()
      .getChildren(null)
      .toList()
  }

  private suspend fun startUiCheckForModel(model: String) {
    setPreviewModeAndWaitForRefresh(model) {
      PreviewMode.UiCheck(baseInstance = UiCheckInstance(it, isWearPreview = false))
    }

    // Once we enable Ui Check we need to render again since we are now showing the selected preview
    // with the different analyzers of Ui Check (for example screen sizes, colorblind check etc).
    withContext(uiThread) {
      waitForRender(fakeUi.findAllComponents<SceneViewPeerPanel>().toSet(), timeout = 2.minutes)
      fakeUi.root.validate()
    }
  }

  private suspend fun setPreviewModeAndWaitForRefresh(
    modelName: String,
    previewModeProvider: (ComposePreviewElementInstance<*>) -> PreviewMode,
  ) {
    lateinit var previewElement: ComposePreviewElementInstance<*>
    delayUntilCondition(250, timeout = 1.minutes) {
      previewView.mainSurface.models
        .firstOrNull { it.displaySettings.modelDisplayName.value == modelName }
        ?.dataProvider
        ?.previewElement()
        ?.also { previewElement = it } != null
    }
    val onRefreshCompletable = previewView.getOnRefreshCompletable()
    composePreviewRepresentation.setMode(previewModeProvider(previewElement))
    onRefreshCompletable.join()
  }

  private suspend fun stopUiCheck() {
    waitForAllRefreshesToFinish(1.minutes)

    val onRefreshCompletable = previewView.getOnRefreshCompletable()
    composePreviewRepresentation.setMode(PreviewMode.Default())
    try {
      withTimeout(1.minutes) { onRefreshCompletable.join() }
    } catch (_: TimeoutCancellationException) {}
  }

  private val IssueModel.visualLintRenderIssues: List<VisualLintRenderIssue>
    get() = issues.filterIsInstance<VisualLintRenderIssue>()

  private fun visualLintRenderIssuesFlow(): Flow<List<VisualLintRenderIssue>> = callbackFlow {
    val issueModel = VisualLintService.getInstance(project).issueModel

    val callback = IssueModel.IssueModelListener { trySend(issueModel.visualLintRenderIssues) }
    issueModel.addErrorModelListener(callback)
    trySend(issueModel.visualLintRenderIssues)
    awaitClose { issueModel.removeErrorModelListener(callback) }
  }
}
