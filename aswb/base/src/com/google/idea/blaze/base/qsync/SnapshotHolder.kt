/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync

import com.google.common.collect.ImmutableMap
import com.google.common.io.ByteSource
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot
import java.util.Optional
import org.jetbrains.annotations.TestOnly

/** Keeps a reference to the most up-to date [QuerySyncProjectSnapshot] instance.  */
class SnapshotHolder {
  private val lock = Any()
  private var currentInstance: QuerySyncProjectSnapshot? = null

  private val listeners = mutableListOf<QuerySyncProjectListener>()

  fun addListener(listener: QuerySyncProjectListener) {
    synchronized(lock) {
      listeners.add(listener)
    }
  }

  @Throws(BuildException::class)
  fun setCurrent(context: Context<*>, querySyncProject: ReadonlyQuerySyncProject, newInstance: QuerySyncProjectSnapshot) {
    synchronized(lock) {
      val existingInstance = currentInstance
      if (existingInstance == newInstance) {
        return
      }
      currentInstance = newInstance
      val listenersCopy = this.listeners.toList()
      return@synchronized {
        // Runs unlocked.
        if (existingInstance?.project() != newInstance.project()) {
          for (l in listenersCopy) {
            l.onNewProjectStructure(context, querySyncProject, newInstance)
          }
        }
      }
    }.invoke()
  }

  val current: Optional<QuerySyncProjectSnapshot>
    get() = synchronized(lock) { Optional.ofNullable<QuerySyncProjectSnapshot>(currentInstance) }

  operator fun invoke(): QuerySyncProjectSnapshot? = synchronized(lock) { currentInstance }

  fun getBugreportFiles(): Map<String, ByteSource> {
    val instance = synchronized(lock) { currentInstance }
    return ImmutableMap.of(
      "projectProto",
      instance?.let { ByteSource.wrap(it.project().toByteArray()) } ?: ByteSource.empty()
    )
  }

  @TestOnly
  fun clearProjectStructureForTesting() {
    synchronized(lock) {
      currentInstance = currentInstance?.toBuilder()?.project(ProjectProto.Project.getDefaultInstance())?.build()
    }
  }
}
