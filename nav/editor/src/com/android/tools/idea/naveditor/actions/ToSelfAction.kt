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
package com.android.tools.idea.naveditor.actions

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.analytics.NavUsageTracker
import com.android.tools.idea.naveditor.model.createSelfAction
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction

class ToSelfAction(private val component: NlComponent) : AnAction("To Self") {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.getData(DESIGN_SURFACE) is NavDesignSurface
  }

  override fun actionPerformed(e: AnActionEvent) {
    val surface = e.getRequiredData(DESIGN_SURFACE)
    WriteCommandAction.runWriteCommandAction(component.model.project) {
      val action = component.createSelfAction()
      surface.selectionModel.setSelection(listOfNotNull(action))
      if (action != null) {
        NavUsageTracker.getInstance(surface.model)
          .createEvent(NavEditorEvent.NavEditorEventType.CREATE_ACTION)
          .withActionInfo(action)
          .withSource(NavEditorEvent.Source.CONTEXT_MENU)
          .log()
      }
    }
  }
}
