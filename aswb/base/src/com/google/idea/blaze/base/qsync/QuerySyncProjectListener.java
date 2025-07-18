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
package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;

/** A listener interface for new project snapshots. */
public interface QuerySyncProjectListener {
  /**
   * A new snapshot has got a different project structure, i.e. instance.project() has changed.
   *
   * <p>This will be called on a background thread (from the {@link
   * com.google.idea.blaze.base.async.executor.BlazeExecutor} pool.
   */
  void onNewProjectStructure(Context<?> context, ReadonlyQuerySyncProject querySyncProject, QuerySyncProjectSnapshot instance)
      throws BuildException;
}
