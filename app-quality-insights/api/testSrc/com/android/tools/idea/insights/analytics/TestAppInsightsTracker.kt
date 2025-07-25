/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights.analytics

import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.ai.AiInsight
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.google.wireless.android.sdk.stats.DevServiceDeprecationInfo

object TestAppInsightsTracker : AppInsightsTracker {
  override fun logZeroState(
    event: AppQualityInsightsUsageEvent.AppQualityInsightsZeroStateDetails
  ) = Unit

  override fun logCrashesFetched(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails,
  ) = Unit

  override fun logCrashListDetailView(
    event: AppQualityInsightsUsageEvent.AppQualityInsightsCrashOpenDetails
  ) = Unit

  override fun logStacktraceClicked(
    mode: ConnectionMode?,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsStacktraceDetails,
  ) = Unit

  override fun logConsoleLinkClicked(
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsConsoleLinkDetails,
  ) = Unit

  override fun logError(
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsErrorDetails,
  ) = Unit

  override fun logIssueStatusChanged(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsIssueChangedDetails,
  ) = Unit

  override fun logNotesAction(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsNotesDetails,
  ) = Unit

  override fun logOfflineTransitionAction(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsModeTransitionDetails,
  ) = Unit

  override fun logEventViewed(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    issueId: String,
    eventId: String,
  ) = Unit

  override fun logEventsFetched(
    unanonymizedAppId: String,
    issueId: String,
    crashType: FailureType,
    isFirstFetch: Boolean,
  ) = Unit

  override fun logInsightSentiment(
    sentiment: AppQualityInsightsUsageEvent.InsightSentiment.Sentiment,
    crashType: AppQualityInsightsUsageEvent.CrashType,
    insight: AiInsight,
  ) = Unit

  override fun logInsightFetch(
    unanonymizedAppId: String,
    crashType: FailureType,
    insight: AiInsight,
    contextLimit: Int,
  ) = Unit

  override fun logServiceDeprecated(
    panel: AppQualityInsightsUsageEvent.ServiceDeprecationInfo.Panel,
    deliveryType: DevServiceDeprecationInfo.DeliveryType,
    userNotified: Boolean?,
    userClickedMoreInfo: Boolean?,
    userClickedUpdate: Boolean?,
    userClickedDismiss: Boolean?,
  ) = Unit
}
