/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.hprof.analysis

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ProjectManager

class LiveInstanceStats {
  fun createReport(): String = buildString {
    // Count open projects
    val openProjects = ProjectManager.getInstance().openProjects
    val projectsOpenCount = openProjects.size

    appendLine("Projects open: $projectsOpenCount")
    openProjects.forEachIndexed { projectIndex, project ->
      appendLine("Project ${projectIndex + 1}:")

      val modulesCount = ModuleManager.getInstance(project).modules.count()
      appendLine("  Module count: $modulesCount")

      val allEditors = FileEditorManager.getInstance(project).allEditors
      val typeToCount = allEditors.groupingBy { "${it.javaClass.name}[${it.file?.fileType?.javaClass?.name}]" }.eachCount()
      appendLine("  Editors opened: ${allEditors.size}. Counts by type:")
      typeToCount.entries.sortedByDescending { it.value }.forEach { (typeString, count) ->
        appendLine("   * $count $typeString")
      }
      appendLine()
    }
  }
}