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
package com.android.tools.idea

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidStudio
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import com.android.tools.platform.performance.testing.PlatformPerformanceBenchmark
import com.google.common.math.Quantiles
import com.intellij.openapi.util.SystemInfo
import org.junit.Rule
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class EditorPerformanceTest {
  @JvmField
  @Rule
  val system: AndroidSystem = AndroidSystem.standard()

  @JvmField
  @Rule
  var watcher = MemoryDashboardNameProviderWatcher()

  @Test
  fun testCompletionAndGotoDeclaration() {
    system.installation.addVmOption("-Didea.kotlin.plugin.use.k2=false")
    doTestCompletionAndGotoDeclaration(system, watcher)
  }

  data class CompletionPosition(val path: String,
                                val line: Int,
                                val column: Int,
                                val completionSymbol: String,
                                val checkFindUsages: Boolean = true)

  companion object {
    private fun checkCompletionTestCase(studio: AndroidStudio, completionPosition: CompletionPosition, isWarmup: Boolean) {
      studio.openFile(null, completionPosition.path, completionPosition.line, completionPosition.column, false, isWarmup)
      if (isWarmup)
        studio.completeCode(isWarmup)
      else
        studio.completeCode(isWarmup, completionPosition.completionSymbol)

      studio.executeEditorAction("GotoDeclarationOnly", isWarmup)
      if (completionPosition.checkFindUsages) {
        if (isWarmup)
          studio.findUsages(isWarmup)
        else
          studio.findUsages(isWarmup, completionPosition.path, completionPosition.line)
      }
      studio.closeAllEditorTabs()
    }

    private fun disableGradleDownloadSourcesAutomatically(system: AndroidSystem) {
      val filetypePaths = system.installation.configDir.resolve("options/advancedSettings.xml")
      Files.createDirectories(filetypePaths.parent)

      Files.writeString(filetypePaths, """
      <application>
        <component name="AdvancedSettings">
          <option name="settings">
            <map>
              <entry key="gradle.download.sources.automatically" value="false" />
            </map>
          </option>
        </component>
      </application>""", StandardCharsets.UTF_8)
    }

    fun doTestCompletionAndGotoDeclaration(system: AndroidSystem, watcher: MemoryDashboardNameProviderWatcher) {
      // Create a new android project, and set a fixed distribution
      val project = AndroidProject("tools/adt/idea/android/integration/testData/architecture-samples")
      // Don't show Decompiler legal notice in case of resolving in .class files.
      system.installation.acceptLegalDecompilerNotice()

      disableGradleDownloadSourcesAutomatically(system)
      // Create a maven repo and set it up in the installation and environment
      system.installRepo(MavenRepo("tools/adt/idea/android/integration/editor_performance_test_deps.manifest"))
      project.setDistribution("tools/external/gradle/gradle-8.6-bin.zip")

      system.runStudio(project, watcher.dashboardName) { studio ->
        studio.waitForSync()
        studio.waitForIndex()

        for (warmupCompletionPosition in warmupCompletionPositions) {
          checkCompletionTestCase(studio, warmupCompletionPosition, true)
        }
        for (completionPosition in completionPositions) {
          checkCompletionTestCase(studio, completionPosition, false)
        }
      }
      val benchmark = PlatformPerformanceBenchmark(watcher.dashboardName!!)

      val telemetry = system.installation.telemetry

      Quantiles.median().compute(telemetry.get("completion").toList()).let { benchmark.log("completion_median", it.toLong(), if(SystemInfo.isWindows) 310 else 30) }
      telemetry.get("completion").max(Long::compareTo).get().let { benchmark.logWithoutAnalyzer("completion_max", it) }

      Quantiles.median().compute(telemetry.get("firstCodeAnalysis").toList()).let {
        benchmark.log("firstCodeAnalysis_median", it.toLong(), if(SystemInfo.isWindows) 310 else 100)
      }
      telemetry.get("firstCodeAnalysis").max(Long::compareTo).get().let { benchmark.logWithoutAnalyzer("firstCodeAnalysis_max", it) }

      Quantiles.median().compute(telemetry.getChild("findUsagesParent", "findUsages").toList()).let {
        benchmark.log("findUsages_median", it.toLong(), 10)
      }
      telemetry.getChild("findUsagesParent", "findUsages").max(Long::compareTo).get().let {
        benchmark.logWithoutAnalyzer("findUsages_max", it)
      }

      Quantiles.median().compute(telemetry.getChild("findUsagesParent", "findUsages_firstUsage").toList()).let {
        benchmark.log("findUsages_firstUsage_median", it.toLong(), 1)
      }
      telemetry.getChild("findUsagesParent", "findUsages_firstUsage").max(Long::compareTo).get().let {
        benchmark.logWithoutAnalyzer("findUsages_firstUsage_max", it)
      }

      Quantiles.median().compute(telemetry.getChild("completion", "invokeCompletion").toList()).let {
        benchmark.log("invokeCompletion_median", it.toLong(), if(SystemInfo.isWindows) 6 else 0)
      }
      telemetry.getChild("completion", "invokeCompletion").max(Long::compareTo).get().let {
        benchmark.logWithoutAnalyzer("invokeCompletion_max", it)
      }
    }

    val warmupCompletionPositions =
      listOf(
        CompletionPosition("app/src/androidTest/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksScreenTest.kt", 139, 10,
                           "setContent"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/addedittask/AddEditTaskViewModel.kt", 91,
                           17,
                           "copy", false),
        CompletionPosition(
          "app/src/androidTest/java/com/example/android/architecture/blueprints/todoapp/addedittask/AddEditTaskScreenTest.kt",
          104, 10, "composeTestRule"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/data/Task.kt", 40, 19, "isCompleted"),
        CompletionPosition("app/src/test/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksViewModelTest.kt", 198, 35,
                           "task"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/TodoNavGraph.kt", 86, 14,
                           "TodoDestinations"),
        CompletionPosition("app/src/test/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksViewModelTest.kt", 161, 34,
                           "completed_tasks_cleared"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/statistics/StatisticsViewModel.kt", 54,
                           15,
                           "catch"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/TodoNavGraph.kt", 116, 33, "Activity"),
        CompletionPosition("app/src/test/java/com/example/android/architecture/blueprints/todoapp/data/DefaultTaskRepositoryTest.kt", 77,
                           47,
                           "size"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/taskdetail/TaskDetailScreen.kt", 89, 32,
                           "snackbarHostState"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/di/DataModules.kt", 59, 17, "Room"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksScreen.kt", 93, 29, "Filled"))

    val completionPositions =
      listOf(
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/statistics/StatisticsViewModel.kt", 71,
                           18,
                           "StatisticsUiState"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksViewModel.kt", 115, 29,
                           "completeTask"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksViewModel.kt", 179, 18,
                           "FilteringUiInfo"),
        CompletionPosition("app/src/test/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksViewModelTest.kt", 147, 25,
                           "refresh"),
        CompletionPosition("app/src/test/java/com/example/android/architecture/blueprints/todoapp/data/DefaultTaskRepositoryTest.kt", 204,
                           36,
                           "getTask"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/data/ModelMappingExt.kt", 65, 31,
                           "TaskStatus"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/data/source/network/NetworkTask.kt", 30,
                           42,
                           "ACTIVE"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksViewModel.kt", 73, 47,
                           "Async"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksViewModel.kt", 63, 70,
                           "ALL_TASKS"),
        CompletionPosition("shared-test/src/main/java/com/example/android/architecture/blueprints/todoapp/data/FakeTaskRepository.kt", 89,
                           55,
                           "copy", false),
        CompletionPosition("app/src/test/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksViewModelTest.kt", 190, 21,
                           "tasksViewModel"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/statistics/StatisticsUtils.kt", 29, 53,
                           "isActive"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/taskdetail/TaskDetailViewModel.kt", 114,
                           14,
                           "_isLoading"),
        CompletionPosition("app/src/androidTest/java/com/example/android/architecture/blueprints/todoapp/tasks/AppNavigationTest.kt", 153,
                           10,
                           "composeTestRule"),
        CompletionPosition("app/src/test/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksViewModelTest.kt", 96, 10,
                           "tasksViewModel"),
        CompletionPosition("app/src/test/java/com/example/android/architecture/blueprints/todoapp/taskdetail/TaskDetailViewModelTest.kt",
                           71, 35,
                           "title"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/taskdetail/TaskDetailScreen.kt", 88, 55,
                           "userMessage"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/util/TopAppBars.kt", 176, 35, "onBack"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/statistics/StatisticsViewModel.kt", 69,
                           16,
                           "taskLoad"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/taskdetail/TaskDetailViewModel.kt", 83,
                           38,
                           "isTaskDeleted"),
        CompletionPosition("app/src/test/java/com/example/android/architecture/blueprints/todoapp/statistics/StatisticsUtilsTest.kt", 79,
                           21,
                           "result"),
        CompletionPosition("app/src/test/java/com/example/android/architecture/blueprints/todoapp/data/DefaultTaskRepositoryTest.kt", 228,
                           21,
                           "task1FirstTime"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksScreen.kt", 115, 63,
                           "snackbarText"),
        CompletionPosition("app/src/androidTest/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksTest.kt", 92, 41,
                           "originalTaskTitle"),
        CompletionPosition("app/src/androidTest/java/com/example/android/architecture/blueprints/todoapp/tasks/AppNavigationTest.kt", 139,
                           41,
                           "taskName"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/TodoNavGraph.kt", 103, 21,
                           "TodoDestinations"),
        CompletionPosition("app/src/test/java/com/example/android/architecture/blueprints/todoapp/addedittask/AddEditTaskViewModelTest.kt",
                           124,
                           37, "TodoDestinationsArgs"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/TodoNavigation.kt", 79, 33,
                           "TodoDestinations"),
        CompletionPosition("app/src/main/java/com/example/android/architecture/blueprints/todoapp/TodoNavGraph.kt", 51, 33,
                           "TodoDestinations"))
  }
}

