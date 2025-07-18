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
package com.android.tools.idea.compose.gradle.fast

import com.android.testutils.delayUntilCondition
import com.android.tools.compile.fast.CompilationResult
import com.android.tools.compile.fast.isSuccess
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.gradle.renderer.renderPreviewElement
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.concurrency.AndroidDispatchers.diskIoThread
import com.android.tools.idea.editors.fast.FastPreviewConfiguration
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.fast.toFileNameSet
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.run.deployment.liveedit.LiveEditCompiler
import com.android.tools.idea.run.deployment.liveedit.LiveEditCompilerInput
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException
import com.android.tools.idea.run.deployment.liveedit.MutableIrClassCache
import com.android.tools.idea.run.deployment.liveedit.PsiState
import com.android.tools.idea.run.deployment.liveedit.tokens.ApplicationLiveEditServices
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.replaceText
import com.android.tools.idea.testing.virtualFile
import com.android.tools.idea.util.toIoFile
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FastPreviewManagerGradleTest {
  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  private lateinit var psiMainFile: PsiFile
  private lateinit var fastPreviewManager: FastPreviewManager

  @Before
  fun setUp() {
    FastPreviewConfiguration.getInstance().isEnabled = true
    val mainFile =
      projectRule.project
        .guessProjectDir()!!
        .findFileByRelativePath(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path)!!
    psiMainFile = runReadAction { PsiManager.getInstance(projectRule.project).findFile(mainFile)!! }
    fastPreviewManager = FastPreviewManager.getInstance(projectRule.project)
    runInEdtAndWait {
      projectRule.buildAndAssertIsSuccessful()
      projectRule.fixture.openFileInEditor(mainFile)
      runWriteAction {
        WriteCommandAction.runWriteCommandAction(projectRule.project) {
          // Delete the reference to PreviewInOtherFile since it's a top level function not
          // supported
          // by the embedded compiler (b/201728545) and it's not used by the tests.
          projectRule.fixture.editor.replaceText("PreviewInOtherFile()", "")
        }
      }
      projectRule.fixture.moveCaret("Text(\"Hello 2\")|")
      projectRule.fixture.type("\n")
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue() // Consume editor events
    }
  }

  @After
  fun tearDown() {
    runBlocking { fastPreviewManager.stopAllDaemons().join() }
  }

  @Test
  fun testSingleFileCompileSuccessfully() {
    typeAndSaveDocument("Text(\"Hello 3\")\n")
    runBlocking {
      val (result, _) =
        fastPreviewManager.compileRequest(psiMainFile, BuildTargetReference.from(psiMainFile)!!)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
    }
  }

  @Test
  fun testFastPreviewDoesNotInlineRIds() {
    // Force the use of final resource ids
    File(projectRule.project.guessProjectDir()!!.toIoFile(), "gradle.properties")
      .appendText("android.nonFinalResIds=false")
    projectRule.requestSyncAndWait()
    projectRule.buildAndAssertIsSuccessful()

    typeAndSaveDocument("Text(stringResource(R.string.greeting))\n")
    runBlocking {
      val (result, outputPath) =
        fastPreviewManager.compileRequest(psiMainFile, BuildTargetReference.from(psiMainFile)!!)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)

      // Decompile the generated Preview code
      val decompiledOutput =
        withContext(diskIoThread) {
          File(outputPath).toPath().toFileNameSet().joinToString("\n") {
            try {
              val reader =
                ClassReader(
                  Files.readAllBytes(Paths.get(outputPath, "google/simpleapplication/$it"))
                )
              val outputTrace = StringWriter()
              val classOutputWriter =
                TraceClassVisitor(ClassWriter(ClassWriter.COMPUTE_MAXS), PrintWriter(outputTrace))
              reader.accept(classOutputWriter, 0)
              outputTrace.toString()
            } catch (t: Throwable) {
              ""
            }
          }
        }

      val k1StringResourceCallPattern =
        Regex(
          "LDC (\\d+)\n\\s+ALOAD (\\d+)\n\\s+(?:ICONST_0|BIPUSH (\\d+))\n\\s+INVOKESTATIC androidx/compose/ui/res/StringResources_androidKt\\.stringResource",
          RegexOption.MULTILINE,
        )
      val k2StringResourceCallPattern =
        Regex(
          "GETSTATIC google/simpleapplication/R.string\\.greeting : I\n\\s+ALOAD (\\d+)\n\\s+(?:ICONST_0|BIPUSH (\\d+))\n\\s+INVOKESTATIC androidx/compose/ui/res/StringResources_androidKt\\.stringResource",
          RegexOption.MULTILINE,
        )
      val matches =
        sequenceOf(k1StringResourceCallPattern, k2StringResourceCallPattern).flatMap {
          it.findAll(decompiledOutput)
        }
      assertTrue("Expected stringResource calls not found", matches.count() != 0)

      // K2 does not generate `LDC (\\d+)`, so we cannot check IDs for the light R class.
      if (KotlinPluginModeProvider.isK2Mode()) return@runBlocking

      // Real ids are all above 0x7f000000
      assertTrue(
        "Fake IDs are not expected for a compiled project in the light R class",
        matches.all { it.groupValues[1].toInt() > 0x7f000000 },
      )
    }
  }

  @Test
  fun testDaemonIsRestartedAutomatically() {
    typeAndSaveDocument("Text(\"Hello 3\")\n")
    runBlocking {
      val (result, _) =
        fastPreviewManager.compileRequest(psiMainFile, BuildTargetReference.from(psiMainFile)!!)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
      fastPreviewManager.stopAllDaemons().join()
    }
    runBlocking {
      val (result, _) =
        fastPreviewManager.compileRequest(psiMainFile, BuildTargetReference.from(psiMainFile)!!)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
    }
  }

  @Test
  fun testFastPreviewEditChangeRender() {
    val previewElement =
      SingleComposePreviewElementInstance.forTesting<SmartPsiElementPointer<PsiElement>>(
        "google.simpleapplication.MainActivityKt.TwoElementsPreview"
      )
    val appFacet = projectRule.androidFacet(":app")
    val mainActivityFile =
      appFacet.virtualFile("src/main/java/google/simpleapplication/MainActivity.kt")
    val initialState = renderPreviewElement(appFacet, mainActivityFile, previewElement).get()!!

    typeAndSaveDocument("Text(\"Hello 3\")\n")
    runBlocking {
      val buildTargetReference = BuildTargetReference.from(psiMainFile)!!
      val (result, outputPath) =
        fastPreviewManager.compileRequest(psiMainFile, buildTargetReference)
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
      ModuleClassLoaderOverlays.getInstance(buildTargetReference)
        .pushOverlayPath(File(outputPath).toPath())
    }
    val finalState = renderPreviewElement(appFacet, mainActivityFile, previewElement).get()!!
    assertTrue(
      "Resulting image is expected to be at least 20% higher since a new text line was added",
      finalState.height > initialState.height * 1.20,
    )
  }

  @Test
  fun testMultipleFilesCompileSuccessfully() {
    val psiSecondFile = runReadAction {
      val vFile =
        projectRule.project
          .guessProjectDir()!!
          .findFileByRelativePath("app/src/main/java/google/simpleapplication/OtherPreviews.kt")!!
      PsiManager.getInstance(projectRule.project).findFile(vFile)!!
    }
    runBlocking {
      val (result, outputPath) =
        fastPreviewManager.compileRequest(
          listOf(psiMainFile, psiSecondFile),
          BuildTargetReference.from(psiMainFile)!!,
        )
      assertTrue("Compilation must pass, failed with $result", result == CompilationResult.Success)
      val generatedFilesSet =
        withContext(diskIoThread) { File(outputPath).toPath().toFileNameSet() }
      assertTrue(generatedFilesSet.contains("OtherPreviewsKt.class"))
    }
  }

  // Regression test for b/228168101
  @Test
  fun `test parallel compilations`() {
    var compile = true
    val startCountDownLatch = CountDownLatch(1)

    val previewCompilations = AtomicLong(0)
    val previewThread = thread {
      startCountDownLatch.await()
      while (compile) {
        typeAndSaveDocument("Text(\"Hello 3\")\n")
        runBlocking {
          val (result, _) =
            fastPreviewManager.compileRequest(psiMainFile, BuildTargetReference.from(psiMainFile)!!)
          if (result.isSuccess) previewCompilations.incrementAndGet()
        }
      }
    }

    val irCache = MutableIrClassCache()
    val deviceCompilations = AtomicLong(0)
    val deviceThread = thread {
      startCountDownLatch.await()
      while (compile) {
        try {
          LiveEditCompiler(projectRule.project, irCache)
            .also {
              // Normally initialized from a run configuration triggering deployment.
              it.resetState(ApplicationLiveEditServices.LegacyForTests(projectRule.project))
            }
            .compile(listOf(LiveEditCompilerInput(psiMainFile, PsiState(psiMainFile))))
          deviceCompilations.incrementAndGet()
        } catch (e: LiveEditUpdateException) {
          Logger.getInstance(FastPreviewManagerGradleTest::class.java)
            .warn("Live edit compilation failed ", e)
        }
      }
    }

    val iterations = 20L

    // Start both threads.
    startCountDownLatch.countDown()

    // Wait for both threads to run the iterations.
    runBlocking {
      delayUntilCondition(delayPerIterationMs = 200, timeout = 60.seconds) {
        deviceCompilations.get() >= iterations && previewCompilations.get() >= iterations
      }
      compile = false
    }

    previewThread.join()
    deviceThread.join()
  }

  private fun typeAndSaveDocument(typedString: String) {
    projectRule.fixture.type(typedString)
    runWriteActionAndWait {
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    runInEdtAndWait {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue() // Consume editor events
    }
  }
}
