/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.flow

import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.preview.groups.PreviewGroupManager
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.preview.PreviewElement
import com.intellij.openapi.actionSystem.DataKey
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface used for [PreviewRepresentation]s to manage flows of [PreviewElement]s. It allows
 * retrieving all preview elements available for a [PreviewRepresentation], as well as the same
 * elements but filtered, to be used for rendering purposes.
 *
 * @see [FlowableCollection]
 */
interface PreviewFlowManager<T : PreviewElement<*>> : PreviewGroupManager {

  /** Flow containing all the available [T]s for this manager. */
  val allPreviewElementsFlow: StateFlow<FlowableCollection<T>>

  /**
   * Paginator responsible for paginating the filtered [T]s from [allPreviewElementsFlow] into
   * different pages.
   */
  val previewFlowPaginator: PreviewFlowPaginator<T>

  /**
   * Flow containing the corresponding page from the [previewFlowPaginator] that is expected to be
   * rendered. The content of this flow should differ from [renderedPreviewElementsFlow] iff there
   * is a pending refresh to be done. These filtered [T]s are already sorted.
   */
  val toRenderPreviewElementsFlow: StateFlow<FlowableCollection<T>>

  /**
   * Flow containing all the [T]s that have completed rendering. These are all the
   * [toRenderPreviewElementsFlow] that have rendered.
   *
   * This flow must be updated by calling [updateRenderedPreviews].
   */
  val renderedPreviewElementsFlow: StateFlow<FlowableCollection<T>>

  /**
   * Selects a single [T] preview element. If the value is non-null, then
   * [toRenderPreviewElementsFlow] will be a flow of a singleton containing that preview element. If
   * the value is null, then the single filter is removed.
   */
  fun setSingleFilter(previewElement: T?)

  /**
   * Updates the value of [renderedPreviewElementsFlow] with the given list of [T]s.
   *
   * @see renderedPreviewElementsFlow
   */
  fun updateRenderedPreviews(previewElements: List<T>)

  companion object {
    val KEY = DataKey.create<PreviewFlowManager<*>>("PreviewFlowManager")
  }
}
