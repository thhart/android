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
package com.android.tools.idea.insights.events

import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.InsightsProvider
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.client.AppInsightsCache
import com.android.tools.idea.insights.events.actions.Action

data class AiInsightFetched(private val fetchedInsight: LoadingState.Done<AiInsight>) :
  ChangeEvent {
  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker,
    provider: InsightsProvider,
    cache: AppInsightsCache,
  ): StateTransition<Action> {
    val crashType = state.selectedIssue?.issueDetails?.fatality
    val appId = state.connections.selected?.appId
    val insight = (fetchedInsight as? LoadingState.Ready)?.value
    if (insight != null && crashType != null && appId != null) {
      tracker.logInsightFetch(
        appId,
        crashType,
        insight,
        GeminiPluginApi.getInstance().MAX_QUERY_CHARS,
      )
    }
    return StateTransition(newState = state.copy(currentInsight = fetchedInsight), Action.NONE)
  }
}
