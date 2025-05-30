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
package com.google.idea.blaze.android.run;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;

/** Configures Blaze build to output native symbols and obtains symbol file paths. */
public interface NativeSymbolFinder {
  ExtensionPointName<NativeSymbolFinder> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.NativeSymbolFinder");

  static List<NativeSymbolFinder> getInstances() {
    return NativeSymbolFinder.EP_NAME.getExtensionList();
  }

  /** Returns additional build flags required to output native symbols. */
  String getAdditionalBuildFlags();

  /** Returns native symbol files present in build output. */
  ImmutableList<File> getNativeSymbolsForBuild(
    Project project, BlazeContext context, Label label, BlazeBuildOutputs outputs);
}
