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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.gemini.LlmPrompt
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class FakeGeminiPluginApi : GeminiPluginApi {
  var available = true
  var contextAllowed = true
  var excludedFilePaths = setOf<String>()

  var stagedPrompt: String? = null

  override val MAX_QUERY_CHARS = Int.MAX_VALUE

  override fun isAvailable() = available

  override fun isContextAllowed(project: Project) = contextAllowed

  override fun isFileExcluded(project: Project, file: VirtualFile) =
    excludedFilePaths.contains(file.path)

  override fun sendChatQuery(
    project: Project,
    prompt: LlmPrompt,
    displayText: String?,
    requestSource: GeminiPluginApi.RequestSource,
  ) {}

  override fun stageChatQuery(
    project: Project,
    prompt: String,
    requestSource: GeminiPluginApi.RequestSource,
  ) {
    stagedPrompt = prompt
  }
}
