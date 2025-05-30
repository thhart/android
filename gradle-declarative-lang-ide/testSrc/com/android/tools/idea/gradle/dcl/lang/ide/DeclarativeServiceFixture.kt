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
package com.android.tools.idea.gradle.dcl.lang.ide

import com.android.tools.idea.gradle.dcl.lang.sync.BuildDeclarativeSchema
import com.android.tools.idea.gradle.dcl.lang.sync.convert
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.replaceService
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.serialization.SchemaSerialization
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.io.File
val TEST_DATA_RELATIVE_PATH = "tools/adt/idea/gradle-declarative-lang-ide/testData/schemas"
val TEST_PATCHED_DATA_RELATIVE_PATH = "tools/adt/idea/gradle-declarative-lang-ide/testData/patchedSchemas"

internal fun createTestDeclarativeSchemas(path: String): BuildDeclarativeSchemas {
  val folder = File(FileUtil.toSystemDependentName(path))
  val children = folder.list()

  val projectSchemas = mutableSetOf<BuildDeclarativeSchema>()
  val settingsSchemas = mutableSetOf<BuildDeclarativeSchema>()

  children?.forEach { fileName ->
    val file = File(folder, fileName)
    val analysisSchema: AnalysisSchema = SchemaSerialization.schemaFromJsonString(file.readText())
    val ideSchema = analysisSchema.convert()
    if (fileName.startsWith("settings"))
      settingsSchemas.add(ideSchema)
    else
      projectSchemas.add(ideSchema)
  }

  return BuildDeclarativeSchemas(settingsSchemas, projectSchemas)
}

fun registerTestDeclarativeService(project: Project, disposable: Disposable) {
  registerTestDeclarativeService(project, TEST_DATA_RELATIVE_PATH, disposable)
}

fun registerTestDeclarativeServicePatchedSchema(project: Project, disposable: Disposable) {
  registerTestDeclarativeService(project, TEST_PATCHED_DATA_RELATIVE_PATH, disposable)
}

private fun registerTestDeclarativeService(project: Project, schemaPath: String, disposable: Disposable) {
  val mockService = mock(DeclarativeService::class.java)
  val schema = createTestDeclarativeSchemas(schemaPath)
  whenever(mockService.getDeclarativeSchema()).thenReturn(schema)
  project.replaceService(
    DeclarativeService::class.java, mockService, disposable
  )
}