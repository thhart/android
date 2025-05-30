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
package com.android.tools.idea.insights.client

import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.DetailedIssueStats
import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.EventPage
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.IssueState
import com.android.tools.idea.insights.IssueVariant
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Note
import com.android.tools.idea.insights.NoteId
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.Permission
import com.android.tools.idea.insights.TimeIntervalFilter
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.WithCount
import com.android.tools.idea.insights.ai.AiInsight
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.FetchSource

data class IssueRequest(val connection: Connection, val filters: QueryFilters)

data class IssueResponse(
  val issues: List<AppInsightsIssue>,
  val versions: List<WithCount<Version>>,
  val devices: List<WithCount<Device>>,
  val operatingSystems: List<WithCount<OperatingSystemInfo>>,
  val permission: Permission,
)

interface AppInsightsClient {
  suspend fun listConnections(): LoadingState.Done<List<AppConnection>>

  suspend fun listTopOpenIssues(
    request: IssueRequest,
    fetchSource: FetchSource? = null,
    mode: ConnectionMode = ConnectionMode.ONLINE,
    permission: Permission = Permission.NONE,
  ): LoadingState.Done<IssueResponse>

  suspend fun getIssueVariants(
    request: IssueRequest,
    issueId: IssueId,
  ): LoadingState.Done<List<IssueVariant>>

  suspend fun getIssueDetails(
    issueId: IssueId,
    request: IssueRequest,
    variantId: String? = null,
  ): LoadingState.Done<DetailedIssueStats?>

  suspend fun listEvents(
    issueId: IssueId,
    variantId: String?,
    request: IssueRequest,
    failureType: FailureType,
    token: String?,
  ): LoadingState.Done<EventPage>

  suspend fun updateIssueState(
    connection: Connection,
    issueId: IssueId,
    state: IssueState,
  ): LoadingState.Done<Unit>

  suspend fun listNotes(
    connection: Connection,
    issueId: IssueId,
    mode: ConnectionMode,
  ): LoadingState.Done<List<Note>>

  suspend fun createNote(
    connection: Connection,
    issueId: IssueId,
    message: String,
  ): LoadingState.Done<Note>

  suspend fun deleteNote(connection: Connection, id: NoteId): LoadingState.Done<Unit>

  suspend fun fetchInsight(
    connection: Connection,
    issueId: IssueId,
    variantId: String?,
    failureType: FailureType,
    event: Event,
    timeInterval: TimeIntervalFilter,
  ): LoadingState.Done<AiInsight>
}
