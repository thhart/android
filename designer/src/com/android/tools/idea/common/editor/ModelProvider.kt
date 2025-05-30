/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.common.editor

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.function.Consumer

/** Used to provide the [NlModel]s for the editor file. */
fun interface ModelProvider {

  /** The function Create the [NlModel]s for the given virtual file. */
  fun createModel(
    parentDisposable: Disposable,
    project: Project,
    buildTarget: AndroidBuildTargetReference,
    componentRegistrar: Consumer<NlComponent>,
    file: VirtualFile,
  ): NlModel?
}
