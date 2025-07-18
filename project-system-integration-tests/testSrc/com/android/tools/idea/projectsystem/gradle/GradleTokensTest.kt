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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.asdriver.tests.AndroidSystem
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class GradleTokensTest {
  @JvmField
  @Rule
  var system: AndroidSystem = AndroidSystem.standard()

  @Test
  fun `all sub-interfaces of Token are implemented by classes implementing GradleToken`() {
    system.installation.apply {
      addVmOption("-Didea.log.debug.categories=VerifyGradleTokens")
      addVmOption("-Didea.is.internal=true")
    }
    system.runStudioWithoutProject().use { studio ->
      studio.executeAction("VerifyGradleTokensAction")
      val matches = system.installation.ideaLog.waitForMatchingLine(".*VerifyGradleTokens - ([0-9]+)/([0-9]+) problems? found.*", 900, TimeUnit.SECONDS)
      assertThat(matches.groupCount()).isEqualTo(2)
      assertThat(matches.group(1)).isEqualTo("0")
      assertThat(matches.group(2)).isEqualTo("36")
    }
  }
}