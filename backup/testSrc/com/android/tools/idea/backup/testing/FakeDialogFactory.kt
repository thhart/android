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

package com.android.tools.idea.backup.testing

import com.android.testutils.waitForCondition
import com.android.tools.idea.backup.DialogFactory
import com.android.tools.idea.backup.DialogFactory.DialogButton
import com.intellij.openapi.project.Project
import kotlin.time.Duration.Companion.seconds

internal class FakeDialogFactory : DialogFactory {
  val dialogs = mutableListOf<DialogData>()

  override fun showDialog(
    project: Project,
    title: String,
    message: String,
    buttons: List<DialogButton>,
  ) {
    dialogs.add(DialogData(title, message, buttons.map { it.text }))
  }

  fun waitForDialogs(num: Int) {
    waitForCondition(5.seconds) { dialogs.size == num }
  }

  data class DialogData(
    val title: String,
    val message: String,
    val buttons: List<String> = emptyList(),
  )
}
