/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.bazel;

import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.BazelBinarySection;
import com.google.idea.blaze.base.qsync.BazelQueryRunner;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Optional;
import java.util.Set;

class BazelBuildSystem implements BuildSystem {

  @Override
  public BuildSystemName getName() {
    return BuildSystemName.Bazel;
  }

  @Override
  public Optional<BuildInvoker> getBuildInvoker(Project project, Set<BuildInvoker.Capability> requirements) {
    return Optional.of(new LocalBazelInvoker(project, this, binaryPath(project)));
  }

  @Override
  public SyncStrategy getSyncStrategy(Project project) {
    return SyncStrategy.SERIAL;
  }

  @Override
  public void populateBlazeVersionData(WorkspaceRoot workspaceRoot, BlazeInfo blazeInfo, BlazeVersionData.Builder builder) {
    builder.setBazelVersion(BazelVersion.parseVersion(blazeInfo));
  }

  @Override
  public Optional<String> getBazelVersionString(BlazeInfo blazeInfo) {
    return Optional.ofNullable(BazelVersion.parseVersion(blazeInfo).toString());
  }

  @Override
  public BazelQueryRunner createQueryRunner(Project project) {
    return new BazelQueryRunner(project, this);
  }

  private static String binaryPath(Project project) {
    File projectSpecificBinary = null;
    ProjectViewSet projectView = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectView != null) {
      projectSpecificBinary = projectView.getScalarValue(BazelBinarySection.KEY).orElse(null);
    }

    if (projectSpecificBinary != null) {
      return projectSpecificBinary.getPath();
    }
    return BlazeUserSettings.getInstance().getBazelBinaryPath();
  }
}
