/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.android.flags.ifEnabled
import com.android.tools.analytics.UsageTracker
import com.android.tools.compose.COMPOSABLE_ANNOTATION_FQ_NAME
import com.android.tools.compose.COMPOSABLE_ANNOTATION_NAME
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.compose.PsiComposePreviewElement
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.preview.actions.ComposeNotificationGroup
import com.android.tools.idea.compose.preview.actions.ShowDebugBoundaries
import com.android.tools.idea.compose.preview.actions.StopUiCheckPreviewAction
import com.android.tools.idea.compose.preview.actions.UiCheckDropDownAction
import com.android.tools.idea.compose.preview.actions.visibleOnlyInUiCheck
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.editors.sourcecode.isKotlinFileType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.PreviewViewSingleWordFilter
import com.android.tools.idea.preview.actions.CommonViewControlAction
import com.android.tools.idea.preview.actions.GroupSwitchAction
import com.android.tools.idea.preview.actions.PreviewFilterShowHistoryAction
import com.android.tools.idea.preview.actions.PreviewFilterTextAction
import com.android.tools.idea.preview.actions.StopAnimationInspectorAction
import com.android.tools.idea.preview.actions.StopInteractivePreviewAction
import com.android.tools.idea.preview.actions.isPreviewRefreshing
import com.android.tools.idea.preview.actions.visibleOnlyInDefaultPreview
import com.android.tools.idea.preview.actions.visibleOnlyInStaticPreview
import com.android.tools.idea.preview.essentials.PreviewEssentialsModeManager
import com.android.tools.idea.preview.find.FilePreviewElementFinder
import com.android.tools.idea.preview.modes.FOCUS_MODE_LAYOUT_OPTION
import com.android.tools.idea.preview.representation.CommonRepresentationEditorFileType
import com.android.tools.idea.preview.representation.InMemoryLayoutVirtualFile
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility.FULL
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility.HIDDEN
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility.SPLIT
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationProvider
import com.android.tools.idea.util.isAndroidModule
import com.android.tools.idea.util.isCommonWithAndroidModule
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.jetbrains.android.uipreview.AndroidEditorSettings.EditorMode
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex

/** [ToolbarActionGroups] that includes the actions that can be applied to Compose Previews. */
private class ComposePreviewToolbar(surface: DesignSurface<*>) : ToolbarActionGroups(surface) {

  override fun getNorthGroup(): ActionGroup = ComposePreviewNorthGroup()

  private inner class ComposePreviewNorthGroup :
    DefaultActionGroup(
      listOfNotNull(
        StopInteractivePreviewAction(isDisabled = { isPreviewRefreshing(it.dataContext) }),
        StopAnimationInspectorAction(isDisabled = { isPreviewRefreshing(it.dataContext) }),
        StopUiCheckPreviewAction(),
        GroupSwitchAction(isEnabled = { !isPreviewRefreshing(it.dataContext) })
          .visibleOnlyInDefaultPreview(),
        CommonViewControlAction().visibleOnlyInStaticPreview(),
        Separator.getInstance().visibleOnlyInUiCheck(),
        UiCheckDropDownAction().visibleOnlyInUiCheck(),
        StudioFlags.PREVIEW_FILTER.ifEnabled {
          PreviewFilterShowHistoryAction().visibleOnlyInStaticPreview()
        },
        StudioFlags.PREVIEW_FILTER.ifEnabled {
          PreviewFilterTextAction(PreviewViewSingleWordFilter()).visibleOnlyInStaticPreview()
        },
        StudioFlags.COMPOSE_DEBUG_BOUNDS.ifEnabled { ShowDebugBoundaries() },
      )
    ) {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      super.update(e)
      if (isEssentialsModeSelected != PreviewEssentialsModeManager.isEssentialsModeEnabled) {
        isEssentialsModeSelected = PreviewEssentialsModeManager.isEssentialsModeEnabled
        if (isEssentialsModeSelected) {
          val layoutSwitcher = e.getData(DESIGN_SURFACE)?.layoutManagerSwitcher
          ApplicationManager.getApplication().invokeLater {
            layoutSwitcher?.currentLayoutOption?.value = FOCUS_MODE_LAYOUT_OPTION
          }
        }
      }
    }

    private var isEssentialsModeSelected: Boolean = false
  }

  override fun getNorthEastGroup(): ActionGroup = ComposeNotificationGroup(this)
}

/** [InMemoryLayoutVirtualFile] for composable functions. */
class ComposeAdapterLightVirtualFile(name: String, content: String, originFile: VirtualFile) :
  InMemoryLayoutVirtualFile("compose-$name", content, originFile)

/** A [PreviewRepresentationProvider] coupled with [ComposePreviewRepresentation]. */
class ComposePreviewRepresentationProvider(
  private val filePreviewElementProvider: () -> FilePreviewElementFinder<PsiComposePreviewElement> =
    {
      AnnotationFilePreviewElementFinder
    }
) : PreviewRepresentationProvider {

  private object ComposeEditorFileType :
    CommonRepresentationEditorFileType(
      ComposeAdapterLightVirtualFile::class.java,
      LayoutEditorState.Type.COMPOSE,
      ::ComposePreviewToolbar,
    )

  init {
    DesignerTypeRegistrar.register(ComposeEditorFileType)
  }

  /**
   * Checks if the input [psiFile] contains compose previews and therefore can be provided with the
   * `PreviewRepresentation` of them.
   */
  override suspend fun accept(project: Project, psiFile: PsiFile): Boolean {
    project.coroutineScope.launch { logFileIfComposable(project, psiFile) }
    return psiFile.virtualFile.isKotlinFileType() &&
      (readAction {
        (psiFile.getModuleSystem()?.usesCompose == true ||
          isCompatibleComposableClassAvailable(psiFile)) && !psiFile.isInLibrary()
      })
  }

  private suspend fun logFileIfComposable(project: Project, psiFile: PsiFile) {
    // We need to be in smart mode to be able to access the index for the annotations.
    val foundComposableAnnotations =
      smartReadAction(project) {
        KotlinAnnotationsIndex[
          COMPOSABLE_ANNOTATION_NAME,
          project,
          GlobalSearchScope.fileScope(psiFile),
        ]
      }

    withContext(Dispatchers.Default) {
      // If the file has methods annotated with @Composable methods, log it as a Compose file.
      if (foundComposableAnnotations.isNotEmpty()) {
        val studioEvent =
          AndroidStudioEvent.newBuilder()
            .setCategory(AndroidStudioEvent.EventCategory.LAYOUT_EDITOR)
            .setKind(AndroidStudioEvent.EventKind.LAYOUT_EDITOR_EVENT)
            .setLayoutEditorEvent(
              LayoutEditorEvent.newBuilder()
                .setType(LayoutEditorEvent.LayoutEditorEventType.COMPOSE_FILE_OPEN)
            )
        UsageTracker.log(studioEvent)
      }
    }
  }

  /** Creates a [ComposePreviewRepresentation] for the input [psiFile]. */
  override suspend fun createRepresentation(psiFile: PsiFile): ComposePreviewRepresentation {
    val hasPreviewMethods =
      filePreviewElementProvider().hasPreviewElements(psiFile.project, psiFile.virtualFile)
    thisLogger().debug { "${psiFile.virtualFile.path} hasPreviewMethods=${hasPreviewMethods}" }

    val globalState = AndroidEditorSettings.getInstance().globalState
    val preferredVisibility =
      if (globalState.showSplitViewForPreviewFiles && hasPreviewMethods) {
        SPLIT
      } else {
        globalState.preferredEditorMode.getVisibility(HIDDEN)
      }

    return ComposePreviewRepresentation(psiFile, preferredVisibility, ::ComposePreviewViewImpl)
  }

  override val displayName = message("representation.name")

  private fun PsiFile.isInLibrary() =
    ProjectRootManager.getInstance(project).fileIndex.isInLibrary(virtualFile)
}

private fun isCompatibleComposableClassAvailable(file: PsiFile): Boolean {
  // We need to be in smart mode to be able to access the index for the annotations.
  if (DumbService.getInstance(file.project).isDumb) return false

  val module = ModuleUtilCore.findModuleForFile(file) ?: return false
  // we only accept modules that are:
  // - Android modules
  // - Common modules, but those having Android counterpart
  // in all other scenarios, Compose Preview in common should be hidden.
  if (!module.isAndroidModule() && !module.isCommonWithAndroidModule()) {
    return false
  }
  return if (!DumbService.getInstance(module.project).isDumb) {
    val moduleScope = module.getModuleWithDependenciesAndLibrariesScope(/*includeTests = */true)
    val foundClasses = KotlinFullClassNameIndex[COMPOSABLE_ANNOTATION_FQ_NAME, module.project, moduleScope]
    foundClasses.isNotEmpty()
  } else {
    false
  }
}

private const val PREFIX = "ComposePreview"
internal val COMPOSE_PREVIEW_MANAGER = DataKey.create<ComposePreviewManager>("$PREFIX.Manager")
internal val PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE =
  DataKey.create<PsiComposePreviewElementInstance>("$PREFIX.PreviewElement")

@TestOnly fun getComposePreviewManagerKeyForTests() = COMPOSE_PREVIEW_MANAGER

private fun EditorMode?.getVisibility(defaultValue: PreferredVisibility) =
  when (this) {
    EditorMode.CODE -> HIDDEN
    EditorMode.SPLIT -> SPLIT
    EditorMode.DESIGN -> FULL
    null -> defaultValue
  }
