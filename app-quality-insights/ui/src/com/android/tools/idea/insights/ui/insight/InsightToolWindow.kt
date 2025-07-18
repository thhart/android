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
package com.android.tools.idea.insights.ui.insight

import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.events.actions.Action
import com.android.tools.idea.insights.ui.AppInsightsToolWindowContext
import com.android.tools.idea.insights.ui.AppInsightsToolWindowDefinition
import com.android.tools.idea.insights.ui.InsightDeprecatedPanel
import com.android.tools.idea.insights.ui.InsightPermissionDeniedHandler
import com.intellij.openapi.Disposable
import com.intellij.util.ui.JBDimension
import icons.StudioIcons
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object InsightToolWindow {
  fun create(
    projectController: AppInsightsProjectLevelController,
    parentDisposable: Disposable,
    permissionDeniedHandler: InsightPermissionDeniedHandler,
    tabVisibility: Flow<Boolean>,
    tracker: AppInsightsTracker,
  ): AppInsightsToolWindowDefinition {
    val insightWindowVisibility =
      MutableSharedFlow<Boolean>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val content =
      InsightToolWindowContent(
        projectController,
        parentDisposable,
        permissionDeniedHandler,
        tracker,
        insightWindowVisibility,
      )
    return AppInsightsToolWindowDefinition(
        "Insights",
        StudioIcons.StudioBot.LOGO_MONOCHROME,
        "APP_INSIGHTS_INSIGHTS",
        tabVisibility,
      ) {
        content
      }
      .apply {
        val insightStateFLow =
          projectController.state
            .map { it.currentInsight }
            .stateIn(
              projectController.coroutineScope,
              SharingStarted.Eagerly,
              LoadingState.Ready(null),
            )
        projectController.coroutineScope.launch {
          toolWindowVisibility.collect { isVisible ->
            insightWindowVisibility.tryEmit(isVisible)
            if (isVisible) {
              projectController.enableAction(Action.FetchInsight::class)
              if (insightStateFLow.value !is LoadingState.Ready) {
                projectController.refreshInsight(false)
              }
            } else {
              projectController.disableAction(Action.FetchInsight::class)
            }
          }
        }
      }
  }
}

private class InsightToolWindowContent(
  projectController: AppInsightsProjectLevelController,
  parentDisposable: Disposable,
  permissionDeniedHandler: InsightPermissionDeniedHandler,
  tracker: AppInsightsTracker,
  visibilityFlow: Flow<Boolean>,
) : ToolContent<AppInsightsToolWindowContext> {
  private val component = JPanel(BorderLayout())

  init {
    val comp =
      if (projectController.aiInsightToolkit.insightDeprecationData.isUnsupported()) {
        InsightDeprecatedPanel(
          AndroidCoroutineScope(parentDisposable),
          projectController.project,
          projectController.aiInsightToolkit.insightDeprecationData,
          visibilityFlow,
          tracker,
        )
      } else {
        InsightMainPanel(projectController, parentDisposable, permissionDeniedHandler)
      }

    component.add(comp, BorderLayout.CENTER)
    component.minimumSize = JBDimension(450, 0)
  }

  override fun dispose() = Unit

  override fun getComponent() = component

  override fun setToolContext(toolContext: AppInsightsToolWindowContext?) = Unit
}
