/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.preview.ConfigurablePreviewElement
import com.android.tools.preview.DisplayPositioning
import com.android.tools.preview.MethodPreviewElement
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.PreviewElementInstance
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

class TestBasePreviewElement<T>(
  displayName: String = "",
  baseName: String = "",
  parameterName: String? = null,
  override val methodFqn: String = "TestMethod",
  groupName: String? = null,
  showDecorations: Boolean = false,
  showBackground: Boolean = false,
  backgroundColor: String? = null,
  displayPositioning: DisplayPositioning = DisplayPositioning.NORMAL,
  override val instanceId: String = methodFqn,
  override val configuration: PreviewConfiguration = PreviewConfiguration.cleanAndGet(),
  override val previewElementDefinition: T? = null,
) : MethodPreviewElement<T>, ConfigurablePreviewElement<T>, PreviewElementInstance<T> {
  override val hasAnimations = false
  override val displaySettings =
    PreviewDisplaySettings(
      name = displayName,
      baseName = baseName,
      parameterName = parameterName,
      group = groupName,
      showDecoration = showDecorations,
      showBackground = showBackground,
      backgroundColor = backgroundColor,
      displayPositioning = displayPositioning,
      organizationGroup = "",
      organizationName = "",
    )
  override val previewBody = null

  override fun createDerivedInstance(
    displaySettings: PreviewDisplaySettings,
    config: PreviewConfiguration,
  ) =
    TestBasePreviewElement(
      displayName = displaySettings.name,
      baseName = displaySettings.baseName,
      parameterName = displaySettings.parameterName,
      methodFqn = methodFqn,
      groupName = displaySettings.group,
      showDecorations = displaySettings.showDecoration,
      showBackground = displaySettings.showBackground,
      backgroundColor = displaySettings.backgroundColor,
      displayPositioning = displaySettings.displayPositioning,
      instanceId = instanceId,
      configuration = config,
      previewElementDefinition = previewElementDefinition,
    )
}

internal class TestMethodPreviewElement(
  override val methodFqn: String,
  override val displaySettings: PreviewDisplaySettings = someDisplaySettings(),
  override val previewElementDefinition: Unit? = null,
  override val previewBody: Unit? = null,
  override val hasAnimations: Boolean = false,
) : MethodPreviewElement<Unit>

typealias PsiTestPreviewElement = TestBasePreviewElement<SmartPsiElementPointer<PsiElement>>

typealias TestPreviewElement = TestBasePreviewElement<Unit>
