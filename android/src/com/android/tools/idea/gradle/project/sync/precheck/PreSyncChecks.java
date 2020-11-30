/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.precheck;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.gradle.project.sync.precheck.PreSyncCheckResult.SUCCESS;

public class PreSyncChecks {
  @NotNull private final List<SyncCheck> myStrategies;

  @NotNull
  public static PreSyncChecks getInstance() {
    return ApplicationManager.getApplication().getService(PreSyncChecks.class);
  }

  public PreSyncChecks() {
    this(new AndroidSdkPreSyncCheck(), new JdkPreSyncCheck(), new GradleWrapperPreSyncCheck());
  }

  @VisibleForTesting
  PreSyncChecks(@NotNull SyncCheck... strategies) {
    myStrategies = Lists.newArrayList(strategies);
  }

  @NotNull
  public PreSyncCheckResult canSyncAndTryToFix(@NotNull Project project) {
    for (SyncCheck condition : myStrategies) {
      PreSyncCheckResult result = condition.checkCanSyncAndTryToFix(project);
      if (!result.isSuccess()) {
        return result;
      }
    }
    return SUCCESS;
  }

  @VisibleForTesting
  @NotNull
  List<SyncCheck> getStrategies() {
    return myStrategies;
  }
}