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
package com.android.tools.idea.run.activity.launch

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.AnAction
import javax.swing.JComponent

class EmptyTestConsoleView : ConsoleView {
  val printedMessages = mutableListOf<Pair<String, ConsoleViewContentType>>()
  override fun print(s: String, contentType: ConsoleViewContentType) {
    printedMessages.add(Pair(s, contentType))
  }
  override fun clear() {}
  override fun scrollTo(offset: Int) {}
  override fun attachToProcess(processHandler: ProcessHandler) {}
  override fun setOutputPaused(value: Boolean) {}
  override fun isOutputPaused() = false
  override fun hasDeferredOutput() = false
  override fun performWhenNoDeferredOutput(runnable: Runnable) {}
  override fun setHelpId(helpId: String) {}
  override fun addMessageFilter(filter: Filter) {}
  override fun printHyperlink(hyperlinkText: String, info: HyperlinkInfo?) {}
  override fun getContentSize() = 0
  override fun canPause() = false
  override fun createConsoleActions() = AnAction.EMPTY_ARRAY
  override fun allowHeavyFilters() {}
  override fun getComponent(): JComponent {
    return object : JComponent() {}
  }

  override fun getPreferredFocusableComponent(): JComponent {
    return object : JComponent() {}
  }

  override fun dispose() {}
}