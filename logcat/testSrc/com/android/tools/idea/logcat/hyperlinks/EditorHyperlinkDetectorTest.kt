/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.hyperlinks

import com.android.sdklib.AndroidApiLevel
import com.android.tools.idea.logcat.LogcatConsoleFilterProvider
import com.android.tools.idea.logcat.testing.LogcatEditorRule
import com.android.tools.idea.logcat.util.waitForCondition
import com.android.tools.idea.testing.WaitForIndexRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.Result
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.UrlFilter
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.registerExtension
import org.junit.Rule
import org.junit.Test

/** Tests for [editorHyperlinkDetector] */
@RunsInEdt
class EditorHyperlinkDetectorTest {
  private val projectRule = ProjectRule()
  private val logcatEditorRule = LogcatEditorRule(projectRule)
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      WaitForIndexRule(projectRule),
      logcatEditorRule,
      disposableRule,
      EdtRule(),
    )

  private val project
    get() = projectRule.project

  private val editor
    get() = logcatEditorRule.editor

  /**
   * Tests that we are using the correct filter as provided by
   * ConsoleViewUtil.computeConsoleFilters(). This is a CompositeFilter that wraps a set of filters
   * provided by the IDEA.
   */
  @Test
  fun usesCorrectFilters_containsAllConsoleFilters() {
    val expectedFilters =
      ConsoleViewUtil.computeConsoleFilters(
        project,
        /* consoleView= */ null,
        GlobalSearchScope.allScope(project),
      )

    val hyperlinkDetector = editorHyperlinkDetector(editor)

    val expected = expectedFilters.map { it::class }
    waitForCondition {
      hyperlinkDetector.filter.compositeFilter.filters.map { it::class }.containsAll(expected)
    }
  }

  /** Tests that we include filters from the extensions. */
  @Test
  fun usesCorrectFilters_containsFiltersFromExtensions() {
    val filter = Filter { _, _ -> null }
    val ext =
      object : LogcatConsoleFilterProvider {
        override fun create(editor: EditorEx): Filter {
          return filter
        }
      }
    ApplicationManager.getApplication()
      .registerExtension(LogcatConsoleFilterProvider.EP_NAME, ext, disposableRule.disposable)

    val hyperlinkDetector = editorHyperlinkDetector(editor)
    waitForCondition {
      hyperlinkDetector.filter.compositeFilter.filters.map { it::class }.contains(filter::class)
    }
  }

  /** Tests that we are always using the SimpleFileLink filter. */
  @Test
  fun usesCorrectFilters_containsSimpleFileLinkFilter() {
    val consoleFilters =
      ConsoleViewUtil.computeConsoleFilters(
        project,
        /* consoleView= */ null,
        GlobalSearchScope.allScope(project),
      )
    val expected = consoleFilters.map { it::class } + SimpleFileLinkFilter::class

    val hyperlinkDetector = editorHyperlinkDetector(editor)

    waitForCondition {
      hyperlinkDetector.filter.compositeFilter.filters.map { it::class }.containsAll(expected)
    }
    assertThat(hyperlinkDetector.filter.compositeFilter.filters.map { it::class })
      .containsAllIn(expected)
      .inOrder()
  }

  /**
   * Tests that we actually detect a hyperlink and add to the editor.
   *
   * The easiest hyperlink type to test is a URL which is one of the filters injected by the IDEA.
   */
  @Test
  fun detectHyperlinks() {
    editor.document.setText("http://www.google.com")
    val hyperlinkSupport = EditorHyperlinkSupport.get(editor)

    val detector = editorHyperlinkDetector(editor)
    waitForCondition { detector.filter.compositeFilter.filters.any { it is UrlFilter } }
    detector.detectHyperlinks(0, editor.document.lineCount - 1, sdk = null)

    hyperlinkSupport.waitForPendingFilters(/* timeoutMs= */ 5000)
    assertThat(
        hyperlinkSupport.findAllHyperlinksOnLine(0).map {
          editor.document.text.substring(it.startOffset, it.endOffset)
        }
      )
      .containsExactly("http://www.google.com")
  }

  @Test
  fun detectHyperlinks_usesAllFilters() {
    editor.document.setText("Foo Bar")
    val hyperlinkSupport = EditorHyperlinkSupport.get(editor)
    val editorHyperlinkDetector = editorHyperlinkDetector(editor)
    val filters = (editorHyperlinkDetector.filter.compositeFilter)
    filters.addFilter(TestFilter("Foo"))
    filters.addFilter(TestFilter("Bar"))

    editorHyperlinkDetector.detectHyperlinks(0, editor.document.lineCount - 1, sdk = null)

    hyperlinkSupport.waitForPendingFilters(/* timeoutMs= */ 5000)
    assertThat(
        hyperlinkSupport.findAllHyperlinksOnLine(0).map {
          editor.document.text.substring(it.startOffset, it.endOffset)
        }
      )
      .containsExactly("Foo", "Bar")
  }

  @Test
  fun detectHyperlinks_passesSdk() {
    val editorHyperlinkDetector = editorHyperlinkDetector(editor)

    editorHyperlinkDetector.detectHyperlinks(
      0,
      editor.document.lineCount - 1,
      sdk = AndroidApiLevel(23),
    )

    assertThat(editorHyperlinkDetector.filter.apiLevel).isEqualTo(AndroidApiLevel(23))
  }

  private class TestFilter(private val text: String) : Filter {
    override fun applyFilter(line: String, entireLength: Int): Result? {
      val start = line.indexOf(text)
      if (start < 0) {
        return null
      }

      return Result(start, start + text.length, Info())
    }

    private class Info : HyperlinkInfo {
      override fun navigate(project: Project) {}
    }
  }

  private fun editorHyperlinkDetector(editor: EditorEx) =
    EditorHyperlinkDetector(
      project,
      editor,
      disposableRule.disposable,
      ModalityState.any(),
      MoreExecutors.newDirectExecutorService(),
    )
}
