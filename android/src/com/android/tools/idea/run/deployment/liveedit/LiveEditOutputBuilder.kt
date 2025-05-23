/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.deploy.proto.Deploy.LiveEditRequest.InvalidateMode
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.nonPrivateInlineFunctionFailure
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationAddedUserClass
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationClinit
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationConstructor
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationWhenEnumPath
import com.android.tools.idea.run.deployment.liveedit.analysis.ComposeGroup
import com.android.tools.idea.run.deployment.liveedit.analysis.RegularClassVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.SyntheticClassVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.computeGroupTable
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.Differ
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.MethodDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.isInline
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrAccessFlag
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrMethod
import com.android.tools.idea.run.deployment.liveedit.analysis.toStringWithLineInfo
import com.android.tools.idea.run.deployment.liveedit.tokens.ApplicationLiveEditServices
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.codegen.`when`.WhenByEnumsMapping.MAPPINGS_CLASS_NAME_POSTFIX
import org.jetbrains.kotlin.codegen.`when`.WhenByEnumsMapping.MAPPING_ARRAY_FIELD_PREFIX
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.TimeUnit

private val logger = LogWrapper(Logger.getInstance(LiveEditOutputBuilder ::class.java))
private val debug = LiveEditLogger("LiveEditOutputBuilder")

internal class LiveEditOutputBuilder {
  // The outputs builder is *cumulative* and will include the outputs from *all previously compiled files* during this LiveEdit operation
  // Be extremely careful if you use the state inside the outputs object for any reason (or better yet, don't) - it's very easy to
  // inadvertently re-process classes and break things, especially when running in manual mode
  // TODO: Refactor this pattern so this isn't a thing that can happen
  internal fun getGeneratedCode(applicationLiveEditServices: ApplicationLiveEditServices,
                                sourceFile: KtFile,
                                compiledFiles: List<OutputFile>,
                                irCache: IrClassCache,
                                inlineCandidateCache: SourceInlineCandidateCache,
                                outputs: LiveEditCompilerOutput.Builder) {
    val startTimeNs = System.nanoTime()
    val classFiles = compiledFiles.filter { it.relativePath.endsWith(".class") }

    if (classFiles.isEmpty()) {
      throw LiveEditUpdateException.internalErrorNoCompilerOutput(sourceFile)
    }

    val keyMetaFiles = classFiles.filter(::isKeyMeta)

    val declaredClasses = getDeclaredClassNames(sourceFile)

    val irClasses = mutableListOf<IrClass>()
    val modifiedMethods = mutableListOf<IrMethod>()
    val requiresReinit = mutableListOf<IrClass>()
    for (classFile in classFiles.filterNot { it in keyMetaFiles }) {
      val changes = handleClassFile(applicationLiveEditServices, classFile, sourceFile, declaredClasses, irCache, inlineCandidateCache,
                                    outputs)
      irClasses.add(changes.clazz)

      modifiedMethods.addAll(changes.modifiedMethods)
      if (changes.requiresReinit) {
        requiresReinit.add(changes.clazz)
      }
    }

    val groupTable = computeGroupTable(irClasses)
    debug.log(groupTable.toStringWithLineInfo(sourceFile))

    // If a Composable lambda is created in a non-Compose context, re-instantiating it requires restarting the activity. The most common
    // occurrence of this is a lambda passed to setContent() inside the onCreate() method of a Compose activity
    for (clazz in requiresReinit.filter { it in groupTable.lambdaParents }) {
      val parent = groupTable.lambdaParents[clazz]!!
      if (groupTable.getComposeGroup(parent) == null) {
        logger.info("LiveEdit will restart activity because ${clazz.name} needs reinitialization in ${groupTable.lambdaParents[clazz]}")
        outputs.invalidateMode = InvalidateMode.RESTART_ACTIVITY
        break
      }
    }

    val groupsToInvalidate = mutableListOf<ComposeGroup>()
    for (method in modifiedMethods) {
      // Don't bother checking if we're already restarting the activity
      if (outputs.invalidateMode == InvalidateMode.RESTART_ACTIVITY) {
        break
      }

      // If we have method changes but no group information, the best we can do is a save and load
      if (groupTable.groups.isEmpty()) {
        outputs.invalidateMode = InvalidateMode.SAVE_AND_LOAD
        break
      }

      // Restart lambdas are a cached lambda held by Compose. They are invoked to re-run the body of a Composable function during
      // invalidation. Because they are cached, if they require re-instantiation (due to parameter or interface changes), their parent scope
      // needs to be invalidated. Since we have no way to determine the set of all parent scopes (every caller of the Composable function),
      // we need to throw away the entire tree.
      // TODO: Current Compose versions may resolve this for us, but until we verify which versions and the exact fix, this is sufficient
      if (method.clazz in groupTable.restartLambdas) {
        logger.info("LiveEdit will fully invalidate the tree because ${method.clazz.name} has changed and is a restart lambda")
        outputs.invalidateMode = InvalidateMode.SAVE_AND_LOAD
        break
      }

      val group = groupTable.getComposeGroup(method)

      // Changes to non-composable methods require a full state invalidation to ensure we re-run the changes. The ComposableSingletons class
      // is not directly associated with a group but is a special case.
      if (group == null) {
        // Handle switching the setContent lambda. We'd ideally like to restart the activity in all cases of non-Compose code being
        // modified, but this can cause issues at the moment as large blocks of non-Compose code will execute. This will suffice for now.
        if (method.name == "onCreate" && method.desc in setOf("(Landroid/os/Bundle;)V",
                                                              "(Landroid/os/Bundle;Landroid/os/PersistableBundle;)V")) {
          logger.info("LiveEdit will restart the activity due to changes in the onCreate() method of an activity")
          outputs.invalidateMode = InvalidateMode.RESTART_ACTIVITY
          outputs.hasNonComposeChanges = true
          break
        } else if (!method.clazz.name.contains("ComposableSingletons$")) {
          logger.info("LiveEdit will fully invalidate the tree due to non-Compose changes in $method")
          outputs.invalidateMode = InvalidateMode.SAVE_AND_LOAD
          outputs.hasNonComposeChanges = true
          break
        }
      }

      groupsToInvalidate.addIfNotNull(group)
    }

    if (outputs.invalidateMode == InvalidateMode.INVALIDATE_GROUPS) {
      groupsToInvalidate.forEach { outputs.addGroupId(it.key) }
    }

    val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNs)
    debug.log("Class file analysis ran in ${durationMs}ms")
  }

  private data class ChangeInfo(val clazz: IrClass, val modifiedMethods: List<IrMethod>, val requiresReinit: Boolean)

  private fun handleClassFile(applicationLiveEditServices: ApplicationLiveEditServices,
                              classFile: OutputFile,
                              sourceFile: KtFile,
                              declaredClasses: Set<String>,
                              irCache: IrClassCache,
                              inlineCandidateCache: SourceInlineCandidateCache,
                              output: LiveEditCompilerOutput.Builder): ChangeInfo {
    val classBytes = classFile.asByteArray()
    val newClass = IrClass(classBytes)
    val oldClass = irCache[newClass.name] ?: run {
      logger.info("Live Edit: No cache entry for ${newClass.name}; using the APK for class diff")
      val classContent = applicationLiveEditServices.getClassContent(sourceFile.originalFile.virtualFile, newClass.name)
      classContent?.let { IrClass(it.content) }
    }

    output.addIrClass(newClass)

    val isFirstDiff = newClass.name !in irCache
    val classType = if (newClass.name in declaredClasses) LiveEditClassType.NORMAL_CLASS else LiveEditClassType.SUPPORT_CLASS

    // Live Edit supports adding new synthetic classes in order to handle the lambda classes that Compose generates
    if (oldClass == null) {
      if (classType != LiveEditClassType.SUPPORT_CLASS) {
        throw unsupportedSourceModificationAddedUserClass("added new class ${newClass.name} in ${newClass.sourceFile}", sourceFile)
      }

      inlineCandidateCache.computeIfAbsent(newClass.name) { SourceInlineCandidate(sourceFile, it) }.setByteCode(classBytes)
      output.addClass(LiveEditCompiledClass(newClass.name, classBytes, sourceFile.module, LiveEditClassType.SUPPORT_CLASS))

      return ChangeInfo(newClass, newClass.methods, false)
    }

    val diff = Differ.diff(oldClass, newClass)

    // If we have a class diff, or it is the first time we've diffed this class, send it to the device. The first time we see a class, we
    // need to send it to the device in case it or one of its dependencies has a different name than the version in the APK.
    if (diff != null || isFirstDiff) {
      // Update the inline cache for all classes, both normal and support.
      inlineCandidateCache.computeIfAbsent(newClass.name) { SourceInlineCandidate(sourceFile, it) }.setByteCode(classBytes)
      output.addClass(LiveEditCompiledClass(newClass.name, classBytes, sourceFile.module, classType))
    }

    // If we have no diff, we don't need to check for incompatible changes or resolve group IDs.
    if (diff == null) {
      return ChangeInfo(newClass, emptyList(), false)
    }

    // Run validation on the class and get a list of method diffs containing all modified methods
    val modifiedMethods: List<MethodDiff>
    val requiresReinit: Boolean
    if (classType == LiveEditClassType.SUPPORT_CLASS) {
      val validator = SyntheticClassVisitor(newClass.name)
      diff.accept(validator)
      modifiedMethods = validator.modifiedMethods
      requiresReinit = validator.requiresReinit
    } else {
      val validator = RegularClassVisitor(newClass.name, logger)
      diff.accept(validator)
      modifiedMethods = validator.modifiedMethods
      requiresReinit = false
    }

    if (classType == LiveEditClassType.SUPPORT_CLASS && isWhenMapping(newClass)) {
      if (modifiedMethods.any {it.name.equals("<clinit>")}) {
        throw unsupportedSourceModificationWhenEnumPath("Changing `when` on enum code path in ${newClass.sourceFile}", sourceFile)
      }
    }

    val modifiedIrMethods = mutableListOf<IrMethod>()
    for (methodDiff in modifiedMethods) {
      // Map each method diff to the IR
      val irMethod = newClass.methods.single { it.name == methodDiff.name && it.desc == methodDiff.desc }
      if (isNonPrivateInline(irMethod)) {
        throw nonPrivateInlineFunctionFailure(sourceFile)
      }
      if (classType == LiveEditClassType.NORMAL_CLASS) {
        checkForInit(newClass, irMethod, !isFirstDiff)
      }
      modifiedIrMethods.add(irMethod)
    }

    return ChangeInfo(newClass, modifiedIrMethods, requiresReinit)
  }
}

/**
 * Kotlin compiler generates a synthetic mapping class when the code contains a 'when' on an enum type.
 *
 * This class only contain a static int[] that maps the enum's ordinate to an int that represent a control flow branch in the body.
 * Therefore, when the branching is changed, we need to update that mapping. However, since we can't rerun the <clinit> this is
 * impossible for now. We can tree WhenMappings just like any helper class and allow adding of new mappings but as long as existing
 * mapping is changed, we will need to go into unsupported state.
 */
private fun isWhenMapping(clazz: IrClass) : Boolean {
  return clazz.name.endsWith(MAPPINGS_CLASS_NAME_POSTFIX) && clazz.fields.all { it.name.startsWith(MAPPING_ARRAY_FIELD_PREFIX)}
}


// First diff is against the APK, so the diff for the constructors and static initializers is likely to be noisy. We skip this check
// for that particular case, and rely on the PSI validation that happened before.
private fun checkForInit(irClass: IrClass, irMethod: IrMethod, throwOnFail: Boolean) {
  if (irMethod.name == "<init>") {
    if (throwOnFail) {
      throw unsupportedSourceModificationConstructor("in ${irClass.name.replace('/', '.')}, modified constructor ${irMethod.getReadableDesc()}")
    } else {
      logger.warning("Live Edit detected modified constructor ${irClass.name}${irMethod.desc} in ${irClass.sourceFile}")
    }
  }

  if (irMethod.name == "<clinit>") {
    if (throwOnFail) {
      throw unsupportedSourceModificationClinit("in ${irClass.name.replace('/', '.')}, modified static initializer")
    } else {
      logger.warning("Live Edit detected modified static initializer block in class ${irClass.name} of ${irClass.sourceFile}")
    }
  }
}

private fun isKeyMeta(classFile: OutputFile) = classFile.relativePath.endsWith("\$KeyMeta.class")

private fun isNonPrivateInline(method: IrMethod) = method.isInline() && !method.access.contains(IrAccessFlag.PRIVATE)
