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
package com.android.tools.idea.preview

import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.model.NlDataProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import kotlin.test.assertEquals
import org.junit.Assert
import org.junit.Test

private class TextAdapter(private val modelsToElements: Map<Any, TestPreviewElement?>) :
  PreviewElementModelAdapter<TestPreviewElement, Any> {

  override fun modelToElement(model: Any) = modelsToElements[model]

  override fun toXml(previewElement: TestPreviewElement) = ""

  override fun applyToConfiguration(
    previewElement: TestPreviewElement,
    configuration: Configuration,
  ) {}

  override fun createDataProvider(previewElement: TestPreviewElement): NlDataProvider? = null

  override fun toLogString(previewElement: TestPreviewElement) = ""

  override fun createLightVirtualFile(content: String, backedFile: VirtualFile, id: Long) =
    LightVirtualFile()
}

class PreviewElementAffinityTest {

  @Test
  fun testCalcAffinityPriority() {
    val pe1 = TestMethodPreviewElement(methodFqn = "foo")
    val pe2 = TestMethodPreviewElement(methodFqn = "foo")
    val pe3 = TestMethodPreviewElement(methodFqn = "foo", someDisplaySettings(name = "foo"))
    val pe4 = TestMethodPreviewElement(methodFqn = "bar")

    Assert.assertTrue(calcAffinity(pe1, pe1) < calcAffinity(pe1, pe2))
    Assert.assertTrue(calcAffinity(pe1, pe2) < calcAffinity(pe1, pe3))
    Assert.assertTrue(calcAffinity(pe1, pe3) < calcAffinity(pe1, null))
    Assert.assertTrue(calcAffinity(pe1, null) == calcAffinity(pe1, pe4))
  }

  @Test
  fun testMatchElementsToModels() {
    val model1 = Any()
    val model2 = Any()
    val model3 = Any()

    val element1 = TestPreviewElement("foo")
    val element11 = TestPreviewElement("foo")
    val element2 = TestPreviewElement("bar")
    val element3 = TestPreviewElement("baz")
    val element31 = TestPreviewElement("baz")
    val element4 = TestPreviewElement("qwe")

    run {
      val modelsToElements = mapOf(model1 to element3, model2 to element1, model3 to element2)

      val adapter = TextAdapter(modelsToElements)

      val result =
        matchElementsToModels(
          listOf(model1, model2, model3),
          listOf(element1, element2, element3),
          adapter,
        )

      assertEquals(listOf(1, 2, 0), result)
    }

    run {
      val modelsToElements = mapOf(model1 to element1, model2 to element2, model3 to element3)

      val adapter = TextAdapter(modelsToElements)

      val result =
        matchElementsToModels(listOf(model2, model3), listOf(element1, element2, element3), adapter)

      assertEquals(listOf(-1, 0, 1), result)
    }

    run {
      val modelsToElements = mapOf(model1 to element1, model2 to element2, model3 to element3)

      val adapter = TextAdapter(modelsToElements)

      val result =
        matchElementsToModels(listOf(model1, model2, model3), listOf(element2, element3), adapter)

      assertEquals(listOf(1, 2), result)
    }

    run {
      val modelsToElements = mapOf(model1 to element4, model2 to element31, model3 to element11)

      val adapter = TextAdapter(modelsToElements)

      val result =
        matchElementsToModels(
          listOf(model1, model2, model3),
          listOf(element1, element2, element3),
          adapter,
        )

      assertEquals(listOf(2, 0, 1), result)
    }

    run {
      val modelsToElements = mapOf(model1 to element1, model2 to null)

      val adapter = TextAdapter(modelsToElements)

      val result =
        matchElementsToModels(listOf(model1, model2), listOf(element1, element2), adapter)

      assertEquals(listOf(0, 1), result)
    }

    run {
      val adapter = TextAdapter(mapOf())

      val result = matchElementsToModels(listOf(), listOf(element1), adapter)

      assertEquals(listOf(-1), result)
    }
  }

  @Test
  fun testMatchElementsToModels_stress() {
    val nModels = 10_000
    val nElements = 20_000

    val models = List(nModels) { Any() }
    val elements = List(nElements) { TestPreviewElement("$it") }
    val modelsToElements = models.zip(elements.shuffled()).toMap()
    val adapter = TextAdapter(modelsToElements)

    val result = matchElementsToModels(models, elements, adapter)

    val expectedResult = List(nModels) { it } + List(nElements - nModels) { -1 }

    // Compare them as multisets
    assertEquals(result.sorted(), expectedResult.sorted())
  }
}
