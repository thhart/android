/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.visuallint.analyzers

import android.widget.Button
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.configurations.Configuration
import com.android.tools.rendering.RenderResult
import com.android.tools.visuallint.VisualLintAnalyzer
import com.android.tools.visuallint.VisualLintErrorType
import com.android.utils.HtmlBuilder

private const val MAX_BUTTON_WIDTH_DP = 320

/** [VisualLintAnalyzer] for issues where a button is wider than the recommended 320dp. */
object ButtonSizeAnalyzer : VisualLintAnalyzer() {
  override val type: VisualLintErrorType
    get() = VisualLintErrorType.BUTTON_SIZE

  override fun findIssues(
    renderResult: RenderResult,
    configuration: Configuration,
  ): List<VisualLintIssueContent> {
    val issues = mutableListOf<VisualLintIssueContent>()
    val viewsToAnalyze = ArrayDeque(renderResult.rootViews)
    while (viewsToAnalyze.isNotEmpty()) {
      val view = viewsToAnalyze.removeLast()
      view.children.forEach { viewsToAnalyze.addLast(it) }
      if (isWideButton(view, configuration)) {
        issues.add(createIssueContent(view))
      }
    }
    return issues
  }

  private fun isWideButton(view: ViewInfo, configuration: Configuration): Boolean {
    if (!checkIsClass(view, Button::class.java)) {
      return false
    }
    val widthInDp = pxToDp(configuration, view.right - view.left)
    return widthInDp > MAX_BUTTON_WIDTH_DP
  }

  private fun createIssueContent(view: ViewInfo): VisualLintIssueContent {
    val summary = "The button ${nameWithId(view)} is too wide"
    val simpleName = simpleName(view)
    val provider = { count: Int ->
      HtmlBuilder()
        .add(
          "The button $simpleName is wider than ${MAX_BUTTON_WIDTH_DP}dp in ${previewConfigurations(count)}."
        )
        .newline()
        .add("Material Design recommends buttons to be no wider than ${MAX_BUTTON_WIDTH_DP}dp")
    }
    return VisualLintIssueContent(view = view, message = summary, descriptionProvider = provider)
  }
}
