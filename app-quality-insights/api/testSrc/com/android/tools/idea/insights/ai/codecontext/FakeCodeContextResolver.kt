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
package com.android.tools.idea.insights.ai.codecontext

import com.android.tools.idea.insights.StacktraceGroup
import com.android.tools.idea.insights.experiments.AppInsightsExperimentFetcher
import com.android.tools.idea.insights.experiments.Experiment
import com.android.tools.idea.insights.experiments.ExperimentGroup

open class FakeCodeContextResolver(private var codeContext: List<CodeContext>) :
  CodeContextResolver {
  override suspend fun getSource(
    stack: StacktraceGroup,
    overrideSourceLimit: Boolean,
  ): CodeContextData {
    val experiment =
      AppInsightsExperimentFetcher.instance.getCurrentExperiment(ExperimentGroup.CODE_CONTEXT)

    return when (experiment) {
      Experiment.TOP_SOURCE,
      Experiment.TOP_THREE_SOURCES,
      Experiment.ALL_SOURCES -> CodeContextData(codeContext, experiment)
      Experiment.CONTROL ->
        if (overrideSourceLimit) {
          CodeContextData(codeContext, experiment)
        } else {
          CodeContextData.CONTROL
        }
      Experiment.UNKNOWN -> CodeContextData.UNASSIGNED
    }
  }
}
