/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.projectsystem;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

public abstract class AndroidProjectSettingsService extends ProjectSettingsService {

  abstract public void openSdkSettings();

  abstract public void chooseJdkLocation(@Nullable @SystemIndependent String gradleRootProjectPath);

  abstract public void openAndSelectDependency(@NotNull Module module, @NotNull String dependencyString);

  abstract public void openAndSelectDependenciesEditor(@NotNull Module module);

  abstract public void openSuggestions();
}
