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

import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.ai.AiInsight

data class GeminiCrashInsightRequest(
  val connection: Connection,
  val issueId: IssueId,
  val variantId: String?,
  val deviceName: String,
  val apiLevel: String,
  val event: Event,
)

interface AiInsightClient {

  /**
   * Gets AI generated insight for this issue
   *
   * @param request - Additional context required by the insight client to get insights for the
   *   crash
   */
  suspend fun fetchCrashInsight(request: GeminiCrashInsightRequest): AiInsight
}
