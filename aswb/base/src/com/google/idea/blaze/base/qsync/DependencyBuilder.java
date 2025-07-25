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

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.deps.OutputGroup;
import com.google.idea.blaze.qsync.deps.OutputInfo;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/** A query sync service that knows how to build dependencies for given targets */
public interface DependencyBuilder {

  OutputInfo build(BlazeContext context, Set<Label> buildTargets, Collection<OutputGroup> outputGroups)
    throws IOException, BuildException;
}
