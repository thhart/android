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
package com.android.tools.idea.uibuilder.handlers.motion.editor.actions

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory

/** Modify Menu action. */
abstract class ModifyMenuAction :
  DumbAwareAction("Modify Constraint Set", null, MEIcons.EDIT_MENU) {

  abstract val actions: List<AnAction>
  var enabled: Boolean = true

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = enabled
  }

  override fun actionPerformed(e: AnActionEvent) {
    val menu =
      JBPopupFactory.getInstance()
        .createActionGroupPopup(null, DefaultActionGroup(actions), e.dataContext, null, true)
    menu.showUnderneathOf(e.inputEvent!!.component)
  }
}
