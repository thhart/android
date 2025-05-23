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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.gemini.LlmPrompt
import com.android.tools.idea.gemini.buildLlmPrompt
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.KotlinLanguage

internal const val PROMPT_PREFIX =
  "An analysis of my project has detected an error. How do I modify my code to fix it?"

/**
 * Quick fix action for asking Gemini how to fix a Visual Lint issue.
 *
 * This action calls on Gemini to provide an explanation of the given issue, and to suggest a fix.
 */
class ComposeVisualLintAiFix(
  private val project: Project,
  private val issue: VisualLintRenderIssue,
  private val previewName: String,
) : Runnable {
  override fun run() {
    ComposeCodeDialog(project).run {
      show()
      AndroidCoroutineScope(disposable).launch(AndroidDispatchers.workerThread) {
        val response = askAi()
        withContext(AndroidDispatchers.uiThread) { updateContent(response) }
      }
    }
  }

  private suspend fun askAi(): String {
    val geminiPluginApi = GeminiPluginApi.getInstance()
    // The user must complete the Gemini onboarding and enable context sharing, otherwise we
    // can't use the generate API.
    if (!geminiPluginApi.isContextAllowed(project)) {
      return "Gemini context sharing needs to be enabled for this feature"
    }
    try {
      return geminiPluginApi.generate(project, getPrompt(issue)).toList().joinToString("")
    } catch (t: Throwable) {
      return "An error has occurred"
    }
  }

  private fun getPrompt(issue: VisualLintRenderIssue): LlmPrompt {
    return buildLlmPrompt(project) {
      userMessage {
        text(PROMPT_PREFIX, filesUsed = emptyList())
        text("The summary of the issue is: ${issue.summary}", filesUsed = emptyList())
        text("The description of the issue is: ${issue.description}", filesUsed = emptyList())
        text(
          "The files affected by this issues are: ${issue.affectedFiles.map { it.name }.joinToString(",")}",
          filesUsed = issue.affectedFiles,
        )
        text(
          "The issue was found when analysing the Compose preview generated by $previewName",
          filesUsed = issue.affectedFiles,
        )
        issue.affectedFiles.forEach {
          code(String(it.contentsToByteArray()), KotlinLanguage.INSTANCE, issue.affectedFiles)
        }
      }
    }
  }

  private class ComposeCodeDialog(project: Project) : DialogWrapper(project) {

    private val textArea = JBTextArea("Sending query to Gemini...")

    init {
      isModal = false
      super.init()
    }

    override fun createCenterPanel(): JComponent {
      val scrollPane = JBScrollPane(textArea).apply { preferredSize = JBUI.size(600, 1000) }
      return JPanel(BorderLayout()).apply { add(scrollPane, BorderLayout.CENTER) }
    }

    fun updateContent(content: String) {
      textArea.text = content
    }
  }
}
