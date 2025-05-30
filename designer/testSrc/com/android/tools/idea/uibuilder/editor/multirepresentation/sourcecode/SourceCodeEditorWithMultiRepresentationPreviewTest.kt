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
package com.android.tools.idea.uibuilder.editor.multirepresentation.sourcecode

import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.editor.multirepresentation.TestPreviewRepresentationProvider
import com.android.tools.idea.uibuilder.editor.multirepresentation.TextEditorWithMultiRepresentationPreview
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test

class SourceCodeEditorWithMultiRepresentationPreviewTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  // Regression test for b/254613979
  @Test
  fun testNavigationMovesToSplitMode() = runBlocking {
    val file = fixture.addFileToProject("src/Preview.kt", "")
    val editorProvider =
      SourceCodeEditorProvider.forTesting(
        listOf(TestPreviewRepresentationProvider("Representation1", true))
      )
    val editor =
      withContext(uiThread) {
        (editorProvider.createEditor(file.project, file.virtualFile)
            as TextEditorWithMultiRepresentationPreview<*>)
          .also { Disposer.register(projectRule.testRootDisposable, it) }
      }

    // Wait for representations to be fully initialized
    waitUntil { editor.preview.representationNames.isNotEmpty() }

    // ChangeEditorSplitActions in IntelliJ 2024.1 uses the data provider to find about the editor.
    // Here, we inject the "open" editor so it finds it during testing.
    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider(
      {
        when (it) {
          PlatformCoreDataKeys.FILE_EDITOR.name -> editor
          else -> null
        }
      },
      fixture.testRootDisposable,
    )

    withContext(uiThread) {
      // Check we are in design mode before navigation is triggered
      editor.selectDesignMode(false)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    waitUntil { editor.isDesignMode() }
    withContext(uiThread) {
      editor.navigateTo(OpenFileDescriptor(projectRule.project, file.virtualFile, 0))
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
    waitUntil("The editor was expected to switch to split mode when navigating to source") {
      editor.isSplitMode()
    }
  }
}
