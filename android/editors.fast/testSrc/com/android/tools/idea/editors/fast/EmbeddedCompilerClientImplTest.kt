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
package com.android.tools.idea.editors.fast

import com.android.tools.compile.fast.CompilationResult
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException
import com.android.tools.idea.run.deployment.liveedit.composeRuntimePath
import com.android.tools.idea.run.deployment.liveedit.registerComposeCompilerPlugin
import com.android.tools.idea.run.deployment.liveedit.tokens.ApplicationLiveEditServices
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaLibraryDependency
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.tests.AdtTestKotlinArtifacts
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.testFramework.assertInstanceOf
import com.intellij.util.io.delete
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class EmbeddedCompilerClientImplTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels(
    JavaModuleModelBuilder.rootModuleBuilder,
    AndroidModuleModelBuilder(
      ":app",
      "debug",
      AndroidProjectBuilder()
        .withAndroidModuleDependencyList {
          listOf(AndroidModuleDependency(":lib", "debug"))
        }
        .withJavaLibraryDependencyList {
          listOf(
            JavaLibraryDependency.forJar(AdtTestKotlinArtifacts.kotlinStdlib),
            JavaLibraryDependency.forJar(File(composeRuntimePath))
          )
        }
    ),
    AndroidModuleModelBuilder(
      ":lib",
      "debug",
      AndroidProjectBuilder().withJavaLibraryDependencyList {
        listOf(
          JavaLibraryDependency.forJar(AdtTestKotlinArtifacts.kotlinStdlib),
          JavaLibraryDependency.forJar(File(composeRuntimePath))
        )
      }
    )
  )

  private val compiler: EmbeddedCompilerClientImpl by lazy {
    EmbeddedCompilerClientImpl(
      project = projectRule.project,
      log = Logger.getInstance(EmbeddedCompilerClientImplTest::class.java)
    )
  }

  @Before
  fun setUp() {
    registerComposeCompilerPlugin(projectRule.project)
  }

  @Test
  fun `simple compilation request`() {
    val file = projectRule.fixture.addFileToProject(
      "app/src/main/java/com/test/Source.kt",
      """
        fun testMethod() {
        }

        fun testMethodB() {
          testMethod()
        }
      """.trimIndent()
    )
    val outputDirectory = Files.createTempDirectory("out")
    runBlocking {
      val buildTargetReference = readAction { BuildTargetReference.from(file)!! }
      val result = compiler.compileRequest(
        // TODO: solodkyy - get it the right way?
        ApplicationLiveEditServices.LegacyForTests(projectRule.project),
        listOf(file),
        buildTargetReference,
        outputDirectory,
        EmptyProgressIndicator()
      )
      assertTrue(result.toString(), result is CompilationResult.Success)
      if (KotlinPluginModeProvider.isK2Mode()) {
        assertEquals(
          """
          SourceKt.class
        """.trimIndent(), outputDirectory.toFileNameSet().sorted().joinToString("\n")
        )
      } else {
        assertEquals(
          """
          EmbeddedCompilerClientImplTest_simple compilation request.app.main.kotlin_module
          SourceKt.class
        """.trimIndent(), outputDirectory.toFileNameSet().sorted().joinToString("\n")
        )
      }
    }
  }

  @Test
  fun `multi module compilation request succeeds`() {
    val file = projectRule.fixture.addFileToProject(
      "app/src/main/java/com/test/Source.kt",
      """
        package com.test

        fun testMethod() {
        }

        fun testMethodB() {
          testMethod()
        }
      """.trimIndent()
    )
    val fileInLib = projectRule.fixture.addFileToProject(
      "lib/src/main/java/com/test/lib/Source.kt",
      """
        package com.test.lib

        fun aLibMethod() {
        }
      """.trimIndent()
    )
    val outputDirectory = Files.createTempDirectory("out")
    runBlocking {
      val buildTargetReference = readAction { BuildTargetReference.from(file)!! }
      val result = compiler.compileRequest(
        // TODO: solodkyy - get it the right way?
        ApplicationLiveEditServices.LegacyForTests(projectRule.project),
        listOf(file, fileInLib),
        buildTargetReference,
        outputDirectory,
        EmptyProgressIndicator()
      )
      assertInstanceOf<CompilationResult.Success>(result)
      if (KotlinPluginModeProvider.isK2Mode()) {
        assertEquals(
          """
          SourceKt.class
        """.trimIndent(), outputDirectory.toFileNameSet().sorted().joinToString("\n")
        )
      } else {
        assertEquals(
          """
          EmbeddedCompilerClientImplTest_multi module compilation request succeeds.app.main.kotlin_module
          EmbeddedCompilerClientImplTest_multi module compilation request succeeds.lib.main.kotlin_module
          SourceKt.class
        """.trimIndent(), outputDirectory.toFileNameSet().sorted().joinToString("\n")
        )
      }
    }
  }

  @Test
  fun `syntax error compilation request`() {
    val file = projectRule.fixture.addFileToProject(
      "app/src/main/java/src/com/test/Source.kt",
      """
        fun testMethod(
        }
      """.trimIndent()
    )
    val outputDirectory = Files.createTempDirectory("out")
    runBlocking {
      val result = compiler.compileRequest(
        // TODO: solodkyy - get it the right way?
        ApplicationLiveEditServices.LegacyForTests(projectRule.project),
        listOf(file),
        BuildTargetReference.gradleOnly(projectRule.module),
        outputDirectory,
        EmptyProgressIndicator()
      )
      assertTrue(result.toString(), result is CompilationResult.CompilationError)
      assertTrue(outputDirectory.toFileNameSet().isEmpty())
    }
  }

  @Test
  fun `parallel requests`() {
    val file = projectRule.fixture.addFileToProject(
      "app/src/main/java/src/com/test/Source.kt",
      """
        fun testMethod() {
        }
      """.trimIndent()
    )

    val outputDirectories = (1..200).map { Files.createTempDirectory("out") }.toList()
    try {
      runBlocking {
        outputDirectories.forEach { outputDirectory ->
          launch {
            val buildTargetReference = readAction { BuildTargetReference.from(file)!! }
            val result = compiler.compileRequest(
              // TODO: solodkyy - get it the right way?
              ApplicationLiveEditServices.LegacyForTests(projectRule.project),
              listOf(file),
              buildTargetReference,
              outputDirectory,
              EmptyProgressIndicator()
            )
            assertTrue(result.toString(), result is CompilationResult.Success)
            if (KotlinPluginModeProvider.isK2Mode()) {
              assertEquals(
                """
                SourceKt.class
              """.trimIndent(), outputDirectory.toFileNameSet().sorted().joinToString("\n")
              )
            } else {
              assertEquals(
                """
                EmbeddedCompilerClientImplTest_parallel requests.app.main.kotlin_module
                SourceKt.class
              """.trimIndent(), outputDirectory.toFileNameSet().sorted().joinToString("\n")
              )
            }
          }
        }
      }
    } finally {
      outputDirectories.forEach { it.delete(true) }
    }
  }

  @Test
  fun `inline test`() {
    projectRule.fixture.addFileToProject(
      "app/src/main/java/src/com/test/Inline.kt",
      """
        inline fun inlineMethod() {
        }
      """.trimIndent()
    )
    val file = projectRule.fixture.addFileToProject(
      "app/src/main/java/src/com/test/Source.kt",
      """
        fun testMethod() {
          inlineMethod()
        }
      """.trimIndent()
    )

    // Test with inline analysis enabled. This should pass when using inline methods in other files.
    run {
      val compiler = EmbeddedCompilerClientImpl(
        project = projectRule.project,
        log = Logger.getInstance(EmbeddedCompilerClientImplTest::class.java), true
      )
      val outputDirectory = Files.createTempDirectory("out")
      runBlocking {
        val buildTargetReference = readAction { BuildTargetReference.from(file)!! }
        val result = compiler.compileRequest(
          // TODO: solodkyy - get it the right way?
          ApplicationLiveEditServices.LegacyForTests(projectRule.project),
          listOf(file),
          buildTargetReference,
          outputDirectory,
          EmptyProgressIndicator()
        )
        assertTrue(result.toString(), result is CompilationResult.Success)
        if (KotlinPluginModeProvider.isK2Mode()) {
          assertEquals(
            """
                SourceKt.class
              """.trimIndent(), outputDirectory.toFileNameSet().sorted().joinToString("\n")
          )
        } else {
          assertEquals(
            """
                EmbeddedCompilerClientImplTest_inline test.app.main.kotlin_module
                InlineKt.class
                SourceKt.class
              """.trimIndent(), outputDirectory.toFileNameSet().sorted().joinToString("\n")
          )
        }
      }
    }
  }

  /**
   * Verifies that the compileRequest fails correctly when passing a qualifier call with no receiver like `Test.`.
   * This is a regression test to verify that compileRequest does not break and handles that case correctly.
   */
  @Test
  fun `check dot qualifier error`() {
    val file = projectRule.fixture.addFileToProject(
      "app/src/main/java/src/com/test/Source.kt",
      """
        object Test {
          fun method() {}
        }

        fun testMethod() {
          Test.
        }
      """.trimIndent()
    )
    val outputDirectory = Files.createTempDirectory("out")
    runBlocking {
      val buildTargetReference = readAction { BuildTargetReference.from(file)!! }
      val result = compiler.compileRequest(
        // TODO: solodkyy - get it the right way?
        ApplicationLiveEditServices.LegacyForTests(projectRule.project),
        listOf(file),
        buildTargetReference,
        outputDirectory,
        EmptyProgressIndicator()
      )
      assertTrue((result as CompilationResult.CompilationError).e is LiveEditUpdateException)
    }
  }

  /**
   * Verifies that the compileRequest fails correctly when a failure could have been caused by the embedded plugin not being used.
   */
  @Test
  fun `check compilation error with non-embedded plugin`() {
    val file = projectRule.fixture.addFileToProject(
      "app/src/main/java/src/com/test/Source.kt",
      """
        object Test {
          fun method() {}
        }

        fun testMethod() {
          Test.
        }
      """.trimIndent()
    )

    run {
      val compiler = EmbeddedCompilerClientImpl(project = projectRule.project,
                                                log = Logger.getInstance(EmbeddedCompilerClientImplTest::class.java),
                                                isKotlinPluginBundled = false,
                                                { throw IllegalStateException("Message") })
      val outputDirectory = Files.createTempDirectory("out")
      runBlocking {
        val buildTargetReference = readAction { BuildTargetReference.from(file)!! }
        val result = compiler.compileRequest(
          // TODO: solodkyy - get it the right way?
          ApplicationLiveEditServices.LegacyForTests(projectRule.project),
          listOf(file),
          buildTargetReference,
          outputDirectory,
          EmptyProgressIndicator()
        )
        assertTrue(result.toString(), result is CompilationResult.RequestException)
        assertEquals(
          "Fast Preview does not support running with this Kotlin Plugin version and will only work with the bundled Kotlin Plugin.",
          (result as CompilationResult.RequestException).e?.message?.trim()
        )
      }
    }

    // Retry simulating that we are using the embedded compiler. We should get the original exception.
    run {
      val compiler = EmbeddedCompilerClientImpl(project = projectRule.project,
                                                log = Logger.getInstance(EmbeddedCompilerClientImplTest::class.java),
                                                isKotlinPluginBundled = true,
                                                { throw IllegalStateException("Message") })
      val outputDirectory = Files.createTempDirectory("out")
      runBlocking {
        val buildTargetReference = readAction { BuildTargetReference.from(file)!! }
        val result = compiler.compileRequest(
          // TODO: solodkyy - get it the right way?
          ApplicationLiveEditServices.LegacyForTests(projectRule.project),
          listOf(file),
          buildTargetReference,
          outputDirectory,
          EmptyProgressIndicator()
        )
        assertTrue(result.toString(), result is CompilationResult.RequestException)
        assertEquals(
          "Message",
          (result as CompilationResult.RequestException).e?.message?.trim()
        )
      }
    }
  }

  @Test
  fun `write action aborts current compilation`() = runBlocking {
    val file = projectRule.fixture.addFileToProject(
      "app/src/main/java/src/com/test/Source.kt",
      """
        object Test {
          fun method() {}
        }

        fun testMethod() {
        }
      """.trimIndent()
    )

    val compilationHasStarted = CompletableDeferred<Unit>()
    val countDownLatch = CountDownLatch(1)
    val beforeCompileCallCount = AtomicInteger(0)
    run {
      val compiler = EmbeddedCompilerClientImpl(
        project = projectRule.project,
        log = Logger.getInstance(EmbeddedCompilerClientImplTest::class.java),
        isKotlinPluginBundled = true
      ) {
          beforeCompileCallCount.incrementAndGet()
          compilationHasStarted.complete(Unit)
          while (!countDownLatch.await(1, TimeUnit.SECONDS)) {
            ProgressManager.checkCanceled()
          }
      }
      launch(workerThread) {
        compilationHasStarted.await()

        // Trigger a write action that should abort the compilation
        runWriteActionAndWait {}
        // Now we can let the compilation proceed in the next attempt
        countDownLatch.countDown()
      }

      val outputDirectory = Files.createTempDirectory("out")

      val buildTargetReference = readAction { BuildTargetReference.from(file)!! }
      val result = compiler.compileRequest(
        // TODO: solodkyy - get it the right way?
        ApplicationLiveEditServices.LegacyForTests(projectRule.project),
        listOf(file),
        buildTargetReference,
        outputDirectory,
        EmptyProgressIndicator()
      )
      assertEquals(CompilationResult.Success, result)
      assertTrue("Write Action should trigger a compilation re-start", beforeCompileCallCount.get() > 1)
    }
  }
}
