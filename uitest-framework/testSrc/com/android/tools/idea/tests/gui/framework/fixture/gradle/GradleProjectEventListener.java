/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.gradle;

import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.gradle.project.build.BuildContext;
import com.android.tools.idea.gradle.project.build.BuildStatus;
import com.android.tools.idea.gradle.project.build.GradleBuildListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleProjectEventListener implements GradleBuildListener {
  @GuardedBy("myLock")
  private BuildStatus myBuildStatus;

  @GuardedBy("myLock")
  private long myBuildFinished;

  private final Object myLock = new Object();

  @Override
  @UiThread
  public void buildStarted(@NotNull BuildContext context) {
  }

  @Override
  @UiThread
  public void buildFinished(@NotNull BuildStatus status, @Nullable BuildContext context) {
    synchronized (myLock) {
      myBuildFinished = System.currentTimeMillis();
      myBuildStatus = status;
    }
  }

  public long getLastBuildTimestamp() {
    synchronized (myLock) {
      return myBuildFinished;
    }
  }

  public BuildStatus getBuildStatus() {
    synchronized (myLock) {
      return myBuildStatus;
    }
  }
}
