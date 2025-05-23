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
package com.android.tools.idea.npw.multiplatform

import com.android.tools.idea.npw.NewProjectWizardTestUtils.getAgpVersion
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.observable.BatchInvoker
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ConfigureKotlinMultiplatformLibraryModuleStepTest {
  @get:Rule
  val projectRule =
    AndroidGradleProjectRule(agpVersionSoftwareEnvironment = getAgpVersion()).onEdt()

  private lateinit var disposable: Disposable

  @Before
  fun setup() {
    disposable = projectRule.fixture.projectDisposable
    BatchInvoker.setOverrideStrategy(BatchInvoker.INVOKE_IMMEDIATELY_STRATEGY)
  }

  @After
  fun tearDown() {
    BatchInvoker.clearOverrideStrategy()
  }

  private suspend fun buildStepWithProject(
    targetProjectPath: String
  ): Pair<ConfigureKotlinMultiplatformLibraryModuleStep, NewKotlinMultiplatformLibraryModuleModel> {
    return withContext(Dispatchers.EDT) {
      projectRule.loadProject(targetProjectPath, agpVersion = getAgpVersion())
      val model =
        NewKotlinMultiplatformLibraryModuleModel(
          project = projectRule.project,
          moduleParent = ":",
          projectSyncInvoker = emptyProjectSyncInvoker,
        )
      val moduleStep =
        ConfigureKotlinMultiplatformLibraryModuleStep(
          title = "Kotlin Multiplatform Library",
          model = model,
        )
      Disposer.register(disposable, model)
      Disposer.register(disposable, moduleStep)

      moduleStep to model
    }
  }

  @Test
  fun configureKmpModuleWithLibProject() =
    runBlocking(Dispatchers.EDT) {
      val (step, model) = buildStepWithProject(TestProjectPaths.KOTLIN_LIB)

      assertEquals(step.title, "Kotlin Multiplatform Library")
      assertEquals(model.moduleName.get(), "shared")
      assertEquals(model.packageName.get(), "com.example.shared")
    }

  companion object {
    // Ignore project sync (to speed up test), if later we are going to perform a gradle build
    // anyway.
    val emptyProjectSyncInvoker =
      object : ProjectSyncInvoker {
        override fun syncProject(project: Project) {}
      }
  }
}
