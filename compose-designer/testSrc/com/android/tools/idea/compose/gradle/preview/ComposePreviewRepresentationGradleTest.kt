/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.gradle.preview

import com.android.testutils.ImageDiffUtil
import com.android.testutils.delayUntilCondition
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.compile.fast.CompilationResult
import com.android.tools.compile.fast.isSuccess
import com.android.tools.idea.common.surface.SceneViewPanel
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.gradle.ComposePreviewFakeUiGradleRule
import com.android.tools.idea.compose.gradle.getPsiFile
import com.android.tools.idea.compose.preview.ComposePreviewRefreshType
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.compose.preview.waitForAllRefreshesToFinish
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.editors.build.PsiCodeFileOutOfDateStatusReporter
import com.android.tools.idea.editors.build.PsiCodeFileUpToDateStatusRecorder
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.fast.FastPreviewTrackerManager
import com.android.tools.idea.editors.fast.TestFastPreviewTrackerManager
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.preview.DefaultRenderQualityPolicy
import com.android.tools.idea.preview.getDefaultPreviewQuality
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.testing.deleteLine
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.insertText
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.moveCaretLines
import com.android.tools.idea.testing.replaceText
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.problems.ProblemListener
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.awt.Point
import java.awt.Rectangle
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.seconds

class ComposePreviewRepresentationGradleTest {
  @get:Rule
  val projectRule =
    ComposePreviewFakeUiGradleRule(
      SIMPLE_COMPOSE_PROJECT_PATH,
      SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path,
    )
  private val project: Project
    get() = projectRule.project

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private val logger: Logger
    get() = projectRule.logger

  private val composePreviewRepresentation: ComposePreviewRepresentation
    get() = projectRule.composePreviewRepresentation

  private val psiMainFile: PsiFile
    get() = projectRule.psiMainFile

  private val previewView: TestComposePreviewView
    get() = projectRule.previewView

  private val fakeUi: FakeUi
    get() = projectRule.fakeUi

  /** Runs the [runnable]. The [runnable] is expected to trigger a fast preview refresh */
  private suspend fun runAndWaitForFastRefresh(runnable: () -> Unit) {
    return projectRule.runAndWaitForRefresh(anyRefreshStartTimeout = 30.seconds) {
      logger.info("runAndWaitForFastRefresh")
      val fastPreviewManager = FastPreviewManager.getInstance(project)

      assertTrue("FastPreviewManager must be enabled", fastPreviewManager.isEnabled)

      val compileDeferred = CompletableDeferred<CompilationResult>()
      val fastPreviewManagerListener =
        object : FastPreviewManager.Companion.FastPreviewManagerListener {
          override fun onCompilationStarted(files: Collection<PsiFile>) {
            logger.info("runAndWaitForFastRefresh: onCompilationStarted")
          }

          override fun onCompilationComplete(
            result: CompilationResult,
            files: Collection<PsiFile>,
          ) {
            logger.info("runAndWaitForFastRefresh: onCompilationComplete $result")
            // We expect a successful compilation, but some cancelled results can be received here
            // if for some reason a compilation is started while another one was already happening
            if (result.isSuccess) compileDeferred.complete(result)
          }
        }
      logger.info("runAndWaitForFastRefresh: waiting for smart mode")
      IndexingTestUtil.suspendUntilIndexesAreReady(project)
      logger.info("runAndWaitForFastRefresh: Waiting for any previous compilations to complete")
      delayUntilCondition(delayPerIterationMs = 500, timeout = 30.seconds) {
        !FastPreviewManager.getInstance(project).isCompiling
      }
      fastPreviewManager.addListener(fixture.testRootDisposable, fastPreviewManagerListener)
      logger.info("runAndWaitForFastRefresh: Executing runnable")
      runnable()
      logger.info("runAndWaitForFastRefresh: Runnable executed")
      val result = compileDeferred.await()
      logger.info("runAndWaitForFastRefresh: Compilation finished $result")
      assertTrue(result.isSuccess)
    }
  }

  @Test
  fun `panel renders correctly first time`() = runBlocking {
    withContext(uiThread) { fakeUi.layoutAndDispatchEvents() }
    delayUntilCondition(100, 5.seconds) {
      fakeUi.findAllComponents<SceneViewPeerPanel>().count() == 5
    }

    val output = fakeUi.render()

    assertEquals(
      """
        DefaultPreview
        TwoElementsPreview
        NavigatablePreview
        OnlyATextNavigation
        MyPreviewWithInline
      """
        .trimIndent(),
      fakeUi
        .findAllComponents<SceneViewPeerPanel>()
        .filter { it.isShowing }
        .joinToString("\n") { it.displayName },
    )

    val defaultPreviewSceneViewPeerPanel =
      fakeUi.findComponent<SceneViewPeerPanel> { it.displayName == "DefaultPreview" }!!
    val defaultPreviewRender =
      output.getSubimage(
        defaultPreviewSceneViewPeerPanel.x,
        defaultPreviewSceneViewPeerPanel.y,
        defaultPreviewSceneViewPeerPanel.width,
        defaultPreviewSceneViewPeerPanel.height,
      )
    ImageDiffUtil.assertImageSimilar(
      Paths.get(
        "${fixture.testDataPath}/${SIMPLE_COMPOSE_PROJECT_PATH}/defaultRender-withPanel.png"
      ),
      defaultPreviewRender,
      10.0,
      20,
    )
  }

  @Test
  fun `changes to code are reflected in the preview when rebuilding`() = runBlocking {
    // This test only makes sense when fast preview is disabled, as some build related logic is
    // being tested.
    FastPreviewManager.getInstance(project).disable()
    val firstRender = projectRule.findSceneViewRenderWithName("TwoElementsPreview")

    // Make a change to the preview
    runWriteActionAndWait {
      fixture.openFileInEditor(psiMainFile.virtualFile)
      fixture.moveCaret("Text(\"Hello 2\")|")
      fixture.editor.executeAndSave { insertText("\nText(\"Hello 3\")\n") }
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    projectRule.buildAndRefresh(failOnTimeout = false)

    val secondRender = projectRule.findSceneViewRenderWithName("TwoElementsPreview")
    assertTrue(
      "Second image expected at least 10% higher but were second=${secondRender.height} first=${firstRender.height}",
      secondRender.height > (firstRender.height * 1.10),
    )
    try {
      ImageDiffUtil.assertImageSimilar("testImage", firstRender, secondRender, 10.0, 20)
      fail("First render and second render are expected to be different")
    } catch (_: AssertionError) {}

    // Restore to the initial state and verify
    runWriteActionAndWait {
      fixture.editor.executeAndSave { replaceText("Text(\"Hello 3\")\n", "") }
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    projectRule.buildAndRefresh(failOnTimeout = false)

    val thirdRender = projectRule.findSceneViewRenderWithName("TwoElementsPreview")
    ImageDiffUtil.assertImageSimilar("testImage", firstRender, thirdRender, 10.0, 20)
  }

  @Test
  fun `removing preview makes it disappear without rebuilding`() = runBlocking {
    // This test only makes sense when fast preview is disabled, as what's being tested is that
    // annotation changes take effect without rebuilding nor recompiling
    FastPreviewManager.getInstance(project).disable()
    projectRule.runAndWaitForRefresh(failOnTimeout = false) {
      // Remove the @Preview from the NavigatablePreview
      runWriteActionAndWait {
        fixture.openFileInEditor(psiMainFile.virtualFile)
        fixture.moveCaret("NavigatablePreview|")
        // Move to the line with the annotation
        fixture.editor.moveCaretLines(-2)
        fixture.editor.executeAndSave { fixture.editor.deleteLine() }
        PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()
      }
    }
    withContext(uiThread) { fakeUi.findComponent<SceneViewPanel>()?.doLayout() }
    projectRule.validate()

    delayUntilCondition(100, 2.seconds) { fakeUi.findAllComponents<SceneViewPeerPanel>().size == 4 }

    val allSceneViewPeerPanels = fakeUi.findAllComponents<SceneViewPeerPanel>()
    val sceneViewPeerPanelsText =
      allSceneViewPeerPanels.joinToString(", ") { "${it.displayName} showing=${it.isShowing}" }

    assertEquals(
      "Unexpected visible panels. Current is '$sceneViewPeerPanelsText'",
      listOf("DefaultPreview", "MyPreviewWithInline", "OnlyATextNavigation", "TwoElementsPreview"),
      allSceneViewPeerPanels.filter { it.isShowing }.map { it.displayName }.sorted(),
    )
  }

  @Test
  fun `adding preview makes it appear without rebuilding`() = runBlocking {
    // This test only makes sense when fast preview is disabled, as what's being tested is that
    // annotation changes take effect without rebuilding nor recompiling
    FastPreviewManager.getInstance(project).disable()

    projectRule.runAndWaitForRefresh(failOnTimeout = false) {
      // Add another @Preview to NavigatablePreview
      runWriteActionAndWait {
        fixture.openFileInEditor(psiMainFile.virtualFile)
        fixture.moveCaret("NavigatablePreview|")
        // Move to the line with the annotation
        fixture.editor.moveCaretLines(-2)
        // Add another preview annotation
        fixture.editor.executeAndSave { insertText("@Preview(name = \"additional preview\")\n") }
        PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()
      }
    }
    fakeUi.findComponent<SceneViewPanel>()!!.setNoComposeHeadersForTests()
    withContext(uiThread) { fakeUi.findComponent<SceneViewPanel>()?.doLayout() }
    projectRule.validate()

    withContext(uiThread) { fakeUi.layoutAndDispatchEvents() }
    delayUntilCondition(100, 10.seconds) {
      fakeUi.findAllComponents<SceneViewPeerPanel>().size == 6
    }

    val allSceneViewPeerPanels = fakeUi.findAllComponents<SceneViewPeerPanel>()
    val sceneViewPeerPanelsText =
      allSceneViewPeerPanels.joinToString(", ") { "${it.displayName} showing=${it.isShowing}" }

    assertEquals(
      "Unexpected visible panels. Current is '$sceneViewPeerPanelsText'",
      listOf(
        "DefaultPreview",
        "MyPreviewWithInline",
        "NavigatablePreview",
        "NavigatablePreview - additional preview",
        "OnlyATextNavigation",
        "TwoElementsPreview",
      ),
      allSceneViewPeerPanels.filter { it.isShowing }.map { it.displayName }.sorted(),
    )
  }

  @Test
  fun `MultiPreview annotation changes are reflected in the previews without rebuilding`() =
    runBlocking {
      // This test only makes sense when fast preview is disabled, as what's being tested is that
      // annotation changes take effect without rebuilding nor recompiling
      FastPreviewManager.getInstance(project).disable()
      val otherPreviewsFile = getPsiFile(project, SimpleComposeAppPaths.APP_OTHER_PREVIEWS.path)

      projectRule.runAndWaitForRefresh(failOnTimeout = false) {
        // Add an annotation class annotated with Preview in OtherPreviews.kt
        runWriteActionAndWait {
          fixture.openFileInEditor(otherPreviewsFile.virtualFile)
          fixture.moveCaret("|@Preview")
          fixture.editor.executeAndSave {
            insertText("@Preview\nannotation class MyAnnotation\n\n")
          }
          PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
          FileDocumentManager.getInstance().saveAllDocuments()
        }
        // Annotate DefaultPreview with the new MultiPreview annotation class
        runWriteActionAndWait {
          fixture.openFileInEditor(psiMainFile.virtualFile)
          fixture.moveCaret("|@Preview")
          fixture.editor.executeAndSave { insertText("@MyAnnotation\n") }
          PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
          FileDocumentManager.getInstance().saveAllDocuments()
        }
      }

      fakeUi.findComponent<SceneViewPanel>()!!.setNoComposeHeadersForTests()

      projectRule.validate()

      withContext(uiThread) { fakeUi.layoutAndDispatchEvents() }
      delayUntilCondition(100, 5.seconds) {
        fakeUi.findAllComponents<SceneViewPeerPanel>().count() == 6
      }

      assertEquals(
        """
        DefaultPreview - MyAnnotation 1
        DefaultPreview
        TwoElementsPreview
        NavigatablePreview
        OnlyATextNavigation
        MyPreviewWithInline
      """
          .trimIndent(),
        fakeUi
          .findAllComponents<SceneViewPeerPanel>()
          .filter { it.isShowing }
          .map { it.displayName }
          .joinToString("\n"),
      )

      projectRule.runAndWaitForRefresh(failOnTimeout = false) {
        // Modify the Preview annotating MyAnnotation
        runWriteActionAndWait {
          fixture.openFileInEditor(otherPreviewsFile.virtualFile)
          fixture.moveCaret("@Preview|")
          fixture.editor.executeAndSave { insertText("(name = \"newName\")") }
          PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
          FileDocumentManager.getInstance().saveAllDocuments()
        }
      }

      projectRule.validate()
      withContext(uiThread) { fakeUi.layoutAndDispatchEvents() }
      delayUntilCondition(100, 5.seconds) {
        "DefaultPreview - newName" ==
          fakeUi.findAllComponents<SceneViewPeerPanel>().first().displayName
      }

      assertEquals(
        """
        DefaultPreview - newName
        DefaultPreview
        TwoElementsPreview
        NavigatablePreview
        OnlyATextNavigation
        MyPreviewWithInline
      """
          .trimIndent(),
        fakeUi
          .findAllComponents<SceneViewPeerPanel>()
          .filter { it.isShowing }
          .map { it.displayName }
          .joinToString("\n"),
      )
    }

  @Test
  fun `build clean triggers needs refresh`() {
    GradleBuildInvoker.getInstance(projectRule.project).cleanProject().get(2, TimeUnit.SECONDS)
    assertTrue(composePreviewRepresentation.status().isOutOfDate)
  }

  @Test
  fun `refresh returns completed exceptionally if ComposePreviewRepresentation is disposed`() {
    var refreshDeferred = runBlocking {
      val completableDeferred = CompletableDeferred<Unit>()
      composePreviewRepresentation.requestRefreshForTest(
        ComposePreviewRefreshType.QUICK,
        completableDeferred = completableDeferred,
      )
      completableDeferred
    }
    assertNotNull(refreshDeferred)

    runInEdtAndWait { Disposer.dispose(composePreviewRepresentation) }
    refreshDeferred = runBlocking {
      val completableDeferred = CompletableDeferred<Unit>()
      composePreviewRepresentation.requestRefreshForTest(
        ComposePreviewRefreshType.QUICK,
        completableDeferred = completableDeferred,
      )
      completableDeferred
    }
    // Verify that it is completed exceptionally
    assertTrue(refreshDeferred.isCompleted)
    assertNotNull(refreshDeferred.getCompletionExceptionOrNull())
  }

  @Test
  fun `fast preview request`() = runBlocking {
    val requestCompleted = CompletableDeferred<Unit>()
    val testTracker = TestFastPreviewTrackerManager { requestCompleted.complete(Unit) }

    project.replaceService(
      FastPreviewTrackerManager::class.java,
      testTracker,
      fixture.testRootDisposable,
    )

    runAndWaitForFastRefresh {
      runWriteActionAndWait {
        projectRule.fixture.openFileInEditor(psiMainFile.virtualFile)
        projectRule.fixture.moveCaret("Text(\"Hello 2\")|")
        projectRule.fixture.editor.executeAndSave {
          insertText("\nText(\"added during test execution\")")
        }
        PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()
      }
    }

    runBlocking {
      withTimeout(TimeUnit.SECONDS.toMillis(10)) {
        // Wait for the tracking request to be submitted
        requestCompleted.await()
      }
    }

    assertEquals(
      "compilationSucceeded (compilationDurationMs=>0, compiledFiles=1, refreshTime=>0)",
      testTracker.logOutput(),
    )
  }

  @Test
  fun `refresh cancellation`() = runBlocking {
    // Wait for an "infinite" refresh to start
    projectRule.waitForAnyRefreshToStart(30.seconds, ComposePreviewRefreshType.NORMAL) {
      runWriteActionAndWait {
        projectRule.fixture.openFileInEditor(psiMainFile.virtualFile)
        projectRule.fixture.moveCaret("|Text(\"Hello 2\")")
        projectRule.fixture.editor.executeAndSave { insertText("while(true) { }\n") }
        PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()
      }
    }

    assertFails { waitForAllRefreshesToFinish(10.seconds) }

    // Delete the infinite loop, triggering a new refresh
    runWriteActionAndWait {
      projectRule.fixture.moveCaret("while(true) { }|")
      fixture.editor.executeAndSave { fixture.editor.deleteLine() }
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    // First refresh should get cancelled and then second one should complete
    waitForAllRefreshesToFinish(30.seconds)
  }

  @Test
  fun `fast preview fixing syntax error triggers compilation`() = runBlocking {
    runAndWaitForFastRefresh {
      // Mark the file as invalid so the fast preview triggers a compilation when the problems
      // disappear
      PsiCodeFileUpToDateStatusRecorder.getInstance(project)
        .markFileAsOutOfDateForTests(psiMainFile)
      project.messageBus
        .syncPublisher(ProblemListener.TOPIC)
        .problemsDisappeared(psiMainFile.virtualFile)
    }
  }

  @Test
  fun `file modification triggers refresh on other active preview representations`() = runBlocking {
    val otherPreviewsFile = getPsiFile(project, SimpleComposeAppPaths.APP_OTHER_PREVIEWS.path)

    // Modifying otherPreviewsFile should trigger a refresh in the main file representation.
    // (and in any active one)
    runAndWaitForFastRefresh {
      runWriteActionAndWait {
        fixture.openFileInEditor(otherPreviewsFile.virtualFile)
        // Add a MultiPreview annotation that won't be used
        fixture.moveCaret("|@Preview")
        fixture.editor.executeAndSave { insertText("@Preview\nannotation class MyAnnotation\n\n") }
        PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()
      }
    }
  }

  /**
   * When a kotlin file is updated while a preview is inactive, this will not trigger a refresh, but
   * then refresh does happen when we come back to the preview.
   */
  @Test
  fun `file modification don't refresh inactive representations but do refresh on reactivation`() =
    runBlocking {
      val otherPreviewsFile = getPsiFile(project, SimpleComposeAppPaths.APP_OTHER_PREVIEWS.path)

      composePreviewRepresentation.onDeactivate()

      // Modifying otherPreviewsFile should not trigger a refresh in the main file representation
      // (nor in any inactive one).
      assertFalse(composePreviewRepresentation.isInvalid())
      assertFails {
        projectRule.runAndWaitForRefresh {
          runWriteActionAndWait {
            fixture.openFileInEditor(otherPreviewsFile.virtualFile)
            fixture.moveCaret("|@Preview")
            fixture.editor.executeAndSave { insertText("\n\nfun testMethod() {}\n\n") }
            PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
            FileDocumentManager.getInstance().saveAllDocuments()
          }
        }
      }

      // Change above should have marked that file as outdated
      delayUntilCondition(delayPerIterationMs = 500, timeout = 5.seconds) {
        PsiCodeFileOutOfDateStatusReporter.getInstance(project).outOfDateFiles.isNotEmpty()
      }

      // When reactivating, a full refresh should happen due to the modification of
      // otherPreviewsFile during the inactive time of this representation.
      runAndWaitForFastRefresh { composePreviewRepresentation.onActivate() }
      assertFalse(composePreviewRepresentation.isInvalid())
    }

  @Test
  fun `reactivation don't trigger full refresh when nothing has changed`() = runBlocking {
    composePreviewRepresentation.onDeactivate()
    assertFalse(composePreviewRepresentation.isInvalid())
    assertFails { projectRule.runAndWaitForRefresh { composePreviewRepresentation.onActivate() } }
    assertFalse(composePreviewRepresentation.isInvalid())
  }

  @Test
  fun `background indicator is not created if project is disposed`() {
    var backgroundIndicatorsCreated = 0
    composePreviewRepresentation.updateRefreshIndicatorCallbackForTests {
      backgroundIndicatorsCreated++
    }
    runBlocking {
      val completableDeferred = CompletableDeferred<Unit>()
      composePreviewRepresentation.requestRefreshForTest(
        ComposePreviewRefreshType.QUICK,
        completableDeferred = completableDeferred,
      )
      completableDeferred.await()
    }
    assertEquals(1, backgroundIndicatorsCreated)

    runInEdtAndWait { Disposer.dispose(composePreviewRepresentation, false) }
    runBlocking {
      val completableDeferred = CompletableDeferred<Unit>()
      composePreviewRepresentation.requestRefreshForTest(
        ComposePreviewRefreshType.QUICK,
        completableDeferred = completableDeferred,
      )
      assertTrue(completableDeferred.isCompleted)
      // Verify that it is completed exceptionally
      assertNotNull(completableDeferred.getCompletionExceptionOrNull())
    }
    // Verify that no additional background indicator is created
    assertEquals(1, backgroundIndicatorsCreated)
  }

  @Test
  fun `test zoom-to-fit when enable Focus mode`() = runBlocking {
    val defaultModeScale = 1.5
    previewView.mainSurface.zoomController.setScale(defaultModeScale)

    val previewElements =
      previewView.mainSurface.models.mapNotNull { it.dataProvider?.previewElement() }
    val selectedPreviewElement = previewElements.single { "DefaultPreview" in it.methodFqn }

    // Ensures that the current mode is Default and the zoom is not a zoom-to-fit scale.
    assertTrue(composePreviewRepresentation.mode.value is PreviewMode.Default)
    assertTrue(previewView.mainSurface.zoomController.canZoomToFit())

    // Start Focus mode.
    projectRule.runAndWaitForRefresh(allRefreshesFinishTimeout = 35.seconds) {
      composePreviewRepresentation.setMode(PreviewMode.Focus(selectedPreviewElement))
    }
    delayUntilCondition(delayPerIterationMs = 500, timeout = 10.seconds) {
      composePreviewRepresentation.mode.value is PreviewMode.Focus
    }
    // FakeUi doesn't call the designSurface.resize() callback needed to call the [notifyZoomToFit]
    // when the render has finished. We need then to do notify the resize manually.
    previewView.mainSurface.notifyComponentResizedForTest()
    delayUntilCondition(delayPerIterationMs = 250) {
      !previewView.mainSurface.zoomController.canZoomToFit()
    }

    // Focus mode should be in zoom-to-fit scale.
    assertTrue(composePreviewRepresentation.mode.value is PreviewMode.Focus)
    assertFalse(previewView.mainSurface.zoomController.canZoomToFit())

    // Change the scale of the surface.
    previewView.mainSurface.zoomController.zoom(ZoomType.OUT)
    previewView.mainSurface.zoomController.zoom(ZoomType.OUT)

    // Stop Focus mode.
    switchToDefaultMode()

    // Check that the surface of the default mode is unchanged.
    delayUntilCondition(delayPerIterationMs = 250) {
      previewView.mainSurface.zoomController.scale == defaultModeScale
    }
    assertTrue(composePreviewRepresentation.mode.value is PreviewMode.Default)
    assertEquals(defaultModeScale, previewView.mainSurface.zoomController.scale, 0.001)
  }

  @Test
  fun `test zoom-to-fit when switch Focus tabs`() = runBlocking {
    val defaultModeScale = 1.5
    previewView.mainSurface.zoomController.setScale(defaultModeScale)

    val previewElements =
      previewView.mainSurface.models.mapNotNull { it.dataProvider?.previewElement() }
    val firstPreviewElement = previewElements.single { "DefaultPreview" in it.methodFqn }
    val secondSelectedPreviewElement =
      previewElements.single { "MyPreviewWithInline" in it.methodFqn }

    // Ensures that the current mode is Default and the zoom is not a zoom-to-fit scale.
    assertTrue(composePreviewRepresentation.mode.value is PreviewMode.Default)
    assertTrue(previewView.mainSurface.zoomController.canZoomToFit())

    // Start Focus mode with the first tab.
    projectRule.runAndWaitForRefresh(allRefreshesFinishTimeout = 35.seconds) {
      composePreviewRepresentation.setMode(PreviewMode.Focus(firstPreviewElement))
    }
    delayUntilCondition(delayPerIterationMs = 500, timeout = 10.seconds) {
      composePreviewRepresentation.mode.value is PreviewMode.Focus
    }
    previewView.mainSurface.notifyComponentResizedForTest()
    delayUntilCondition(delayPerIterationMs = 250) {
      !previewView.mainSurface.zoomController.canZoomToFit()
    }

    // Focus mode first tab should be in zoom-to-fit scale.
    assertTrue(composePreviewRepresentation.mode.value is PreviewMode.Focus)
    assertFalse(previewView.mainSurface.zoomController.canZoomToFit())

    // Change the scale of the first tab.
    previewView.mainSurface.zoomController.zoom(ZoomType.IN)
    previewView.mainSurface.zoomController.zoom(ZoomType.IN)
    previewView.mainSurface.zoomController.zoom(ZoomType.IN)

    // Because of the zoom change now we would be able to zoom-to-fit again.
    assertTrue(previewView.mainSurface.zoomController.canZoomToFit())

    // Switch to second tab of Focus mode.
    projectRule.runAndWaitForRefresh(allRefreshesFinishTimeout = 35.seconds) {
      composePreviewRepresentation.setMode(PreviewMode.Focus(secondSelectedPreviewElement))
    }

    previewView.mainSurface.notifyComponentResizedForTest()
    previewView.mainSurface.notifyLayoutCreatedForTest()
    delayUntilCondition(delayPerIterationMs = 250) {
      !previewView.mainSurface.zoomController.canZoomToFit()
    }

    // Focus mode second tab should be in zoom-to-fit scale.
    assertTrue(composePreviewRepresentation.mode.value is PreviewMode.Focus)
    assertFalse(previewView.mainSurface.zoomController.canZoomToFit())
  }

  @Test
  fun `test zoom-to-fit when enable Animation inspection mode`() = runBlocking {
    val defaultModeScale = 1.5
    previewView.mainSurface.zoomController.setScale(defaultModeScale)

    val previewElements =
      previewView.mainSurface.models.mapNotNull { it.dataProvider?.previewElement() }
    val selectedPreviewElement = previewElements.single { "DefaultPreview" in it.methodFqn }

    // Ensures that the current mode is Default and the zoom is not a zoom-to-fit scale.
    assertTrue(composePreviewRepresentation.mode.value is PreviewMode.Default)
    assertTrue(previewView.mainSurface.zoomController.canZoomToFit())

    // Start Animation inspection.
    projectRule.runAndWaitForRefresh(allRefreshesFinishTimeout = 35.seconds) {
      composePreviewRepresentation.setMode(PreviewMode.AnimationInspection(selectedPreviewElement))
    }
    delayUntilCondition(delayPerIterationMs = 500, timeout = 10.seconds) {
      composePreviewRepresentation.mode.value is PreviewMode.AnimationInspection
    }
    // FakeUi doesn't call the designSurface.resize() callback needed to call the [notifyZoomToFit]
    // when the render has finished. We need then to do notify the resize manually.
    previewView.mainSurface.notifyComponentResizedForTest()
    delayUntilCondition(delayPerIterationMs = 250) {
      !previewView.mainSurface.zoomController.canZoomToFit()
    }

    // Animation inspection mode should be in zoom-to-fit scale.
    assertTrue(composePreviewRepresentation.mode.value is PreviewMode.AnimationInspection)
    assertFalse(previewView.mainSurface.zoomController.canZoomToFit())

    // Change the scale of the surface.
    previewView.mainSurface.zoomController.zoom(ZoomType.OUT)
    previewView.mainSurface.zoomController.zoom(ZoomType.OUT)

    // Stop Animation inspection mode.
    switchToDefaultMode()

    // Check that the surface of the default mode is unchanged.
    delayUntilCondition(delayPerIterationMs = 250) {
      previewView.mainSurface.zoomController.scale == defaultModeScale
    }
    assertTrue(composePreviewRepresentation.mode.value is PreviewMode.Default)
    assertEquals(defaultModeScale, previewView.mainSurface.zoomController.scale, 0.001)
  }

  @Test
  fun `test zoom-to-fit on when enable Interactive Preview mode`() = runBlocking {
    val defaultModeScale = 1.5
    previewView.mainSurface.zoomController.setScale(defaultModeScale)

    val previewElements =
      previewView.mainSurface.models.mapNotNull { it.dataProvider?.previewElement() }
    val selectedPreviewElement = previewElements.single { "DefaultPreview" in it.methodFqn }

    // Ensures that the current mode is Default and the zoom is not a zoom-to-fit scale.
    assertTrue(composePreviewRepresentation.mode.value is PreviewMode.Default)
    assertTrue(previewView.mainSurface.zoomController.canZoomToFit())

    // Start Interactive Preview mode.
    startInteractiveMode(selectedPreviewElement)

    // Interactive Preview mode should be in zoom-to-fit scale.
    assertTrue(composePreviewRepresentation.mode.value is PreviewMode.Interactive)
    assertFalse(previewView.mainSurface.zoomController.canZoomToFit())

    // Change the scale of the surface.
    previewView.mainSurface.zoomController.zoom(ZoomType.OUT)
    previewView.mainSurface.zoomController.zoom(ZoomType.OUT)

    // Stop Interactive Preview mode.
    switchToDefaultMode()

    // Check that the surface of the default mode is unchanged.
    delayUntilCondition(delayPerIterationMs = 250) {
      previewView.mainSurface.zoomController.scale == defaultModeScale
    }
    assertTrue(composePreviewRepresentation.mode.value is PreviewMode.Default)
    assertEquals(defaultModeScale, previewView.mainSurface.zoomController.scale, 0.001)

    // Start Interactive Preview mode.
    startInteractiveMode(selectedPreviewElement)

    // Interactive Preview again and should be in zoom-to-fit scale again.
    assertTrue(composePreviewRepresentation.mode.value is PreviewMode.Interactive)
    assertFalse(previewView.mainSurface.zoomController.canZoomToFit())
  }

  private suspend fun startInteractiveMode(
    selectedPreviewElement: PsiComposePreviewElementInstance
  ) {
    projectRule.runAndWaitForRefresh(allRefreshesFinishTimeout = 35.seconds) {
      composePreviewRepresentation.setMode(PreviewMode.Interactive(selectedPreviewElement))
    }
    delayUntilCondition(delayPerIterationMs = 500, timeout = 10.seconds) {
      composePreviewRepresentation.mode.value is PreviewMode.Interactive
    }
    delayUntilCondition(delayPerIterationMs = 250) {
      !previewView.mainSurface.zoomController.canZoomToFit()
    }
  }

  private suspend fun switchToDefaultMode() {
    projectRule.runAndWaitForRefresh(
      expectedRefreshType = ComposePreviewRefreshType.NORMAL,
      failOnTimeout = true,
    ) {
      composePreviewRepresentation.setMode(PreviewMode.Default())
    }
  }

  @Test
  fun testPreviewRenderQuality_zoom() {
    projectRule.runWithRenderQualityEnabled {
      var firstPreview: SceneViewPeerPanel? = null
      // zoom and center to one preview (quality change refresh should happen)
      projectRule.runAndWaitForRefresh(
        expectedRefreshType = ComposePreviewRefreshType.QUALITY,
        failOnTimeout = false,
      ) {
        firstPreview = fakeUi.findAllComponents<SceneViewPeerPanel>().first { it.isShowing }
        firstPreview!!.sceneView.let {
          previewView.mainSurface.zoomAndCenter(
            it,
            Rectangle(Point(it.x, it.y), it.scaledContentSize),
          )
        }
      }
      withContext(uiThread) { fakeUi.root.validate() }
      // Default quality should have been used
      assertEquals(
        getDefaultPreviewQuality(),
        (fakeUi
            .findAllComponents<SceneViewPeerPanel>()
            .first { it.displayName == firstPreview!!.displayName }
            .sceneView
            .sceneManager as LayoutlibSceneManager)
          .lastRenderQuality,
      )

      // Now zoom out a lot to go below the threshold (quality change refresh should happen)
      projectRule.runAndWaitForRefresh(
        expectedRefreshType = ComposePreviewRefreshType.QUALITY,
        failOnTimeout = false,
      ) {
        previewView.mainSurface.zoomController.setScale(
          DefaultRenderQualityPolicy.scaleVisibilityThreshold / 2.0
        )
      }
      withContext(uiThread) { fakeUi.root.validate() }
      assertEquals(
        DefaultRenderQualityPolicy.lowestQuality,
        (fakeUi
            .findAllComponents<SceneViewPeerPanel>()
            .first { it.displayName == firstPreview!!.displayName }
            .sceneView
            .sceneManager as LayoutlibSceneManager)
          .lastRenderQuality,
      )

      // Now zoom in a little bit to go above the threshold (quality change refresh should happen)
      projectRule.runAndWaitForRefresh(
        expectedRefreshType = ComposePreviewRefreshType.QUALITY,
        failOnTimeout = false,
      ) {
        previewView.mainSurface.zoomController.setScale(
          DefaultRenderQualityPolicy.scaleVisibilityThreshold * 2.0
        )
      }
      withContext(uiThread) { fakeUi.root.validate() }
      assertEquals(
        DefaultRenderQualityPolicy.scaleVisibilityThreshold * 2,
        (fakeUi
            .findAllComponents<SceneViewPeerPanel>()
            .first { it.displayName == firstPreview!!.displayName }
            .sceneView
            .sceneManager as LayoutlibSceneManager)
          .lastRenderQuality,
      )
    }
  }

  @Test
  fun testPreviewRenderQuality_lifecycle() {
    projectRule.runWithRenderQualityEnabled {
      var firstPreview: SceneViewPeerPanel? = null
      // zoom and center to one preview (quality change refresh should happen)
      projectRule.runAndWaitForRefresh(
        expectedRefreshType = ComposePreviewRefreshType.QUALITY,
        failOnTimeout = false,
      ) {
        firstPreview = fakeUi.findAllComponents<SceneViewPeerPanel>().first { it.isShowing }
        firstPreview!!.sceneView.let {
          previewView.mainSurface.zoomAndCenter(
            it,
            Rectangle(Point(it.x, it.y), it.scaledContentSize),
          )
        }
      }
      withContext(uiThread) { fakeUi.root.validate() }
      // Default quality should have been used
      assertEquals(
        getDefaultPreviewQuality(),
        (fakeUi
            .findAllComponents<SceneViewPeerPanel>()
            .first { it.displayName == firstPreview!!.displayName }
            .sceneView
            .sceneManager as LayoutlibSceneManager)
          .lastRenderQuality,
      )

      // Now deactivate the preview representation (quality change refresh should happen)
      projectRule.runAndWaitForRefresh(
        expectedRefreshType = ComposePreviewRefreshType.QUALITY,
        failOnTimeout = false,
      ) {
        composePreviewRepresentation.onDeactivate()
      }
      withContext(uiThread) { fakeUi.root.validate() }
      assertEquals(
        DefaultRenderQualityPolicy.lowestQuality,
        (fakeUi
            .findAllComponents<SceneViewPeerPanel>()
            .first { it.displayName == firstPreview!!.displayName }
            .sceneView
            .sceneManager as LayoutlibSceneManager)
          .lastRenderQuality,
      )

      // Now reactivate the preview representation (quality change refresh should happen)
      projectRule.runAndWaitForRefresh(
        expectedRefreshType = ComposePreviewRefreshType.QUALITY,
        failOnTimeout = false,
      ) {
        composePreviewRepresentation.onActivate()
      }
      withContext(uiThread) { fakeUi.root.validate() }
      assertEquals(
        getDefaultPreviewQuality(),
        (fakeUi
            .findAllComponents<SceneViewPeerPanel>()
            .first { it.displayName == firstPreview!!.displayName }
            .sceneView
            .sceneManager as LayoutlibSceneManager)
          .lastRenderQuality,
      )
    }
  }
}
