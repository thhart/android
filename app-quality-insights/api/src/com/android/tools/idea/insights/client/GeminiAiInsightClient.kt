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
package com.android.tools.idea.insights.client

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.gemini.buildLlmPrompt
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ai.InsightSource
import com.android.tools.idea.insights.ai.codecontext.CodeContext
import com.android.tools.idea.insights.ai.codecontext.CodeContextData
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolverImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import org.jetbrains.annotations.VisibleForTesting

/** Guidelines for the model to provide context and fine tune the response. */
@VisibleForTesting
private val GEMINI_PREAMBLE =
  """
    Respond in MarkDown format only. Do not format with HTML. Do not include duplicate heading tags.
    For headings, use H3 only. Initial explanation should not be under a heading.
    Begin with the explanation directly. Do not add fillers at the start of response.
  """
    .trimIndent()

private val GEMINI_INSIGHT_PROMPT_FORMAT =
  """
    Explain this exception from my app running on %s with Android version %s:
    Exception:
    ```
    %s
    ```
  """
    .trimIndent()

private val GEMINI_INSIGHT_WITH_CODE_CONTEXT_PROMPT_FORMAT =
  """
    Explain this exception from my app running on %s with Android version %s.
    Please reference the provided source code if they are helpful.
    Exception:
    ```
    %s
    ```
  """
    .trimIndent()

private val CONTEXT_PREAMBLE =
  """
    Respond with a comma separated list of the paths of the files, in descending order of relevance.
    If the file is a Java or Kotlin file, convert its package name to path.
  """
    .trimIndent()

private val CONTEXT_PROMPT =
  """
    What are the files relevant for fixing this exception?
    ```
    %s
    ```
  """
    .trimIndent()

// Extra space reserved for system preamble
private const val CONTEXT_WINDOW_PADDING = 150

class GeminiAiInsightClient(
  private val project: Project,
  private val cache: AppInsightsCache,
  private val codeContextResolver: CodeContextResolver = CodeContextResolverImpl(project),
) : AiInsightClient {
  private val logger =
    Logger.getInstance("com.android.tools.idea.insights.client.GeminiAiInsightClient")

  override suspend fun fetchCrashInsight(request: GeminiCrashInsightRequest): AiInsight =
    if (StudioFlags.GEMINI_FETCH_REAL_INSIGHT.get()) {
      val contextData =
        if (!request.connection.isMatchingProject()) {
          CodeContextData.DISABLED
        } else if (StudioFlags.GEMINI_ASSISTED_CONTEXT_FETCH.get()) {
          queryForRelevantContext(request)
        } else {
          codeContextResolver.getSource(request.connection, request.event.stacktraceGroup)
        }

      cache
        .getAiInsight(request.connection, request.issueId, request.variantId, contextData)
        ?.let { insight ->
          return insight
        }
      val userPrompt = createPrompt(request, contextData.codeContext)
      val finalPrompt =
        buildLlmPrompt(project) {
          systemMessage { text(GEMINI_PREAMBLE, emptyList()) }
          userMessage { text(userPrompt, emptyList()) }
        }
      logger.debug("This is the final prompt:\n$userPrompt")

      val response =
        GeminiPluginApi.getInstance().generate(project, finalPrompt).toList().joinToString("\n")

      AiInsight(response, insightSource = InsightSource.STUDIO_BOT, codeContextData = contextData)
        .also { cache.putAiInsight(request.connection, request.issueId, request.variantId, it) }
    } else {
      // Simulate a delay that would come generating an actual insight
      delay(2000)
      AiInsight(createPrompt(request, emptyList()), insightSource = InsightSource.STUDIO_BOT)
    }

  private suspend fun queryForRelevantContext(request: GeminiCrashInsightRequest): CodeContextData {
    if (
      !GeminiPluginApi.getInstance().isAvailable() ||
        !GeminiPluginApi.getInstance().isContextAllowed(project)
    )
      return CodeContextData(emptyList())
    val prompt =
      buildLlmPrompt(project) {
        systemMessage { text(CONTEXT_PREAMBLE, emptyList()) }
        userMessage {
          text(String.format(CONTEXT_PROMPT, request.event.prettyStackTrace()), emptyList())
        }
      }

    val response =
      GeminiPluginApi.getInstance().generate(project, prompt).toList().joinToString("\n")

    val fileNames = response.split(",").map { it.trim() }
    logger.debug("Gemini wants to see $fileNames")

    val contextData = codeContextResolver.getSource(fileNames)
    logger.debug(
      "AQI was able to find these context files: ${contextData.codeContext.joinToString { it.filePath }}"
    )

    return contextData
  }

  private fun createPrompt(request: GeminiCrashInsightRequest, context: List<CodeContext>): String {
    val initialPrompt =
      String.format(
          if (context.isEmpty()) GEMINI_INSIGHT_PROMPT_FORMAT
          else GEMINI_INSIGHT_WITH_CODE_CONTEXT_PROMPT_FORMAT,
          request.deviceName,
          request.apiLevel,
          request.event.prettyStackTrace(),
        )
        .trim()
    var availableContextSpace =
      GeminiPluginApi.getInstance().MAX_QUERY_CHARS - CONTEXT_WINDOW_PADDING - initialPrompt.count()
    val prompt =
      context
        .takeWhile { ctx ->
          val nextContextString = "\n${ctx.filePath}:\n```\n${ctx.content}\n```"
          availableContextSpace -= nextContextString.count()
          availableContextSpace >= 0
        }
        .fold(initialPrompt) { acc, (path, content) -> "$acc\n${path}:\n```\n$content\n```" }
    return prompt
  }
}

fun createGeminiInsightRequest(
  connection: Connection,
  issueId: IssueId,
  variantId: String?,
  event: Event,
) =
  GeminiCrashInsightRequest(
    connection = connection,
    issueId = issueId,
    variantId = variantId,
    deviceName = event.eventData.device.let { "${it.manufacturer} ${it.model}" },
    apiLevel = event.eventData.operatingSystemInfo.displayVersion,
    event = event,
  )

private fun Event.prettyStackTrace() =
  buildString {
      stacktraceGroup.exceptions.forEachIndexed { idx, exception ->
        if (idx == 0 || exception.rawExceptionMessage.shouldTakeException()) {
          appendLine(exception.rawExceptionMessage)
          append(exception.stacktrace.frames.joinToString(separator = "") { "\t${it.rawSymbol}\n" })
        }
      }
    }
    .trim()

private fun String.shouldTakeException() = startsWith("Caused by")
