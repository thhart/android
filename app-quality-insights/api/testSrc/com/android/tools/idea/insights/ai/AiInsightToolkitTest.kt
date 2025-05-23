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
package com.android.tools.idea.insights.ai

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.gservices.DevServicesDeprecationData
import com.android.tools.idea.gservices.DevServicesDeprecationDataProvider
import com.android.tools.idea.gservices.DevServicesDeprecationStatus.SUPPORTED
import com.android.tools.idea.gservices.DevServicesDeprecationStatus.UNSUPPORTED
import com.android.tools.idea.insights.StacktraceGroup
import com.android.tools.idea.insights.ai.codecontext.CodeContext
import com.android.tools.idea.insights.ai.codecontext.CodeContextData
import com.android.tools.idea.insights.ai.codecontext.FakeCodeContextResolver
import com.android.tools.idea.insights.ai.codecontext.Language
import com.android.tools.idea.insights.experiments.AppInsightsExperimentFetcher
import com.android.tools.idea.insights.experiments.Experiment
import com.android.tools.idea.insights.experiments.ExperimentGroup
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AiInsightToolkitTest {

  @get:Rule val projectRule = ProjectRule()

  private lateinit var fakeGeminiPluginApi: FakeGeminiPluginApi

  private lateinit var scope: CoroutineScope
  private lateinit var geminiToolWindow: FakeGeminiToolWindow
  private lateinit var deprecationDataProvider: DevServicesDeprecationDataProvider

  @Before
  fun setUp() {
    fakeGeminiPluginApi = FakeGeminiPluginApi()
    ExtensionTestUtil.maskExtensions(
      GeminiPluginApi.EP_NAME,
      listOf(fakeGeminiPluginApi),
      projectRule.disposable,
    )

    scope = AndroidCoroutineScope(projectRule.disposable)
    geminiToolWindow = FakeGeminiToolWindow(projectRule.project)
    val manager = FakeToolWindowManager(projectRule.project, geminiToolWindow)
    projectRule.project.replaceService(
      ToolWindowManager::class.java,
      manager,
      projectRule.disposable,
    )
    application.replaceService(
      AppInsightsExperimentFetcher::class.java,
      object : AppInsightsExperimentFetcher {
        override fun getCurrentExperiment(experimentGroup: ExperimentGroup) = Experiment.ALL_SOURCES
      },
      projectRule.disposable,
    )
    deprecationDataProvider = mock<DevServicesDeprecationDataProvider>()
    whenever(deprecationDataProvider.getCurrentDeprecationData(any()))
      .thenReturn(DevServicesDeprecationData("", "", "", false, SUPPORTED))
    application.replaceService(
      DevServicesDeprecationDataProvider::class.java,
      deprecationDataProvider,
      projectRule.disposable,
    )
  }

  @Test
  fun `code context resolver returns empty result when context sharing is off`() = runBlocking {
    val toolKit =
      AiInsightToolkitImpl(
        projectRule.project,
        StubInsightsOnboardingProvider(),
        FakeCodeContextResolver(listOf(CodeContext("class", "a/b/c", "blah", Language.KOTLIN))),
      )

    fakeGeminiPluginApi.contextAllowed = false
    assertThat(toolKit.getSource(StacktraceGroup())).isEqualTo(CodeContextData.UNASSIGNED)

    fakeGeminiPluginApi.contextAllowed = true
    assertThat(toolKit.getSource(StacktraceGroup()).codeContext).isNotEmpty()
  }

  @Test
  fun `insight deprecation data checks gemini first`() = runBlocking {
    whenever(deprecationDataProvider.getCurrentDeprecationData("gemini/gemini"))
      .thenReturn(DevServicesDeprecationData("Gemini", "desc", "url", true, UNSUPPORTED))
    whenever(deprecationDataProvider.getCurrentDeprecationData("aqi/insights"))
      .thenReturn(DevServicesDeprecationData("", "", "", false, SUPPORTED))
    val toolkit =
      AiInsightToolkitImpl(
        projectRule.project,
        StubInsightsOnboardingProvider(),
        FakeCodeContextResolver(emptyList()),
      )

    val data = toolkit.insightDeprecationData
    assertThat(data.isDeprecated()).isTrue()
    assertThat(data.header).isEqualTo("Gemini")
    assertThat(data.description).isEqualTo("desc")
    assertThat(data.moreInfoUrl).isEqualTo("url")
    assertThat(data.showUpdateAction).isTrue()
  }

  @Test
  fun `insight deprecation data checks insights after checking gemini`() = runBlocking {
    whenever(deprecationDataProvider.getCurrentDeprecationData("gemini/gemini"))
      .thenReturn(DevServicesDeprecationData("", "", "", false, SUPPORTED))
    whenever(deprecationDataProvider.getCurrentDeprecationData("aqi/insights"))
      .thenReturn(DevServicesDeprecationData("AQI", "desc", "url", true, UNSUPPORTED))
    val toolkit =
      AiInsightToolkitImpl(
        projectRule.project,
        StubInsightsOnboardingProvider(),
        FakeCodeContextResolver(emptyList()),
      )

    val data = toolkit.insightDeprecationData
    assertThat(data.isDeprecated()).isTrue()
    assertThat(data.header).isEqualTo("AQI")
    assertThat(data.description).isEqualTo("desc")
    assertThat(data.moreInfoUrl).isEqualTo("url")
    assertThat(data.showUpdateAction).isTrue()
  }
}
