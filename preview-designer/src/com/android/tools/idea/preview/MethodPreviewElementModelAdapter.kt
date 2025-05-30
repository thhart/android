/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.preview

import com.android.tools.idea.common.model.NlDataProvider
import com.android.tools.idea.common.model.NlDataProviderHolder
import com.android.tools.idea.common.model.NlModel
import com.android.tools.preview.MethodPreviewElement
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.Disposer

/** Base model adapter for [MethodPreviewElement]s. */
abstract class MethodPreviewElementModelAdapter<
  T : MethodPreviewElement<*>,
  M : NlDataProviderHolder,
  >(private val elementKey: DataKey<T>) : PreviewElementModelAdapter<T, M> {

  override fun modelToElement(model: M): T? =
    if (!Disposer.isDisposed(model)) {
      model.dataProvider?.getData(elementKey) as? T
    } else null

  /**
   * Creates a [DataContext] that is when assigned to [NlModel] can be retrieved with
   * [modelToElement] call against that model.
   */
  override fun createDataProvider(previewElement: T): NlDataProvider? =
    object : NlDataProvider(elementKey) {
      override fun getData(dataId: String): Any? =
        previewElement.takeIf { dataId == elementKey.name }
    }

  override fun toLogString(previewElement: T): String =
    """
        displayName=${previewElement.displaySettings.name}
        methodName=${previewElement.methodFqn}
  """
      .trimIndent()
}
