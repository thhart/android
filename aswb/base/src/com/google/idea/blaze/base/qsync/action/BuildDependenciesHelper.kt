/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.qsync.action

import com.android.tools.idea.concurrency.coroutineScope
import com.google.common.collect.Iterables.getOnlyElement
import com.google.common.util.concurrent.SettableFuture.create
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.base.qsync.QuerySyncManager.TaskOrigin
import com.google.idea.blaze.base.qsync.QuerySyncManager.getInstance
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.project.TargetsToBuild
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.guava.asDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private val logger = Logger.getInstance(BuildDependenciesHelper::class.java)

/**
 * Helper class for actions that build dependencies for source files, to allow the core logic to be
 * shared.
 */
class BuildDependenciesHelper(val project: Project) {
  private val syncManager = QuerySyncManager.getInstance(project)

  fun interface DisambiguateTargetPrompt {
    fun showPrompt(displayFileName: String, targets: Set<Label>, onChosen: (Label) -> Unit, onCancelled: () -> Unit)
  }

  fun canEnableAnalysisNow(): Boolean = syncManager.isProjectLoaded && !syncManager.operationInProgress()

  fun getTargetsToEnableAnalysisForPaths(workspaceRelativePaths: Collection<Path>): Set<TargetsToBuild> {
    if (!canEnableAnalysisNow()) {
      return emptySet()
    }
    return syncManager.getTargetsToBuildByPaths(workspaceRelativePaths)
  }

  fun getSourceFileMissingDepsCount(toBuild: TargetsToBuild.SourceFile): Int {
    val snapshot = syncManager.currentSnapshot.getOrNull() ?: return 0
    return snapshot.getPendingExternalDeps(toBuild.targets).size
  }

  fun canEnableAnalysisFor(virtualFile: VirtualFile): Boolean {
    if (!virtualFile.isInLocalFileSystem) {
      return false
    }
    val workspaceRoot = WorkspaceRoot.fromProject(project).path()
    val filePath = virtualFile.fileSystem.getNioPath(virtualFile) ?: return false
    if (!filePath.startsWith(workspaceRoot)) {
      return false
    }

    val relative = workspaceRoot.relativize(filePath)
    return syncManager.canEnableAnalysisFor(relative)
  }

  @get:Throws(BuildException::class)
  val workingSet: Set<Path>
    get() =// TODO: Any output from the context here is not shown in the console.
      syncManager.getLoadedProject().orElseThrow().getWorkingSet(BlazeContext.create())

  fun getAffectedTargetsForPaths(paths: Set<Path>): Set<Label> {
    return getTargetsToEnableAnalysisForPaths(paths.toList()).flatMap { it.targets }.toSet()
  }

  fun determineTargetsAndRun(
    workspaceRelativePaths: Collection<Path>,
    disambiguateTargetPrompt: DisambiguateTargetPrompt,
    targetDisambiguationAnchors: TargetDisambiguationAnchors,
    querySyncActionStats: QuerySyncActionStatsScope,
    consumer: (Set<Label>) -> Deferred<Boolean>
  ): Deferred<Boolean> {
    return project.coroutineScope.async(Dispatchers.Default) {
      // semi sync - without updating project
      if (!canEnableAnalysisNow()) return@async false
      val syncResult =
        withContext(Dispatchers.EDT) {
          syncManager.syncQueryDataIfNeeded(querySyncActionStats, TaskOrigin.AUTOMATIC)
        }
          .asDeferred()
          .await()
      if (!syncResult) {
        QuerySyncManager.getInstance(project)
          .notifyError("Refreshing build structure failed",
                       "Refreshing build structure failed. Check the sync console for details. Proceeding with the last known project structure.")
      }
      val groupsToBuild = getTargetsToEnableAnalysisForPaths(workspaceRelativePaths)
      val disambiguator = TargetDisambiguator.createDisambiguatorForTargetGroups(groupsToBuild, targetDisambiguationAnchors)
      val ambiguousTargets = disambiguator.ambiguousTargetSets

      val targetsToBuild = when {
        ambiguousTargets.isEmpty() -> disambiguator.unambiguousTargets
        ambiguousTargets.size == 1 -> {
          // there is a single ambiguous target set. Show the UI to disambiguate it.
          val ambiguousOne: TargetsToBuild = getOnlyElement(ambiguousTargets)!!
          val displayFileName = ambiguousOne.displayLabel
          val selection = create<Label?>()
          launch(Dispatchers.EDT) {
            disambiguateTargetPrompt.showPrompt(
              displayFileName = displayFileName,
              targets = ambiguousOne.targets,
              onChosen = { runBlocking { selection.set(it) } },
              onCancelled = { runBlocking { selection.set(null) } }
            )
          }
          val selectedLabel = selection.asDeferred().await() ?: return@async false // Cancelled.

          setOf<Label>(selectedLabel) + disambiguator.unambiguousTargets
        }

        else -> {
          logger.warn(
            "Multiple ambiguous target sets; not building them: " +
            ambiguousTargets.joinToString<TargetsToBuild>(",  ", limit = 3) { it.displayLabel })
          getInstance(project)
            .notifyWarning(
              "Ambiguous target sets found",
              "Ambiguous target sets found; not building them: "
              + ambiguousTargets.joinToString<TargetsToBuild>(", ", limit = 3) { it.displayLabel }
            )
          when {
            disambiguator.unambiguousTargets.isNotEmpty<Label>() -> disambiguator.unambiguousTargets
            else -> {
              // TODO(mathewi) show an error?
              // or should we show multiple popups in parallel? (doesn't seem great if there are lots)
              emptySet<Label>()
            }
          }
        }
      }
      val buildProcess = withContext(Dispatchers.EDT) {
        consumer(targetsToBuild)
      }
      buildProcess.await()
    }
  }

  val workingSetTargetsIfEnabled: Set<Label>
    /**
     * Returns the set of targets affected by files in the current working set if automatic building of the dependencies
     * in the working set is enabled.
     */
    get() = if (QuerySyncSettings.getInstance().buildWorkingSet()) getWorkingSetTargets() else setOf()

  private fun getWorkingSetTargets(): Set<Label> {
    return try {
      getAffectedTargetsForPaths(this.workingSet)
    }
    catch (be: BuildException) {
      syncManager.notifyWarning(
        "Could not obtain working set",
        "Error trying to obtain working set. Not including it in build: $be",
      )
      setOf()
    }
  }
}
