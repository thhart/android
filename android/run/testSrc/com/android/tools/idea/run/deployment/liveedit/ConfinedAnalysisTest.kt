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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.deployment.liveedit.analysis.directApiCompile
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ConfinedAnalysisTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory().withKotlin()
  private var files = HashMap<String, PsiFile>()

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)
    StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_CONFINED_ANALYSIS.override(true)
    files["A.kt"] = projectRule.fixture.configureByText("A.kt",
                                                        "public class A() { fun foo() : Int { return B().foo() } }")
    files["B.kt"] = projectRule.fixture.configureByText("B.kt",
                                                        "public class B() { fun foo() : Int { asdfadsfasdf } }")
  }

  @After
  fun takeDown() {
    StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_CONFINED_ANALYSIS.clearOverride()
  }

  @Test
  fun `Test With and Without Confined Analysis`() {
    projectRule.directApiCompile(listOf(files["A.kt"] as KtFile))
  }
}