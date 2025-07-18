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

import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ui.AppInsightsStatusText
import com.android.tools.idea.insights.ui.EMPTY_STATE_TEXT_FORMAT
import com.android.tools.idea.insights.ui.EMPTY_STATE_TITLE_FORMAT
import com.android.tools.idea.insights.ui.InsightPermissionDeniedHandler
import com.android.tools.idea.insights.ui.transparentPanel
import com.intellij.openapi.Disposable
import java.awt.CardLayout
import java.awt.Graphics
import javax.swing.JPanel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val MAIN_CARD = "main"
private const val EMPTY_CARD = "empty"

/**
 * Main tool window panel for [InsightToolWindow].
 *
 * Shows the insight if it is available; otherwise shows [AppInsightsStatusText] with appropriate
 * message
 */
class InsightMainPanel(
  controller: AppInsightsProjectLevelController,
  parentDisposable: Disposable,
  permissionDeniedHandler: InsightPermissionDeniedHandler,
) : JPanel() {

  private val scope =
    controller.coroutineScope.createChildScope(parentDisposable = parentDisposable)

  private var isShowingInsight = false

  private val mainContentPanel =
    InsightContentPanel(
      controller,
      scope,
      controller.state
        .map { it.currentInsight }
        .stateIn(scope, SharingStarted.Eagerly, LoadingState.Ready(null)),
      parentDisposable,
      permissionDeniedHandler,
    )

  private val emptyStateText =
    AppInsightsStatusText(this) { !isShowingInsight }
      .apply {
        appendText("Select an issue", EMPTY_STATE_TITLE_FORMAT)
        appendLine("Select an issue to view insights.", EMPTY_STATE_TEXT_FORMAT, null)
      }

  init {
    val cardLayout = CardLayout()
    layout = cardLayout

    add(mainContentPanel, MAIN_CARD)
    add(transparentPanel(), EMPTY_CARD)
    scope.launch {
      controller.state
        .map { it.issues.map { timed -> timed.value.selected } }
        .distinctUntilChanged()
        .collect { state ->
          isShowingInsight = (state as? LoadingState.Ready)?.value != null

          cardLayout.show(this@InsightMainPanel, if (isShowingInsight) MAIN_CARD else EMPTY_CARD)
        }
    }
  }

  override fun paint(g: Graphics?) {
    super.paint(g)
    emptyStateText.paint(this, g)
  }
}
