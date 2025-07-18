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
package com.android.tools.idea.wear.preview

import com.android.tools.idea.common.model.NlDataProvider
import com.android.tools.idea.common.model.NlDataProviderHolder
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.wear.preview.WearTilePreviewElement
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.ApplicationRule
import org.junit.After
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

private fun wearTilePreviewElement(
  methodFqn: String,
  displaySettings: PreviewDisplaySettings = simplestDisplaySettings(),
  previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>? = null,
  previewBodyPsi: SmartPsiElementPointer<PsiElement>? = null,
) =
  WearTilePreviewElement(
    displaySettings = displaySettings,
    previewElementDefinition = previewElementDefinitionPsi,
    previewBody = previewBodyPsi,
    methodFqn = methodFqn,
    configuration = PreviewConfiguration.cleanAndGet(device = "id:wearos_small_round"),
  )

private class TestModel(override var dataProvider: NlDataProvider?) : NlDataProviderHolder {
  override fun dispose() {}
}

private fun simplestDisplaySettings(name: String = "") =
  PreviewDisplaySettings(
    name = name,
    baseName = name,
    parameterName = null,
    group = null,
    showDecoration = false,
    showBackground = false,
    backgroundColor = null,
    organizationName = "organizationName",
    organizationGroup = "organizationGroup",
  )

class WearTilePreviewElementModelAdapterTest {
  private val rootDisposable = Disposer.newDisposable()
  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun testModelAndPreviewElementConnection() {
    val adapter = WearTilePreviewElementModelAdapter<TestModel>()

    val element = wearTilePreviewElement(methodFqn = "foo")

    val model = TestModel(adapter.createDataProvider(element))
    Disposer.register(rootDisposable, model)

    Assert.assertEquals(element, adapter.modelToElement(model))

    Disposer.dispose(model)

    Assert.assertNull(adapter.modelToElement(model))
  }

  @Test
  fun testWearTilesXml() {
    Assert.assertEquals(
      """<androidx.wear.tiles.tooling.TileServiceViewAdapter
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#ff000000"
    android:minWidth="1px"
    android:minHeight="1px"
    tools:tilePreviewMethodFqn="foo" />

"""
        .trimIndent(),
      WearTilePreviewElementModelAdapter<TestModel>()
        .toXml(
          WearTilePreviewElement(
            displaySettings = simplestDisplaySettings(),
            previewElementDefinition = null,
            previewBody = null,
            methodFqn = "foo",
            configuration = PreviewConfiguration.cleanAndGet(device = "id:wearos_small_round"),
          )
        ),
    )
  }

  @After
  fun tearDown() {
    Disposer.dispose(rootDisposable)
  }
}
