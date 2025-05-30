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
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.editors.liveedit.LiveEditService
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException
import com.android.tools.idea.run.deployment.liveedit.analyzeSingleDepthInlinedFunctions
import com.android.tools.idea.run.deployment.liveedit.getCompilerConfiguration
import com.android.tools.idea.run.deployment.liveedit.isKotlinPluginBundled
import com.android.tools.idea.run.deployment.liveedit.k2.OutputFileForKtCompiledFile
import com.android.tools.idea.run.deployment.liveedit.k2.backendCodeGenForK2
import com.android.tools.idea.run.deployment.liveedit.readActionPrebuildChecks
import com.android.tools.idea.run.deployment.liveedit.runWithCompileLock
import com.android.tools.idea.run.deployment.liveedit.tokens.ApplicationLiveEditServices
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.io.createParentDirectories
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.components.KaCompiledFile
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile

private fun Throwable?.isCompilationError(): Boolean = this is LiveEditUpdateException && this.isCompilationError()

/**
 * [Throwable] used by the [EmbeddedCompilerClientImpl] during a compilation when the embedded plugin is not being used. This signals
 * to the caller that the error *might* have been caused by an incompatibility of the Kotlin Plugin.
 */
private class NotUsingKotlinBundledPlugin(cause: Throwable):
  Exception(FastPreviewBundle.message("fast.preview.error.needs.bundled.plugin"), cause)

/**
 * Implementation of the [CompilerDaemonClient] that uses the embedded compiler in Android Studio. This allows
 * to compile fragments of code in-process similar to how Live Edit does for the emulator.
 *
 * [isKotlinPluginBundled] is a method that returns if the available Kotlin Plugin is the version bundled with Android Studio. This
 * is used to diagnose problems and inform users when there is a failure that might have been caused by the user updating the Kotlin
 * Plugin.
 *
 * [beforeCompilationStarts] can be used during testing to trigger error conditions, by default in production, it does nothing.
 */
class EmbeddedCompilerClientImpl private constructor(
  private val project: Project,
  private val log: Logger,
  private val isKotlinPluginBundled: () -> Boolean,
  private val beforeCompilationStarts: () -> Unit) : CompilerDaemonClient {

  constructor(project: Project, log: Logger):
    this(project, log, ::isKotlinPluginBundled, {})

  @TestOnly
  constructor(project: Project,
              log: Logger,
              isKotlinPluginBundled: Boolean = true,
              beforeCompilationStarts: () -> Unit = {}) :
    this(project, log,
         isKotlinPluginBundled = { isKotlinPluginBundled },
         beforeCompilationStarts = beforeCompilationStarts)

  private val daemonLock = Mutex()

  /**
   * The embedded compiler does not have a daemon to start so is always running.
   */
  override val isRunning: Boolean = true

  /**
   * The Live Edit inline candidates cache. The cache can only be accessed with the Compile lock (see [runWithCompileLock]).
   * The cache is automatically invalidated on build.
   */
  private val inlineCandidateCache = LiveEditService.getInstance(project).inlineCandidateCache()

  /**
   * Compiles the given list of inputs. All inputs must belong to the same module.
   * The output will be generated in the given [outputDirectory] and progress will be updated in the given [ProgressIndicator].
   */
  private suspend fun compileModuleKtFiles(applicationLiveEditServices: ApplicationLiveEditServices, moduleForAllInputs: Module, inputs: List<KtFile>, outputDirectory: Path) = withContext(AndroidDispatchers.workerThread) {
    log.debug("compileModuleKtFiles($inputs, $outputDirectory)")

    if (KotlinPluginModeProvider.isK2Mode()) {
      compileKtFilesForK2(moduleForAllInputs, inputs, outputDirectory)
      return@withContext
    }

    val generationState =
      readAction {
        runWithCompileLock {
          beforeCompilationStarts()

          log.debug("fetchResolution")
          val resolution = fetchResolution(project, inputs)
          ProgressManager.checkCanceled()
          log.debug("analyze")
          val analysisResult = analyze(inputs, resolution)
          val inlineCandidates = inputs
            .flatMap { analyzeSingleDepthInlinedFunctions(it, analysisResult.bindingContext, inlineCandidateCache) }
            .toSet()
          ProgressManager.checkCanceled()
          log.debug("backCodeGen")
          try {
            backendCodeGen(applicationLiveEditServices, project, analysisResult, inputs, moduleForAllInputs, inlineCandidates)
          }
          catch (e: LiveEditUpdateException) {
            if (e.isCompilationError() || e.cause.isCompilationError()) {
              log.debug("backCodeGen compilation exception ", e)
              throw e
            }

            if (e.error != LiveEditUpdateException.Error.UNABLE_TO_INLINE) {
              log.debug("backCodeGen exception ", e)
              throw e
            }

            // Add any extra source file this compilation need in order to support the input file calling an inline function
            // from another source file then perform a compilation again.
            log.debug("inline analysis")
            val inputFilesWithInlines = inputs.flatMap {
              performInlineSourceDependencyAnalysis(resolution, it, analysisResult.bindingContext)
            }

            // We need to perform the analysis once more with the new set of input files.
            log.debug("inline analysis with inlines ${inputFilesWithInlines.joinToString(",") { it.name }}")
            val newAnalysisResult = resolution.analyzeWithAllCompilerChecks(inputFilesWithInlines)

            // We will need to start using the new analysis for code gen.
            log.debug("backCodeGen retry")
            backendCodeGen(applicationLiveEditServices, project, newAnalysisResult, inputFilesWithInlines, moduleForAllInputs, inlineCandidates)
          }
        }
      }
    log.debug("backCodeGen completed")

    generationState.factory.asList().forEach { it.writeTo(outputDirectory) }
  }

  @OptIn(KaExperimentalApi::class)
  private suspend fun compileKtFilesForK2(moduleForAllInputs: Module, inputs: List<KtFile>, outputDirectory: Path) {
    readAction {
      // Verify that the files are valid and the module has not been disposed.
      if (moduleForAllInputs.isDisposed) throw LiveEditUpdateException.moduleIsDisposed(moduleForAllInputs)
      inputs.forEach {
        if (!it.isValid) throw LiveEditUpdateException.fileNotValid(it)
        it.module?.let { module -> if (module.isDisposed) throw LiveEditUpdateException.moduleIsDisposed(module) }
      }

      val pathToCompileOutput = mutableMapOf<String, OutputFileForKtCompiledFile>()
      val filesAlreadyCompiled = mutableSetOf<VirtualFile>()
      runWithCompileLock {
        beforeCompilationStarts()
        log.debug("backCodeGen")
        inputs.forEach { inputFile ->
          if (inputFile.virtualFile in filesAlreadyCompiled) return@forEach

          val configuration = getCompilerConfiguration(moduleForAllInputs, inputFile)

          @OptIn(KaExperimentalApi::class)
          val result = backendCodeGenForK2(inputFile, moduleForAllInputs, configuration)
          log.debug("backCodeGen for ${inputFile.virtualFilePath} completed")
          @OptIn(KaExperimentalApi::class)
          addIfNotDuplicated(pathToCompileOutput, result.output.map { OutputFileForKtCompiledFile(it) })

          filesAlreadyCompiled.addAll(result.output.getSourceVirtualFiles())
        }
      }

      pathToCompileOutput.forEach { (_, output) -> output.writeTo(outputDirectory) }
    }
  }

  @OptIn(KaExperimentalApi::class)
  private fun List<KaCompiledFile>.getSourceVirtualFiles() =
    flatMap { it.sourceFiles }.mapNotNull { VfsUtil.findFileByIoFile(it, false) }

  /**
   * Add elements of [newCompileOutput] to [pathToCompileOutput] if [pathToCompileOutput] does not
   * have its `relativePath` as a key. Otherwise, it compares the byte arrays and raises an error
   * if they are different.
   */
  private fun addIfNotDuplicated(pathToCompileOutput: MutableMap<String, OutputFileForKtCompiledFile>,
                                 newCompileOutput: List<OutputFileForKtCompiledFile>) {
    for (output in newCompileOutput) {
      val duplicate = pathToCompileOutput[output.relativePath]
      if (duplicate == null) {
        pathToCompileOutput[output.relativePath] = output
      } else {
        if (!output.asByteArray().contentEquals(duplicate.asByteArray()) &&
            !output.relativePath.startsWith("META-INF/")) {
          error("Two Kotlin files must have the same output")
        }
      }
    }
  }

  private fun OutputFile.writeTo(outputDirectory: Path) {
    log.debug("output: $relativePath")
    val path = outputDirectory.resolve(relativePath)
    path.createParentDirectories().createFile()
    Files.write(path, asByteArray())
  }

  override suspend fun compileRequest(
    applicationLiveEditServices: ApplicationLiveEditServices,
    files: Collection<PsiFile>,
    contextBuildTargetReference: BuildTargetReference,
    outputDirectory: Path,
    indicator: ProgressIndicator): CompilationResult = coroutineScope {
    daemonLock.withLock(this) {
      val allKtInputs = files.filterIsInstance<KtFile>().toList()
      val result = CompletableDeferred<CompilationResult>()
      val compilationIndicator = ProgressWrapper.wrap(indicator)


      // When the coroutine completes, make sure we also stop or cancel the indicator
      // depending on what happened with the co-routine.
      coroutineContext.job.invokeOnCompletion {
        try {
          if (compilationIndicator.isRunning) {
            if (coroutineContext.job.isCancelled) compilationIndicator.cancel() else compilationIndicator.stop()
          }
        }
        catch (t: Throwable) {
          // stop might throw if the indicator is already stopped
          log.warn(t)
        }
      }

      try {
        allKtInputs
          .groupBy { readAction { it.module } }
          .forEach { (module, inputs) ->
            if (module == null) {
              throw LiveEditUpdateException.internalErrorMultiModule(emptySet())
            }
            compileModuleKtFiles(applicationLiveEditServices, module, inputs, outputDirectory = outputDirectory)
          }
        result.complete(CompilationResult.Success)
      }
      catch (t: CancellationException) {
        result.complete(CompilationResult.CompilationAborted())
      }
      catch (t: ProcessCanceledException) {
        result.complete(CompilationResult.CompilationAborted())
      }
      catch (t: Throwable) {
        when {
          t.isCompilationError() || t.cause.isCompilationError() -> result.complete(CompilationResult.CompilationError(t))
          !isKotlinPluginBundled() -> {
            // The embedded Compiler is only guaranteed to work with the bundled plugin. If the user has changed the plugin,
            // we can not guarantee Fast Preview to work.
            result.complete(CompilationResult.RequestException(NotUsingKotlinBundledPlugin(t)))
          }

          else -> result.complete(CompilationResult.RequestException(t))
        }
      }

      result.await()
    }
  }

  override fun dispose() {}
}
