/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors.runsGradleErrors

import com.android.SdkConstants
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TemplateBasedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.testProjectTemplateFromPath
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import org.junit.Test

class KtsBuildFileCompilationBrokenTest: AbstractSyncFailureIntegrationTest() {

  private fun runSyncAndCheckFailure(
    preparedProject: PreparedTestProject,
    expectedErrorNodeNameVerifier: (String) -> Unit
  ) = runSyncAndCheckGeneralFailure(
    preparedProject = preparedProject,
    verifySyncViewEvents = { _, buildEvents ->
      // Expect single MessageEvent on Sync Output
      buildEvents.filterIsInstance<MessageEvent>().let { events ->
        expect.that(events).hasSize(1)
        events.firstOrNull()?.let { expectedErrorNodeNameVerifier(it.message) }
      }
      // Make sure no additional error events are generated
      expect.that(buildEvents.filterIsInstance<BuildIssueEvent>()).isEmpty()
      expect.that(buildEvents.finishEventFailures()).isEmpty()

    },
    verifyFailureReported = {
      expect.that(it.gradleSyncFailure).isEqualTo(AndroidStudioEvent.GradleSyncFailure.KTS_COMPILATION_ERROR)
      expect.that(it.buildOutputWindowStats.buildErrorMessagesList.map { it.errorShownType })
        .containsExactly(BuildErrorMessage.ErrorType.KOTLIN_COMPILER)
      expect.that(it.gradleSyncStats.printPhases()).isEqualTo("""
          FAILURE : SYNC_TOTAL/GRADLE_CONFIGURE_ROOT_BUILD
          FAILURE : SYNC_TOTAL
        """.trimIndent())
      Truth.assertThat(it.gradleFailureDetails.toTestString()).isEqualTo("""
        failure {
          error {
            exception: org.gradle.tooling.BuildActionFailureException
              at: [0]org.gradle.tooling.internal.consumer.connection.PhasedActionAwareConsumerConnection#run
            exception: org.gradle.api.ProjectConfigurationException
              at: [0]org.gradle.configuration.project.LifecycleProjectEvaluator#wrapException
            exception: org.gradle.internal.exceptions.LocationAwareException
              at: [0]org.gradle.kotlin.dsl.execution.Interpreter${'$'}ProgramHost${'$'}compileSecondStageOf${'$'}cacheDir${'$'}1#invoke
            exception: org.gradle.kotlin.dsl.support.ScriptCompilationException
              at: [0]org.gradle.kotlin.dsl.support.KotlinCompilerKt#reportToMessageCollectorAndThrowOnErrors
          }
        }
      """.trimIndent())
    }

  )

  @Test
  fun testMethodNotFoundInBuildFileRoot() {
    val preparedProject = projectRule.prepareTestProject(testProject())

    val buildFile = preparedProject.root.resolve(SdkConstants.FN_BUILD_GRADLE_KTS)
    buildFile.appendText("\nabcd()")

    runSyncAndCheckFailure(
      preparedProject = preparedProject,
      expectedErrorNodeNameVerifier = {
        // The message may have multiple lines, so check the first line only.
        // Example:
        //     Unresolved reference: abcd
        //     Build 06caa169-39fa-46b1-befd-827d18fbb27e is started
        //     Build 06caa169-39fa-46b1-befd-827d18fbb27e is closed
        expect.that(it.lines().firstOrNull()).isEqualTo("Unresolved reference: abcd")
      }
    )
  }

  @Test
  fun testPropertyNotFoundInBuildFileRoot() {
    val preparedProject = projectRule.prepareTestProject(testProject())

    val buildFile = preparedProject.root.resolve(SdkConstants.FN_BUILD_GRADLE_KTS)
    buildFile.appendText("\nabcd = \"abcd\"")

    runSyncAndCheckFailure(
      preparedProject = preparedProject,
      expectedErrorNodeNameVerifier = {
        // The message may have multiple lines, so check the first line only
        expect.that(it.lines().firstOrNull()).isEqualTo("Unresolved reference: abcd")
      }
    )
  }

  @Test
  fun testMethodNotFoundInBuildFileAndroidSection() {
    val preparedProject = projectRule.prepareTestProject(testProject())

    val buildFile = preparedProject.root.resolve(SdkConstants.FN_BUILD_GRADLE_KTS)
    buildFile.appendText("\nandroid { abcd { } }")

    runSyncAndCheckFailure(
      preparedProject = preparedProject,
      expectedErrorNodeNameVerifier = {
        // The message may have multiple lines, so check the first line only
        expect.that(it.lines().firstOrNull()).isEqualTo("Unresolved reference: abcd")
      }
    )
  }

  private fun testProject(): TemplateBasedTestProject = testProjectTemplateFromPath(
    TestProjectPaths.BASIC_KOTLIN_GRADLE_DSL,
    "tools/adt/idea/android/testData"
  )
}