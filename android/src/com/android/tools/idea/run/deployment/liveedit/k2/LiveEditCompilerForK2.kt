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
package com.android.tools.idea.run.deployment.liveedit.k2

import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.run.deployment.liveedit.LiveEditCompiler
import com.android.tools.idea.run.deployment.liveedit.LiveEditCompilerInput
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.compilationError
import com.android.tools.idea.run.deployment.liveedit.checkPsiErrorElement
import com.android.tools.idea.run.deployment.liveedit.readActionPrebuildChecks
import com.android.tools.idea.run.deployment.liveedit.runWithCompileLock
import com.android.tools.idea.run.deployment.liveedit.tokens.ApplicationLiveEditServices
import com.android.tools.idea.util.findAndroidModule
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaCompilationResult
import org.jetbrains.kotlin.analysis.api.components.KaCompilerTarget
import org.jetbrains.kotlin.analysis.api.diagnostics.getDefaultMessageWithFactoryName
import org.jetbrains.kotlin.analysis.api.projectStructure.contextModule
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProduction
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProductionOrTest
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForTest
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleWithElementSourceModuleKindOrProduction
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

@OptIn(KaExperimentalApi::class)
internal class LiveEditCompilerForK2(private val project: Project,
                                     private val module: Module) : LiveEditCompiler.LiveEditCompilerForKotlinVersion {

  private val LOGGER = LogWrapper(Logger.getInstance(LiveEditCompilerForK2::class.java))

  override fun compileKtFile(applicationLiveEditServices: ApplicationLiveEditServices,
                             file: KtFile,
                             inputs: Collection<LiveEditCompilerInput>) = runWithCompileLock {
    LOGGER.info("Using Live Edit K2 CodeGen")
    readActionPrebuildChecks(project, file)
    val result = backendCodeGenForK2(file, module, applicationLiveEditServices.getKotlinCompilerConfiguration(file))
    return@runWithCompileLock result.output.map { OutputFileForKtCompiledFile(it) }
  }
}

@OptIn(KaExperimentalApi::class)
private fun getCompileTargetFile(original: KtFile, module: Module): KtFile {
  if (!module.platform.isCommon()) {
    return original
  }

  if (module.implementingModules.filter { it.platform.isJvm() }.size < 2) {
    return original
  }

  val androidModule = module.findAndroidModule() ?: return original
  val sourceModule = androidModule.toKaSourceModuleForProduction()
                     ?: androidModule.toKaSourceModuleForTest()
                     ?: return original

  // create a dangling copy of this file with the proper (Android) context.
  val danglingFile = KtPsiFactory(module.project).createFile(original.name, original.text)
  danglingFile.contextModule = sourceModule
  return danglingFile
}

@OptIn(KaExperimentalApi::class)
fun backendCodeGenForK2(file: KtFile, module: Module, configuration: CompilerConfiguration): KaCompilationResult.Success {
  if (ModuleUtilCore.findModuleForFile(file) != module) {
    throw LiveEditUpdateException.internalErrorFileOutsideModule(file)
  }

  // Since K2 compile AA reports syntax error, this may be unnecessary, but it throws an exception early when it has a syntax error.
  // In other words, there is no performance penalty from this early check. Let's keep it because there is no guarantee that
  // K2 compile AA covers all cases.
  listOf(file).checkPsiErrorElement()

  // TODO(316965795): Check the performance and the responsiveness once we complete K2 LE implementation.
  //                  Add/remove ProgressManager.checkCanceled() based on the performance and the responsiveness.
  ProgressManager.checkCanceled()

  val substituteFile = getCompileTargetFile(file, module)
  analyze(substituteFile) {
    val compileTarget = KaCompilerTarget.Jvm(isTestMode = false, compiledClassHandler = null, debuggerExtension = null)
    val result = this@analyze.compile(substituteFile, configuration, compileTarget) {
      // This is a lambda for `allowedErrorFilter` parameter. `compiler` API internally filters diagnostic errors with
      // `allowedErrorFilter`. If `allowedErrorFilter(diagnosticError)` is true, the error will not be reported.
      // Since we want to always report the diagnostic errors, we just return `false` here.
      false
    }
    when (result) {
      is KaCompilationResult.Success -> return result
      is KaCompilationResult.Failure -> throw compilationError(result.errors.joinToString { it.getDefaultMessageWithFactoryName() })
    }
  }
}
