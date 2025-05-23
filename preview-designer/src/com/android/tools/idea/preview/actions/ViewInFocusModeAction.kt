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
package com.android.tools.idea.preview.actions

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.representation.PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger

/**
 * [AnAction] that open the selected sceneView in Focus Mode. The current mouse position determines
 * the selected sceneView when the action is created. The action is not enabled if the current mode
 * is Focus already.
 */
class ViewInFocusModeAction(
  @SwingCoordinate private val x: Int,
  @SwingCoordinate private val y: Int,
) : AnAction(message("action.view.in.focus.mode")) {

  private val logger = Logger.getInstance(ViewInFocusModeAction::class.java)

  override fun update(e: AnActionEvent) {
    val surface = e.getData(DESIGN_SURFACE) as? NlDesignSurface
    val modeManager = e.dataContext.findPreviewManager(PreviewModeManager.KEY)

    val sceneView = surface?.getSceneViewAt(x, y)
    val isFocusMode: Boolean = modeManager?.mode?.value is PreviewMode.Focus
    val isDefault: Boolean = modeManager?.mode?.value is PreviewMode.Default

    // Hide completely the action if:
    // * View-in-focus flag is disabled
    // * The selected preview mode is neither Default nor Focus.
    // When in Focus mode, we want to show up the action, but disabled.
    e.presentation.isVisible = StudioFlags.VIEW_IN_FOCUS_MODE.get() && (isDefault || isFocusMode)

    val hasRendered: Boolean =
      (sceneView?.sceneManager as? LayoutlibSceneManager)?.renderResult != null

    // Disable the button if:
    // * SceneView has not finished to render yet.
    // * If we are in Focus mode already.
    e.presentation.isEnabled = !isFocusMode && hasRendered
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val surface = e.getData(DESIGN_SURFACE) as NlDesignSurface
    val previewElementInstance =
      surface
        .getSceneViewAt(x, y)
        ?.sceneManager
        ?.model
        ?.dataProvider
        ?.getData(PREVIEW_ELEMENT_INSTANCE)

    if (previewElementInstance == null) {
      logger.error("Cannot find any preview element instance")
      return
    }

    val modeManager = e.dataContext.findPreviewManager(PreviewModeManager.KEY)
    if (modeManager == null) {
      logger.error("Cannot find any preview manager")
      return
    }
    modeManager.setMode(PreviewMode.Focus(previewElementInstance))
  }
}
