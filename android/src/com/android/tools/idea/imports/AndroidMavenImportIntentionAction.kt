/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.imports

import com.android.support.AndroidxNameUtils
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.getTokenOrNull
import com.android.tools.idea.util.listenUntilNextSync
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.GlobalUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.ImportHelper
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.facet.implementedModules
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.structuralsearch.resolveExprType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.types.error.ErrorType

private const val ALL_RECEIVER_TYPES = "*"

/**
 * An action which recognizes classes from key Maven artifacts and offers to add a dependency on
 * them.
 */
class AndroidMavenImportIntentionAction : PsiElementBaseIntentionAction() {
  private var intentionActionText: String = familyName

  private data class AutoImportVariant(
    val artifactToAdd: String,
    val classToImport: String,
    val version: String?,
  ) : Comparable<AutoImportVariant> {
    override fun compareTo(other: AutoImportVariant): Int {
      artifactToAdd.compareTo(other.artifactToAdd).let { if (it != 0) return it }

      return classToImport.compareTo(other.classToImport)
    }
  }

  private class Resolvable
  private constructor(val libraries: Collection<MavenClassRegistry.LibraryImportData>) {
    companion object {
      fun createNewOrNull(
        libraries: Collection<MavenClassRegistry.LibraryImportData>
      ): Resolvable? = libraries.takeUnless { it.isEmpty() }?.let(::Resolvable)
    }
  }

  override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    val registry = MavenClassRegistryManager.getInstance().tryGetMavenClassRegistry()
    if (registry == null) {
      // This should never happen, because isAvailable should return false if there's no registry
      // yet.
      thisLogger().error("Could not get MavenClassRegistry")
      return
    }

    invoke(project, editor, element, registry, syncAfterChanges)
  }

  @TestOnly internal var syncAfterChanges = true

  override fun getFamilyName(): String =
    AndroidBundle.message("android.suggested.import.action.family.name")

  override fun getText(): String = intentionActionText

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
    val module = ModuleUtil.findModuleForPsiElement(element) ?: return false
    val projectSystem = project.getProjectSystem()
    val token = projectSystem.getTokenOrNull(AndroidMavenImportToken.EP_NAME) ?: return false
    val moduleSystem = module.getModuleSystem()

    // TODO: b/398839232 for non-jvm modules, we currently don't support import suggestions
    if (module.multiplatformNonJvm()) {
      return false
    }

    val registry =
      MavenClassRegistryManager.getInstance().tryGetMavenClassRegistry() ?: return false

    val resolvable =
      findResolvable(element, editor?.caretModel?.offset ?: -1) { text, receiverType ->
        Resolvable.createNewOrNull(
          findLibraryData(
            registry,
            moduleSystem.useAndroidX,
            text,
            receiverType,
            element.containingFile?.fileType,
            module,
          )
        )
      } ?: return false

    val foundLibraries = resolvable.libraries
    // If we already depend on any of them, we just abort providing any suggestions as well.
    if (
      foundLibraries.isEmpty() ||
        foundLibraries.any { token.dependsOn(projectSystem, module, it.artifact) }
    )
      return false

    // Update the text.
    intentionActionText =
      if (foundLibraries.size == 1) {
        val library = foundLibraries.single()
        val artifact =
          resolveArtifact(registry, moduleSystem.useAndroidX, element.language, library.artifact)
        AndroidBundle.message(
          "android.suggested.import.action.name.prefix",
          flagPreview(artifact, library.version),
        )
      } else {
        familyName
      }

    return true
  }

  override fun generatePreview(
    project: Project,
    editor: Editor,
    file: PsiFile,
  ): IntentionPreviewInfo {
    // b/396483011: Since this action uses write commands and does a Gradle sync, it doesn't work
    // well with previews. Previews also don't make a ton of sense here, since the code being
    // modified is not at the same location as the cursor. To handle this, we simply don't give a
    // preview for this action.
    return IntentionPreviewInfo.EMPTY
  }

  companion object {
    private fun invoke(
      project: Project,
      editor: Editor,
      element: PsiElement,
      registry: MavenClassRegistry,
      sync: Boolean,
    ) {
      val module = ModuleUtil.findModuleForPsiElement(element) ?: return
      val useAndroidX = module.getModuleSystem().useAndroidX

      val resolvable =
        findResolvable(element, editor.caretModel.offset) { text, receiverType ->
          Resolvable.createNewOrNull(
            findLibraryData(
              registry,
              useAndroidX,
              text,
              receiverType,
              element.containingFile?.fileType,
              module,
            )
          )
        } ?: return

      val suggestions =
        resolvable.libraries
          .asSequence()
          .map {
            val artifact = resolveArtifact(registry, useAndroidX, element.language, it.artifact)
            val importSymbol = resolveImport(useAndroidX, it.importedItemFqName)
            AutoImportVariant(artifact, importSymbol, it.version)
          }
          .toSortedSet()

      if (suggestions.isEmpty()) return

      val importSuggestion = { suggestion: AutoImportVariant ->
        doImportSuggestion(
          project,
          element,
          suggestion.artifactToAdd,
          suggestion.version,
          suggestion.classToImport,
          sync,
        )
      }

      if (suggestions.size == 1 || ApplicationManager.getApplication().isUnitTestMode) {
        importSuggestion(suggestions.first())
      } else {
        chooseFromPopup(project, editor, suggestions.toList(), importSuggestion)
      }
    }

    private fun chooseFromPopup(
      project: Project,
      editor: Editor,
      suggestions: List<AutoImportVariant>,
      onChosen: (AutoImportVariant) -> Unit,
    ) {
      val step =
        object :
          BaseListPopupStep<AutoImportVariant>(
            AndroidBundle.message("android.suggested.imports.title"),
            suggestions,
          ) {
          override fun getTextFor(value: AutoImportVariant): String {
            return flagPreview(value.artifactToAdd, value.version)
          }

          override fun onChosen(
            selectedValue: AutoImportVariant,
            finalChoice: Boolean,
          ): PopupStep<*>? {
            onChosen(selectedValue)
            return FINAL_CHOICE
          }
        }

      JBPopupFactory.getInstance()
        .createListPopup(project, step) { it }
        .showInBestPositionFor(editor)
    }

    fun doImportSuggestion(
      project: Project,
      element: PsiElement,
      artifact: String,
      artifactVersion: String?,
      importSymbol: String?,
      sync: Boolean,
    ) {
      val module = ModuleUtil.findModuleForPsiElement(element) ?: return
      val moduleSystem = module.getModuleSystem()

      fun String.toAndroidXIfNecessary() =
        if (moduleSystem.useAndroidX) AndroidxNameUtils.getCoordinateMapping(this) else this

      val extraArtifacts =
        MavenClassRegistry.findExtraArtifacts(artifact)
          .map { it.value to it.key }
          .groupBy({ it.first }, { it.second.toAndroidXIfNecessary() })
      WriteCommandAction.runWriteCommandAction(project) {
        doImportSuggestionWithWriteLock(
          project,
          module,
          element,
          artifact,
          extraArtifacts,
          artifactVersion,
          importSymbol,
        )

        if (sync) {
          UndoManager.getInstance(project)
            .undoableActionPerformed(
              object : GlobalUndoableAction() {
                override fun undo() {
                  project.requestSync()
                }

                override fun redo() {
                  project.requestSync()
                }
              }
            )
        }
      }

      if (sync) {
        project
          .getProjectSystem()
          .getSyncManager()
          .requestSyncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED)
      }

      trackSuggestedImport(artifact)
    }

    /**
     * Imports a given artifact in the project, and optionally also imports the given symbol, which
     * is currently limited to classes but will be updated to support functions (for KTX in
     * particular) in a future CL.
     */
    private fun doImportSuggestionWithWriteLock(
      project: Project,
      module: Module,
      element: PsiElement,
      artifact: String,
      extraArtifacts: Map<DependencyType, List<String>>,
      artifactVersion: String?,
      importSymbol: String?,
    ) {
      // Import the class as well (if possible); otherwise it might be confusing that you have to
      // invoke two separate intention actions in order to get your symbol resolved
      if (importSymbol != null) {
        addImportStatement(project, element, importSymbol)
      }
      val projectSystem = project.getProjectSystem()
      val token = projectSystem.getTokenOrNull(AndroidMavenImportToken.EP_NAME)
      if (token == null) {
        // This should never happen because of checks in
        // AndroidMavenImportIntentionAction#isAvailable
        // and MavenClassRegistryUtils#collectFixesFromMavenClassRegistry
        Logger.getInstance(AndroidMavenImportIntentionAction::class.java)
          .error("Could not get applicable AndroidMavenImportToken")
        return
      }

      token.addDependency(projectSystem, module, artifact, artifactVersion)
      extraArtifacts.forEach { (type, artifacts) ->
        artifacts.forEach { artifact ->
          token.addDependency(projectSystem, module, artifact, artifactVersion, type)
        }
      }
    }

    private fun Project.requestSync() {
      val syncManager = getProjectSystem().getSyncManager()
      if (syncManager.isSyncInProgress()) {
        listenUntilNextSync {
          syncManager.requestSyncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED)
        }
      } else {
        syncManager.requestSyncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED)
      }
    }

    private tailrec fun findResolvable(
      element: PsiElement,
      caret: Int,
      resolve: (String, String?) -> Resolvable?,
    ): Resolvable? {
      // This is actually the common case.
      fun resolveWithoutReceiver(s: String) = resolve(s, null)
      if (element is PsiIdentifier || caret == 0) {
        // In Java code, if you're pointing somewhere in the middle of a fully qualified name (such
        // as an import statement to a library that isn't available), the unresolved symbol won't be
        // the final class, it will be the first unavailable package segment. In these cases, search
        // down the chain for the actual imported class symbol and scan on that one instead. E.g.
        // for "androidx.camera.core.ImageCapture.OnImageSavedCallback" and "camera" is an
        // unresolvable symbol, we search first for "androidx.camera", and then
        // "androidx.camera.core", and we stop at the first resolvable, which is
        // "androidx.camera.core.ImageCapture".
        if (element.parent is PsiJavaCodeReferenceElement) {
          var curr: PsiJavaCodeReferenceElement? = element.parent as PsiJavaCodeReferenceElement
          while (curr != null) {
            resolveWithoutReceiver(curr.text)?.let {
              return it
            }

            curr = curr.parent as? PsiJavaCodeReferenceElement
          }
        }

        return resolveWithoutReceiver(element.text)
      } else if (element is LeafPsiElement && element.elementType == KtTokens.IDENTIFIER) {
        // In Kotlin code, if you're pointing somewhere in the middle of a fully qualified name
        // (such as an import statement to a library that isn't available), the unresolved symbol
        // won't be the final class, it will be the first unavailable package segment. In these
        // cases, search down the chain for the actual imported class symbol and scan on that one
        // instead.
        if (element.parent is KtNameReferenceExpression) {
          when (val current = element.parent.parent) {
            is KtDotQualifiedExpression,
            is KtCallExpression -> {
              var curr =
                current as? KtDotQualifiedExpression ?: current.parent as? KtDotQualifiedExpression
              while (curr != null) {
                curr
                  .formText()
                  ?.let { resolve(it.first, it.second) }
                  ?.let {
                    return it
                  }

                curr = curr.parent as? KtDotQualifiedExpression
              }
            }
            is KtUserType -> {
              var curr: KtUserType? = current
              while (curr != null) {
                resolveWithoutReceiver(curr.text)?.let {
                  return it
                }

                curr = curr.parent as? KtUserType
              }
            }
            else -> return resolveWithoutReceiver(element.text)
          }
        }

        return resolveWithoutReceiver(element.text)
      }

      // When the caret is at the end of the word (which it frequently is in the unresolved symbol
      // scenario, where you've just typed in the symbol you're interested in) PSI picks the element
      // on the right of the caret, which is the next element, not the symbol element.
      if (caret == element.textOffset || element is PsiWhiteSpace) {
        // Find the element at the previous position.
        val targetOffset = caret - 1
        element
          .parentContainingOffset(targetOffset)
          ?.findElementAtAbsoluteOffset(targetOffset)
          ?.let {
            return findResolvable(it, targetOffset, resolve)
          }
      }

      return resolveWithoutReceiver(element.text)
    }

    /**
     * Walks up the tree of parent [PsiElement]s until it finds one that contains the
     * [targetOffset].
     */
    private tailrec fun PsiElement.parentContainingOffset(targetOffset: Int): PsiElement? =
      if (textRange.contains(targetOffset)) this else parent?.parentContainingOffset(targetOffset)

    /**
     * Like [PsiElement.findElementAt], but with a [targetOffset] corrected for the relative offset
     * of `this` [PsiElement] in the document.
     */
    private fun PsiElement.findElementAtAbsoluteOffset(targetOffset: Int): PsiElement? =
      findElementAt(targetOffset - textRange.startOffset)

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun KtDotQualifiedExpression.formText(): Pair<String, String>? {
      val referenceNameElement =
        when (val selector = selectorExpression) {
          null -> return null
          is KtCallExpression -> selector.calleeExpression ?: return null // Get rid of any parens.
          else -> selector
        }
      var left: PsiElement = referenceNameElement
      while (left.firstChild != null) {
        left = left.firstChild
      }
      val receiverExpr =
        (receiverExpression as? KtDotQualifiedExpression)?.selectorExpression ?: receiverExpression
      if (KotlinPluginModeProvider.isK2Mode()) {
        allowAnalysisOnEdt {
          @OptIn(KaAllowAnalysisFromWriteAction::class)
          //allowAnalysisFromWriteAction {
            analyze(receiverExpr) {
              (receiverExpr.expressionType as? KaClassType)?.classId?.asFqNameString()?.let {
                return left.text to it
              }
            }
          //}
        }
      } else {
        val receiverType = receiverExpr.resolveExprType()?.takeUnless { it is ErrorType }
        receiverType?.fqName?.asString()?.let {
          return left.text to it
        }
      }

      return "${receiverExpression.text}.${left.text}" to ALL_RECEIVER_TYPES
    }

    private fun findLibraryData(
      registry: MavenClassRegistry,
      useAndroidX: Boolean,
      text: String,
      receiverType: String?,
      completionFileType: FileType?,
      module: Module,
    ): Collection<MavenClassRegistry.LibraryImportData> {
      if (receiverType == ALL_RECEIVER_TYPES) {
        return registry.findLibraryDataAnyReceiver(text, useAndroidX, completionFileType, module)
      }
      return registry.findLibraryData(text, receiverType, useAndroidX, completionFileType, module)
    }

    private fun resolveArtifact(
      registry: MavenClassRegistry,
      useAndroidX: Boolean,
      language: Language,
      artifact: String,
    ): String {
      return if (useAndroidX) {
        var androidx = AndroidxNameUtils.getCoordinateMapping(artifact)

        // Use Kotlin extension library if possible? We're basing this on
        // whether you're importing from a Kotlin file, not whether the project
        // contains Kotlin.
        if (isKotlin(language)) {
          androidx = registry.findKtxLibrary(androidx) ?: androidx
        }

        androidx
      } else {
        artifact
      }
    }

    private fun resolveImport(useAndroidX: Boolean, fqn: String): String {
      return if (useAndroidX) {
        AndroidxNameUtils.getNewName(fqn)
      } else {
        fqn
      }
    }

    private fun addImportStatement(project: Project, element: PsiElement, import: String) {
      val file = element.containingFile
      if (file.text.contains(import)) { // either as import statement or fully qualified reference
        return
      }

      when (element.language) {
        JavaLanguage.INSTANCE -> {
          val factory = JavaPsiFacade.getInstance(project).elementFactory
          val dot = import.lastIndexOf('.')
          val pkg = import.substring(0, dot)
          val name = import.substring(dot + 1)
          val cls = factory.createClass(name)
          (cls.containingFile as PsiJavaFile).packageName = pkg
          val importHelper = ImportHelper(JavaCodeStyleSettings.getInstance(element.containingFile))
          importHelper.addImport(file as PsiJavaFile, cls)
        }
        KotlinLanguage.INSTANCE -> {
          (file as KtFile).addImport(FqName(import))
        }
      // Nothing to do in XML etc
      }
    }
  }
}

private fun Module.isCommon() = implementingModules.isNotEmpty() && implementedModules.isEmpty()

private fun Module.multiplatformNonJvm() =
  isMultiPlatformModule && (isCommon() || !platform.isJvm())
