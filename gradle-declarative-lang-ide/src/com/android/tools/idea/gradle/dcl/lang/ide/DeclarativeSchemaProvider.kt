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

import com.android.tools.idea.gradle.dcl.lang.flags.DeclarativeIdeSupport
import com.android.tools.idea.gradle.dcl.lang.sync.AugmentationKind
import com.android.tools.idea.gradle.dcl.lang.sync.BuildDeclarativeSchema
import com.android.tools.idea.gradle.dcl.lang.sync.ClassType
import com.android.tools.idea.gradle.dcl.lang.sync.Entry
import com.android.tools.idea.gradle.dcl.lang.sync.FullName
import com.android.tools.idea.gradle.dcl.lang.sync.GradleSchemaProjectResolver
import com.android.tools.idea.gradle.dcl.lang.sync.SchemaFunction
import com.intellij.ide.troubleshooting.scale
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.Serializable
@Service(Service.Level.PROJECT)
class DeclarativeService(val project: Project) {

  companion object {
    fun getInstance(project: Project) = project.service<DeclarativeService>()
  }

  fun getDeclarativeSchema(): BuildDeclarativeSchemas? {
      if (!DeclarativeIdeSupport.isEnabled()) return null
      val externalProjectPath: String = project.basePath ?: return null
      val projectInfo = ProjectDataManager.getInstance()
        .getExternalProjectData(project, GradleConstants.SYSTEM_ID, externalProjectPath)
      val projectStructure = projectInfo?.externalProjectStructure ?: return null
      val projectSchemas = ExternalSystemApiUtil.find(projectStructure, GradleSchemaProjectResolver.DECLARATIVE_PROJECT_SCHEMAS)
      val settingsSchemas = ExternalSystemApiUtil.find(projectStructure, GradleSchemaProjectResolver.DECLARATIVE_SETTINGS_SCHEMAS)
      return if (projectSchemas != null && settingsSchemas != null)
        BuildDeclarativeSchemas(
          settingsSchemas.data.settings,
          projectSchemas.data.projects
        )
      else null
  }
}

data class BuildDeclarativeSchemas(val settings: Set<BuildDeclarativeSchema>, val projects: Set<BuildDeclarativeSchema>) : Serializable {
  fun merge(schema: BuildDeclarativeSchemas) =
    BuildDeclarativeSchemas(this.settings + schema.settings, this.projects + schema.projects)

  fun getTopLevelEntries(fileName: String): List<EntryWithContext> =
    getSchemas(fileName).flatMap { schema -> schema.getRootEntries().map { EntryWithContext(it, schema) } }

  private fun getSchemas(fileName: String) =
    if (isSettings(fileName)) settings else projects

  fun getTopLevelEntriesByName(name: String, fileName: String): List<EntryWithContext> =
    getSchemas(fileName).flatMap { schema ->
      schema.getRootEntries { existingName: String -> name == existingName }.map {
        EntryWithContext(it, schema)
      }
    }

  fun getAugmentedTypes(fileName: String): Map<FullName, List<AugmentationKind>> =
    getSchemas(fileName).flatMap { schema -> schema.augmentedTypes.map { it.toPair() } }.toMap()

  fun getInfixFunctions(fileName: String): Map<String, SchemaFunction> =
    getSchemas(fileName).flatMap { schema -> schema.infixFunctionsMap.map { it.toPair() } }.toMap()

  private fun isSettings(name: String) = name == "settings.gradle.dcl"
}

data class EntryWithContext(val entry: Entry, val schema: BuildDeclarativeSchema) {
  fun resolveRef(fqName: FullName): ClassType? = schema.resolveRef(fqName)

  fun getNextLevel(name: String): List<EntryWithContext> = entry.getNextLevel(schema, name).map {
    EntryWithContext(it, schema)
  }

  fun getNextLevel(): List<EntryWithContext> = entry.getNextLevel(schema).map { EntryWithContext(it, schema) }
}
