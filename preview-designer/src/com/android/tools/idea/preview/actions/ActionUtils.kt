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
package com.android.tools.idea.preview.actions

import com.android.tools.idea.actions.SCENE_VIEW
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.util.EnableUnderConditionWrapper
import com.android.tools.idea.common.util.ShowGroupUnderConditionWrapper
import com.android.tools.idea.common.util.ShowUnderConditionWrapper
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.preview.mvvm.PreviewViewModelStatus
import com.android.tools.idea.projectsystem.needsBuild
import com.android.tools.idea.uibuilder.editor.multirepresentation.MultiRepresentationPreview
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.TextEditorWithMultiRepresentationPreview
import com.android.tools.idea.uibuilder.scene.hasRenderErrors
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

/**
 * Helper method that navigates back to the previous [PreviewMode] for all [PreviewModeManager]s in
 * the given [AnActionEvent]'s [DataContext].
 *
 * @param e the [AnActionEvent] holding the context of the action
 */
fun navigateBack(e: AnActionEvent) {
  e.dataContext.findPreviewManager(PreviewModeManager.KEY)?.restorePrevious()
}

/**
 * Returns a preview manager [T] related to the current context (which is implied to be bound to a
 * particular file), or null if one is not found. The search is done among the open preview parts
 * and [PreviewRepresentation] of the selected file editor.
 *
 * This call might access the [CommonDataKeys.VIRTUAL_FILE] so it should not be called in the EDT
 * thread. For actions using it, they should use [ActionUpdateThread.BGT].
 */
@RequiresBackgroundThread
inline fun <reified T> DataContext.findPreviewManager(key: DataKey<T>): T? {
  getData(key)?.let {
    // The context is associated to a preview manager so return it
    return it
  }

  // Fallback to finding the preview manager by looking into all the editors
  val project = getData(CommonDataKeys.PROJECT) ?: return null
  val file = getData(CommonDataKeys.VIRTUAL_FILE) ?: return null

  return FileEditorManager.getInstance(project)?.getSelectedEditor(file)?.getPreviewManager<T>()
}

/**
 * Returns the preview manager of type [T] or null if this [FileEditor]'s preview representation is
 * not of type [T].
 */
inline fun <reified T> FileEditor.getPreviewManager(): T? =
  when (this) {
    is MultiRepresentationPreview -> this.currentRepresentation as? T
    is TextEditorWithMultiRepresentationPreview<out MultiRepresentationPreview> ->
      this.preview.currentRepresentation as? T
    else -> null
  }

/**
 * Makes the given list of actions only visible when the preview is not in interactive or animation
 * modes. Returns an [ActionGroup] that handles the visibility.
 */
fun List<AnAction>.visibleOnlyInStaticPreview(): ActionGroup =
  ShowGroupUnderConditionWrapper(DefaultActionGroup(this)) {
    it.getData(PreviewModeManager.KEY)?.mode?.value?.isNormal == true
  }

/**
 * Makes the given action only visible when the preview is not in interactive or animation modes.
 * Returns an [ActionGroup] that handles the visibility.
 */
fun AnAction.visibleOnlyInStaticPreview(): ActionGroup = listOf(this).visibleOnlyInStaticPreview()

/** Hide the given actions if the [SceneView] contains render errors. */
fun List<AnAction>.hideIfRenderErrors(): List<AnAction> = map {
  ShowUnderConditionWrapper(it) { context -> !hasSceneViewErrors(context) }
}

/**
 * The given disables the actions if any of the following conditions are met:
 * * the surface is refreshing
 * * the [SceneView] contains errors
 * * the preview has errors that will need a refresh
 * * the project needs a build
 */
fun List<AnAction>.disabledIfRefreshingOrHasErrorsOrProjectNeedsBuild(): List<AnAction> =
  disabledIf(
    { context -> context.isRefreshing() || context.hasErrors() || context.projectNeedsBuild() },
    { context ->
      when {
        context.isRefreshing() -> message("action.disabled.refreshing")
        context.hasErrors() -> message("action.disabled.error")
        context.projectNeedsBuild() -> message("action.disabled.project.needs.build")
        else -> null
      }
    },
  )

private fun DataContext.projectNeedsBuild() = getData(CommonDataKeys.PROJECT)?.needsBuild == true

private fun DataContext.isRefreshing() = isPreviewRefreshing(this)

private fun DataContext.hasErrors() = previewHasSyntaxErrors(this) || hasSceneViewErrors(this)

/**
 * Wraps each action to control its enabled state.
 *
 * Disables the action if the `predicate` is true when given the action's context.
 *
 * @param predicate Decides if the action is disabled (returns true).
 * @param reasonForDisabling (Optional) Explains why the action is disabled. It will replace
 *   original description of action if not null.
 * @return A new list of actions.
 */
fun List<AnAction>.disabledIf(
  predicate: (DataContext) -> Boolean,
  reasonForDisabling: (context: DataContext) -> String? = { null },
): List<AnAction> = map {
  EnableUnderConditionWrapper(it, { context -> !predicate(context) }, reasonForDisabling)
}

/**
 * Returns if the preview attached to the given [DataContext] is refreshing or not. The preview
 * needs to have set a [PreviewViewModelStatus] using the [PREVIEW_VIEW_MODEL_STATUS] key in the
 * [DataContext].
 *
 * @param dataContext
 */
fun isPreviewRefreshing(dataContext: DataContext) =
  dataContext.getData(PREVIEW_VIEW_MODEL_STATUS)?.isRefreshing == true

/**
 * Returns if the preview file attached to the given [DataContext] has syntax errors or not.
 *
 * @param dataContext
 */
private fun previewHasSyntaxErrors(dataContext: DataContext) =
  dataContext.getData(PREVIEW_VIEW_MODEL_STATUS)?.hasSyntaxErrors == true

fun hasSceneViewErrors(dataContext: DataContext) =
  dataContext.getData(SCENE_VIEW)?.hasRenderErrors() == true

// TODO(b/292057010) Enable group filtering for Gallery mode.
private class PreviewDefaultWrapper(actions: List<AnAction>) : DefaultActionGroup(actions) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.getData(PreviewModeManager.KEY)?.let {
      e.presentation.isVisible = it.mode.value is PreviewMode.Default
    }
  }
}

/**
 * Makes the given action only visible when the preview is in the [PreviewMode.Default] mode.
 * Returns an [ActionGroup] that handles the visibility.
 */
fun AnAction.visibleOnlyInDefaultPreview(): ActionGroup = PreviewDefaultWrapper(listOf(this))

/** Makes the given action only visible when the preview is in the [PreviewMode.Focus] mode. */
fun AnAction.visibleOnlyInFocus(): AnAction =
  object : AnActionWrapper(this) {
    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isVisible =
        e.presentation.isVisible && e.getData(PreviewModeManager.KEY)?.mode?.value is PreviewMode.Focus
    }
  }
