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
package com.android.tools.idea.gradle.dependencies.runsGradleDependencies

import com.android.tools.idea.gradle.dependencies.CommonPluginsInserter
import com.android.tools.idea.gradle.dependencies.ExactDependencyMatcher
import com.android.tools.idea.gradle.dependencies.FalsePluginMatcher
import com.android.tools.idea.gradle.dependencies.GroupNameDependencyMatcher
import com.android.tools.idea.gradle.dependencies.IdPluginMatcher
import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig
import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig.MatchedStrategy
import com.android.tools.idea.gradle.dependencies.PluginsHelper
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpecImpl
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.BuildEnvironment
import com.android.tools.idea.testing.TestProjectPaths.MIGRATE_BUILD_CONFIG
import com.android.tools.idea.testing.TestProjectPaths.MINIMAL_CATALOG_APPLICATION
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_DECLARATIVE
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_PLUGINS_DSL
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG
import com.android.tools.idea.testing.TestProjectPaths.SINGLE_MODULE_APPLICATION
import com.android.tools.idea.testing.TestProjectPaths.SINGLE_MODULE_VERSION_CATALOG
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.getTextForFile
import com.android.tools.idea.testing.hasModule
import com.android.tools.idea.testing.withDeclarative
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import org.apache.commons.lang3.StringUtils.countMatches
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
class PluginsHelperTest{

  @get:Rule
  val projectRule = AndroidGradleProjectRule().withDeclarative()
  private val fixture get() = projectRule.fixture
  private val project get() = projectRule.project

  @Test
  fun testAddToBuildscriptWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { _, moduleModel, helper ->
             val updates = helper.addClasspathDependency("com.example.libs:lib2:1.0")
             assertThat(updates.size).isEqualTo(2)
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .contains("{ group = \"com.example.libs\", name = \"lib2\", version.ref = \"")
             assertThat(project.getTextForFile("build.gradle"))
               .contains("classpath libs.lib2")
           })
  }

  @Test
  fun testAddSupportedKotlinPlugins() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel!!

             val updates = helper.addPlugin("org.jetbrains.kotlin.android", "1.9.20", false, projectModel, moduleModel)
             assertThat(updates.size).isEqualTo(3)

             val updates2 = helper.addPlugin("org.jetbrains.kotlin.plugin.compose", "1.9.20", false, projectModel, moduleModel)
             assertThat(updates2.size).isEqualTo(3)

             val updates3 = helper.addPlugin("org.jetbrains.kotlin.multiplatform", "1.9.20", false, projectModel, moduleModel)
             assertThat(updates3.size).isEqualTo(3)
           },
           {
             val catalog = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(catalog).contains("kotlin = \"1.9.20\"")
             assertThat(catalog).contains("kotlin-android = { id = \"org.jetbrains.kotlin.android\", version.ref = \"kotlin\" }")
             assertThat(catalog).contains("kotlin-compose = { id = \"org.jetbrains.kotlin.plugin.compose\", version.ref = \"kotlin\" }")
             assertThat(catalog).contains(
               "kotlin-multiplatform = { id = \"org.jetbrains.kotlin.multiplatform\", version.ref = \"kotlin\" }")

             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).contains("alias(libs.plugins.kotlin.android) apply false")
             assertThat(projectBuildContent).contains("alias(libs.plugins.kotlin.compose) apply false")
             assertThat(projectBuildContent).contains("alias(libs.plugins.kotlin.multiplatform) apply false")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).contains("alias(libs.plugins.kotlin.android)")
             assertThat(buildFileContent).contains("alias(libs.plugins.kotlin.compose)")
             assertThat(buildFileContent).contains("alias(libs.plugins.kotlin.multiplatform)")
           })
  }

  @Test
  fun testKotlinPluginsToSingleModuleNoCatalog() {
    doTest(SINGLE_MODULE_APPLICATION,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel!!

             val updates = helper.addPlugin("org.jetbrains.kotlin.jvm", "1.9.22", false, projectModel, moduleModel)
             assertThat(updates.size).isEqualTo(1)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).contains("id 'org.jetbrains.kotlin.jvm' version '1.9.22'")
           })
  }

  @Test
  fun testKotlinPluginsToSingleModuleVersionCatalog() {
    doTest(SINGLE_MODULE_VERSION_CATALOG,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel!!

             val updates = helper.addPlugin("org.jetbrains.kotlin.jvm", "1.9.22", false, projectModel, moduleModel)
             assertThat(updates.size).isEqualTo(2)
           },
           {
             val catalog = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(catalog).contains("jetbrains-kotlin-jvm = \"1.9.22\"")
             assertThat(catalog).contains("jetbrains-kotlin-jvm = { id = \"org.jetbrains.kotlin.jvm\", version.ref = \"jetbrains-kotlin-jvm\" }")

             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).contains("alias(libs.plugins.jetbrains.kotlin.jvm)")
           })
  }

  @Test
  fun testAddAndroidKotlinMultiplatformPlugin() {
    val env = BuildEnvironment.getInstance()
    val version = env.gradlePluginVersion
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel!!
             val updates = helper.addPlugin("com.android.kotlin.multiplatform.library", version, false, projectModel, moduleModel)
             assertThat(updates.size).isEqualTo(3)
           },
           {
             val catalog = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(catalog).contains("agp = \"${version}\"")
             assertThat(catalog).contains(
               "android-kotlin-multiplatform-library = { id = \"com.android.kotlin.multiplatform.library\", version.ref = \"agp\" }")
           })
  }

  @Test
  fun testAddToClasspathNoCatalog() {
    doTest(SIMPLE_APPLICATION,
           { _, _, helper ->
             val updates = helper.addClasspathDependency("com.example.libs:lib2:1.0")
             assertThat(updates.size).isEqualTo(1)
           },
           {
             assertThat(project.getTextForFile("build.gradle"))
               .contains("classpath \'com.example.libs:lib2:1.0\'")
           })
  }

  @Test
  fun testAddToBuildScriptWithExceptions() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { _, moduleModel, helper ->
             val updates = helper.addClasspathDependency(
               "com.example.libs:lib2:1.0",
               listOf(ArtifactDependencySpecImpl.create("com.example.libs:lib3")!!),
               ExactDependencyMatcher("classpath", "com.example.libs:lib2:1.0"),
             )
             assertThat(updates.size).isEqualTo(2)
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .contains("{ group = \"com.example.libs\", name = \"lib2\", version.ref = \"")
             val buildFileContent = project.getTextForFile("build.gradle")
             assertThat(buildFileContent).contains("classpath libs.lib2")
             assertThat(buildFileContent).contains("exclude group: 'com.example.libs', module: 'lib3'")
           })
  }


  @Test
  fun testSimpleAddPlugin() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val updates = helper.addPlugin("com.example.foo", "10.0", false, projectModel!!, moduleModel)
             assertThat(updates.size).isEqualTo(3)
           },
           {
             val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(catalogContent).contains("example-foo = \"10.0\"")
             assertThat(catalogContent).contains("example-foo = { id = \"com.example.foo\", version.ref = \"example-foo\"")

             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).contains("alias(libs.plugins.example.foo) apply false")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).contains("alias(libs.plugins.example.foo)")
           })
  }

  @Test
  fun testSimpleAddPluginNoCatalog() {
    doTest(SIMPLE_APPLICATION,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val updates = helper.addPlugin("com.example.foo", "10.0", false, projectModel!!, moduleModel)
             assertThat(updates.size).isEqualTo(2)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).contains("id 'com.example.foo' version '10.0' apply false")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).contains("apply plugin: 'com.example.foo'")
           })
  }

  @Test
  fun testSimpleAddPluginWithExistingPlugin() {
    val env = BuildEnvironment.getInstance()
    val version = env.gradlePluginVersion
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           {
             FileUtil.appendToFile(
               File(project.basePath, "gradle/libs.versions.toml"),
               "\n[plugins]\nexample = \"com.android.application:${version}\"\n"
             )
           },
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val updates = helper.addPlugin("com.android.application",
                                            version,
                                            false,
                                            projectModel!!,
                                            moduleModel,
                                            IdPluginMatcher("com.android.application"))
             assertThat(updates.size).isEqualTo(0)
           },
           {
             val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(catalogContent).doesNotContain("agp = \"${version}\"") // no version
             assertThat(catalogContent).doesNotContain("androidApplication = { id = \"com.android.application\"")

             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("alias(libs.plugins.androidApplication)") // no new alias

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).doesNotContain("alias(libs.plugins.androidApplication)")
           })
  }

  @Test
  fun testAddPluginToModuleWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val changed = helper.addPluginToModule("com.example.foo", "10.0", moduleModel)
             assertThat(changed.size).isEqualTo(2)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("example")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).contains("alias(libs.plugins.example.foo)")
           })
  }

  @Test
  fun testAddPluginSettingsWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectModel, buildModel, helper ->
             val plugins = projectModel.projectSettingsModel!!.pluginManagement().plugins()
             val changed = helper.addPlugin("com.example.foo", "10.0", false, plugins, buildModel)
             assertThat(changed.size).isEqualTo(2) // versions.toml and build.gradle
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("example.foo")

             val moduleBuildContent = project.getTextForFile("app/build.gradle")
             assertThat(moduleBuildContent).contains("example.foo")

             val settingsBuildContent = project.getTextForFile("settings.gradle")
             assertThat(settingsBuildContent).doesNotContain("example.foo")

             val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(catalogContent).contains("com.example.foo")
           })
  }

  @Test
  fun testAddPluginSettings() {
    doTest(SIMPLE_APPLICATION,
           { projectModel, buildModel, helper ->
             val plugins = projectModel.projectSettingsModel!!.pluginManagement().plugins()
             val changed = helper.addPlugin("com.example.foo", "10.0", false, plugins, buildModel)
             assertThat(changed.size).isEqualTo(2)
           },
           {
             val projectBuildContent = project.getTextForFile("app/build.gradle")
             assertThat(projectBuildContent).contains("example.foo")

             val settingsBuildContent = project.getTextForFile("settings.gradle")
             val pluginsBlockContent = getBlockContent(settingsBuildContent, "pluginManagement.plugins")
             assertThat(pluginsBlockContent).contains("'com.example.foo' version '10.0'")
           })
  }

  @Test
  fun testAddPluginToSettingsWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { _, _, helper ->
             val changed = helper.applySettingsPlugin("com.example.foo", "10.0")
             assertThat(changed.size).isEqualTo(1)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("example")

             val settingsBuildContent = project.getTextForFile("settings.gradle")
             val pluginsBlockContent = getBlockContent(settingsBuildContent, "plugins")
             assertThat(pluginsBlockContent).contains("com.example.foo")
             assertThat(settingsBuildContent).doesNotContain("libs.plugins.example.foo")

             val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(catalogContent).doesNotContain("com.example.foo")
           })
  }

  @Test
  fun testAddPluginToDeclarativeSettings() {
    doTest(SIMPLE_APPLICATION_DECLARATIVE,
                      { _, _, helper ->
                        val changed = helper.applySettingsPlugin("com.example.foo", "10.0")
                        assertThat(changed.size).isEqualTo(1)
                      },
                      {
                        val projectBuildContent = project.getTextForFile("app/build.gradle.dcl")
                        assertThat(projectBuildContent).doesNotContain("example")

                        val settingsBuildContent = project.getTextForFile("settings.gradle.dcl")
                        val pluginsBlockContent = getBlockContent(settingsBuildContent, "plugins")
                        assertThat(pluginsBlockContent).contains("id(\"com.example.foo\").version(\"10.0\")")
                        assertThat(settingsBuildContent).doesNotContain("libs.plugins.example.foo")
                      })
  }

  @Test
  fun testAddPluginToDeclarative() {
    doTest(SIMPLE_APPLICATION_DECLARATIVE,
                      { projectBuildModel, model, helper ->
                        val plugins = projectBuildModel.declarativeSettingsModel!!.plugins()
                        val changed = helper.addPlugin("com.example.foo", "10.0", null, plugins, model)
                        assertThat(changed.size).isEqualTo(1)
                      },
                      {
                        val projectBuildContent = project.getTextForFile("app/build.gradle.dcl")
                        assertThat(projectBuildContent).doesNotContain("example")

                        val settingsBuildContent = project.getTextForFile("settings.gradle.dcl")
                        val pluginsBlockContent = getBlockContent(settingsBuildContent, "plugins")
                        assertThat(pluginsBlockContent).contains("id(\"com.example.foo\").version(\"10.0\")")
                        assertThat(settingsBuildContent).doesNotContain("libs.plugins.example.foo")
                      })
  }

  @Test
  fun testAddPluginToSettingsPluginManagementWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectBuildModel, _, helper ->
             val pluginsModel = projectBuildModel.projectSettingsModel?.pluginManagement()?.plugins()
             assertThat(pluginsModel).isNotNull()
             val changed = helper.declarePluginInPluginManagement("com.example.foo", "10.0", null, pluginsModel!!)
             assertThat(changed.size).isEqualTo(1)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("example")

             val settingsBuildContent = project.getTextForFile("settings.gradle")
             val pluginsBlockContent = getBlockContent(settingsBuildContent, "pluginManagement.plugins")
             assertThat(pluginsBlockContent).contains("com.example.foo")
             assertThat(settingsBuildContent).doesNotContain("libs.plugins.example.foo")

             val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(catalogContent).doesNotContain("com.example.foo")
           })
  }

  @Test
  fun testAddPluginToModuleWithNoCatalog() {
    doTest(SIMPLE_APPLICATION,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val changed = helper.addPluginToModule("com.example.foo", "10.0", moduleModel)
             assertThat(changed.size).isEqualTo(1)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("example")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).contains("apply plugin: 'com.example.foo'")
           })
  }

  @Test
  fun testAddPluginToSettingsWithNoCatalog() {
    doTest(SIMPLE_APPLICATION,
           { _, _, helper ->
             val changed = helper.applySettingsPlugin("com.example.foo", "10.0")
             assertThat(changed.size).isEqualTo(1)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("example")

             val settingsBuildContent = project.getTextForFile("settings.gradle")
             val pluginsBlockContent = getBlockContent(settingsBuildContent, "plugins")
             assertThat(pluginsBlockContent).contains("id 'com.example.foo' version '10.0'")
             assertThat(settingsBuildContent).doesNotContain("libs.plugins.example.foo")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).doesNotContain("com.example.foo")
           })
  }

  @Test
  fun testAddPluginToSettingsPluginManagementWithNoCatalog() {
    doTest(SIMPLE_APPLICATION,
           { projectBuildModel, _, helper ->
             val pluginsModel = projectBuildModel.projectSettingsModel?.pluginManagement()?.plugins()
             assertThat(pluginsModel).isNotNull()
             val changed = helper.declarePluginInPluginManagement("com.example.foo", "10.0", null, pluginsModel!!)
             assertThat(changed.size).isEqualTo(1)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("example")

             val settingsBuildContent = project.getTextForFile("settings.gradle")
             val pluginsBlockContent = getBlockContent(settingsBuildContent, "pluginManagement.plugins")
             assertThat(pluginsBlockContent).contains("id 'com.example.foo' version '10.0'")
             assertThat(settingsBuildContent).doesNotContain("libs.plugins.example.foo")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).doesNotContain("com.example.foo")
           })
  }

  @Test
  fun testAddClasspathDuplicatedDependencyWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectBuildModel, _, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val matcher = GroupNameDependencyMatcher("classpath", "com.example.libs:lib2:1.0")

             helper.addClasspathDependency("com.example.libs:lib2:1.0", listOf(), matcher)
             helper.addClasspathDependency("com.example.libs:lib2:1.0", listOf(), matcher)
           },
           {
             val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(countMatches(catalogContent, "{ group = \"com.example.libs\", name = \"lib2\", version.ref = \"")).isEqualTo(1)

             val buildFileContent = project.getTextForFile("build.gradle")
             assertThat(countMatches(buildFileContent, "classpath libs.lib2")).isEqualTo(1)
           })
  }

  @Test
  fun testAddClasspathDuplicatedDependencyNoCatalog() {
    doTest(SIMPLE_APPLICATION,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val matcher = GroupNameDependencyMatcher("classpath", "com.example.libs:lib2:1.0")

             helper.addClasspathDependency("com.example.libs:lib2:1.0", listOf(), matcher)
             helper.addClasspathDependency("com.example.libs:lib2:1.0", listOf(), matcher)
           },
           {
             val buildFileContent = project.getTextForFile("build.gradle")
             assertThat(countMatches(buildFileContent, "classpath 'com.example.libs:lib2:1.0'")).isEqualTo(1)
           })
  }

  @Test
  fun testSimpleAddPluginNoCatalogWithExistingPlugin() {
    val env = BuildEnvironment.getInstance()
    val version = env.gradlePluginVersion
    doTest(SIMPLE_APPLICATION_PLUGINS_DSL,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val changed = helper.addPlugin("com.android.application",
                                            version,
                                            false,
                                            projectModel!!,
                                            moduleModel,
                                            IdPluginMatcher("com.android.application"))
             assertThat(changed.size).isEqualTo(0)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             // once test is imported it updates agp version and adds updates as comments on the bottom of file
             // this comment affects simple counter with countMatches
             val regex = "\\R\\s*id 'com.android.application' version '${version}' apply false".toRegex()
             assertThat(regex.findAll(projectBuildContent).toList().size).isEqualTo(1)

             val buildFileContent = project.getTextForFile("app/build.gradle")

             assertThat(countMatches(buildFileContent, "id 'com.android.application'")).isEqualTo(1)
           })
  }

  @Test
  fun testSmartAddPluginNoCatalog() {

    doTest(SIMPLE_APPLICATION,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()

             val changed = helper.addPluginOrClasspath("com.google.gms.google-services",
                                                       "com.google.gms:google-services",
                                                       "4.3.14",
                                                       listOf(moduleModel))
             assertThat(changed.size).isEqualTo(2)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             val regex = "\\R\\s*classpath 'com.google.gms:google-services:4.3.14'".toRegex()
             assertThat(regex.findAll(projectBuildContent).toList().size).isEqualTo(1)

             assertThat(project.doesFileExists("gradle/libs.versions.toml")).isFalse()
             assertThat(projectBuildContent).doesNotContain("plugins")
             assertThat(project.getTextForFile("settings.gradle")).doesNotContain("plugins")

             val buildFileContent = project.getTextForFile("app/build.gradle")

             assertThat(countMatches(buildFileContent, "apply plugin: 'com.google.gms.google-services'")).isEqualTo(1)
           })
  }

  @Test
  fun testSmartUpdatePluginVersionInClasspath() {
    doTest(SIMPLE_APPLICATION,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val pluginId = "com.google.gms.google-services"
             val classpathModule = "com.google.gms:google-services"
             // add first
             val changed = helper.addPluginOrClasspath(pluginId,
                                                       classpathModule,
                                                       "4.3.14",
                                                       listOf(moduleModel))
             assertThat(changed.size).isEqualTo(2)

             // then update
             val config = PluginInsertionConfig.defaultInsertionConfig().copy(whenFoundSame = MatchedStrategy.UPDATE_VERSION)
             val changed2 = helper.addPluginOrClasspath(pluginId,
                                                        classpathModule,
                                                        "4.3.15",
                                                        listOf(moduleModel),
                                                        config = config)
             assertThat(changed2.size).isEqualTo(1)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             val regex = "\\R\\s*classpath 'com.google.gms:google-services:4.3.15'".toRegex()
             assertThat(regex.findAll(projectBuildContent).toList().size).isEqualTo(1)

             assertThat(project.doesFileExists("gradle/libs.versions.toml")).isFalse()
             assertThat(projectBuildContent).doesNotContain("plugins")
             assertThat(project.getTextForFile("settings.gradle")).doesNotContain("plugins")

             val buildFileContent = project.getTextForFile("app/build.gradle")

             assertThat(countMatches(buildFileContent, "apply plugin: 'com.google.gms.google-services'")).isEqualTo(1)
           })
  }

  @Test
  fun testSmartUpdatePluginVersionInPlugins() {
    doTest(SIMPLE_APPLICATION_PLUGINS_DSL,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val pluginId = "com.google.gms.google-services"
             val classpathModule = "com.google.gms:google-services"
             // add first
             val changed = helper.addPluginOrClasspath(pluginId,
                                                       classpathModule,
                                                       "4.3.14",
                                                       listOf(moduleModel))
             assertThat(changed.size).isEqualTo(2)

             // then update
             val config = PluginInsertionConfig.defaultInsertionConfig().copy(whenFoundSame = MatchedStrategy.UPDATE_VERSION)
             val changed2 = helper.addPluginOrClasspath(pluginId,
                                                        classpathModule,
                                                        "4.3.15",
                                                        listOf(moduleModel),
                                                        config = config)
             assertThat(changed2.size).isEqualTo(1)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).contains("id 'com.google.gms.google-services' version '4.3.15' apply false")

             assertThat(project.doesFileExists("gradle/libs.versions.toml")).isFalse()
             assertThat(project.getTextForFile("settings.gradle")).doesNotContain("plugins")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(countMatches(buildFileContent, "id 'com.google.gms.google-services'\n")).isEqualTo(1)
           })
  }

  @Test
  fun testSmartUpdatePluginVersionInPluginManagement() {
    doTest(SIMPLE_APPLICATION_PLUGINS_DSL,
           {
             val settings = File(project.basePath, "settings.gradle").readText()
             val newContent = settings.replace("pluginManagement {", "pluginManagement { \n  plugins {\n  \n}\n")
             FileUtil.writeToFile(
               File(project.basePath, "settings.gradle"), newContent
             )
           },
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val pluginId = "com.google.gms.google-services"
             val classpathModule = "com.google.gms:google-services"
             // add first
             val changed = helper.addPluginOrClasspath(pluginId,
                                                       classpathModule,
                                                       "4.3.14",
                                                       listOf(moduleModel))
             assertThat(changed.size).isEqualTo(2)

             // then update
             val config = PluginInsertionConfig.defaultInsertionConfig().copy(whenFoundSame = MatchedStrategy.UPDATE_VERSION)
             val changed2 = helper.addPluginOrClasspath(pluginId,
                                                        classpathModule,
                                                        "4.3.15",
                                                        listOf(moduleModel),
                                                        config = config)
             assertThat(changed2.size).isEqualTo(1)
           },
           {
             val settingsContent = project.getTextForFile("settings.gradle")
             assertThat(settingsContent).contains("id 'com.google.gms.google-services' version '4.3.15'")

             assertThat(project.doesFileExists("gradle/libs.versions.toml")).isFalse()
             assertThat(project.getTextForFile("build.gradle")).doesNotContain("google-services")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(countMatches(buildFileContent, "id 'com.google.gms.google-services'\n")).isEqualTo(1)
           })
  }

  @Test
  fun testSmartUpdatePluginVersionInCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             val pluginId = "com.google.gms.google-services"
             val classpathModule = "com.google.gms:google-services"
             // add first
             val changed = helper.addPluginOrClasspath(pluginId,
                                                       classpathModule,
                                                       "4.3.14",
                                                       listOf(moduleModel))
             assertThat(changed.size).isEqualTo(3)

             // then update
             val config = PluginInsertionConfig.defaultInsertionConfig().copy(whenFoundSame = MatchedStrategy.UPDATE_VERSION)
             val changed2 = helper.addPluginOrClasspath(pluginId,
                                                        classpathModule,
                                                        "4.3.15",
                                                        listOf(moduleModel),
                                                        config = config)
             assertThat(changed2.size).isEqualTo(1)
           },
           {
             val catalog = project.getTextForFile("gradle/libs.versions.toml")
             // we don't update standalone version as it can be used somewhere.
             assertThat(catalog).contains("google-gms-google-services = \"4.3.14\"")
             assertThat(catalog).contains("google-gms-google-services = { id = \"com.google.gms.google-services\", version = \"4.3.15\" }")

             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).contains("alias(libs.plugins.google.gms.google.services) apply false")


             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(buildFileContent).contains("alias(libs.plugins.google.gms.google.services)")
           })
  }

  @Test
  fun testSmartAddPluginToPluginManagement() {
    doTest(SIMPLE_APPLICATION,
           {
             val file = File(project.basePath, "settings.gradle")
             val text = file.readText()
             FileUtil.writeToFile(
               file,
               """
                pluginManagement {
                  plugins {
                  }
                }
                """.trimIndent() + "\n" + text)
           },
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()

             val changed = helper.addPluginOrClasspath("com.google.gms.google-services",
                                                       "com.google.gms:google-services",
                                                       "4.3.14",
                                                       listOf(moduleModel))
             assertThat(changed.size).isEqualTo(2)
           },
           {
             val settingsContent = project.getTextForFile("settings.gradle")
             val regex = "plugins \\{\\n\\s*id 'com.google.gms.google-services'".toRegex()
             assertThat(regex.findAll(settingsContent).toList().size).isEqualTo(1)

             assertThat(project.doesFileExists("gradle/libs.versions.toml")).isFalse()
             val projectBuildContent = project.getTextForFile("build.gradle")
             assertThat(projectBuildContent).doesNotContain("plugins")
             assertThat(projectBuildContent).doesNotContain("classpath 'com.google.gms:google-services:4.3.14'")

             val buildFileContent = project.getTextForFile("app/build.gradle")

             assertThat(countMatches(buildFileContent, "apply plugin: 'com.google.gms.google-services'")).isEqualTo(1)
           })
  }

  /**
   * Test case when addPlugin is called for an existing plugin (with version catalog)
   */
  @Test
  fun testSmartAddPluginWithCatalogWithExistingPlugin() {
    val env = BuildEnvironment.getInstance()
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           {
             val settingsFile = File(project.basePath, "settings.gradle")
             val settingsFileText = settingsFile.readText()
             FileUtil.writeToFile(
               settingsFile,
               settingsFileText.replace("pluginManagement {", "pluginManagement {\n  plugins {}")
             )
           },
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()

             val changed =
               helper.addPluginOrClasspath(
                 "com.android.application",
                 "com.android.tools.build:gradle",
                 env.gradlePluginVersion,
                 listOf(moduleModel)
               )
             assertThat(changed.size).isEqualTo(0)
           },
           {
             val settingsContent = project.getTextForFile("settings.gradle")
             assertThat(settingsContent).isNotEmpty()
             assertThat(settingsContent).doesNotContain("com.android.application")

             val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
             assertThat(catalogContent).isNotEmpty()
             assertThat(catalogContent).doesNotContain("com.android.application")

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(countMatches(buildFileContent, "'com.android.application'")).isEqualTo(1)
           })
  }

  /**
   * Test case when addPlugin is called for an existing plugin (without version catalog)
   */
  @Test
  fun testSmartAddPluginNoCatalogWithExistingPlugin() {
    val env = BuildEnvironment.getInstance()
    doTest(SIMPLE_APPLICATION,
           {
             val settingsFile = File(project.basePath, "settings.gradle")
             val settingsFileText = settingsFile.readText()
             FileUtil.writeToFile(
               settingsFile,
               """
                 pluginManagement {
                   plugins {
                   }
                 }
                 """.trimIndent() + "\n" + settingsFileText
             )
           },
           { projectBuildModel, moduleModel, helper ->
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()

             val changed =
               helper.addPluginOrClasspath(
                 "com.android.application",
                 "com.android.tools.build:gradle",
                 env.gradlePluginVersion,
                 listOf(moduleModel)
               )
             assertThat(changed.size).isEqualTo(0)
           },
           {
             val settingsContent = project.getTextForFile("settings.gradle")
             assertThat(settingsContent).isNotEmpty()
             assertThat(settingsContent).doesNotContain("com.android.application")

             assertThat(project.doesFileExists("gradle/libs.versions.toml")).isFalse()

             val buildFileContent = project.getTextForFile("app/build.gradle")
             assertThat(countMatches(buildFileContent, "apply plugin: 'com.android.application'")).isEqualTo(1)
           })
  }

  @Test
  fun testSmartAddPluginNoCatalogPluginsBlock() {
    doTest(MIGRATE_BUILD_CONFIG,
           { _, moduleModel, helper ->
             val changed = helper.addPluginOrClasspath("com.google.gms.google-services",
                                                       "com.google.gms:google-services",
                                                       "4.3.14",
                                                       listOf(moduleModel))
             assertThat(changed.size).isEqualTo(2)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             val regex = "id 'com.google.gms.google-services' version '4.3.14' apply false".toRegex()
             assertThat(regex.findAll(projectBuildContent).toList().size).isEqualTo(1)

             assertThat(project.doesFileExists("gradle/libs.versions.toml")).isFalse()
             assertThat(projectBuildContent).doesNotContain("classpath")

             // root project plugins block
             val moduleBuildContent = project.getTextForFile("app/build.gradle")
             val regex2 = "id 'com.google.gms.google-services'".toRegex()
             assertThat(regex2.findAll(moduleBuildContent).toList().size).isEqualTo(1)
           })
  }

  @Test
  fun testFindPlaceAndAddPluginNoCatalogPluginsBlock() {
    doTest(MIGRATE_BUILD_CONFIG,
           { _, moduleModel, helper ->
             val changed = helper.findPlaceAndAddPlugin("com.google.gms.google-services",
                                                        "4.3.14",
                                                        listOf(moduleModel))
             assertThat(changed.size).isEqualTo(2)
           },
           {
             val projectBuildContent = project.getTextForFile("build.gradle")
             val regex = "id 'com.google.gms.google-services' version '4.3.14' apply false".toRegex()
             assertThat(regex.findAll(projectBuildContent).toList().size).isEqualTo(1)

             assertThat(project.doesFileExists("gradle/libs.versions.toml")).isFalse()
             assertThat(projectBuildContent).doesNotContain("classpath")

             // root project plugins block
             val moduleBuildContent = project.getTextForFile("app/build.gradle")
             val regex2 = "id 'com.google.gms.google-services'".toRegex()
             assertThat(regex2.findAll(moduleBuildContent).toList().size).isEqualTo(1)
           })
  }

  @Test
  fun testSmartAddPluginWithCatalog() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { _, moduleModel, helper ->
             val changed = helper.addPluginOrClasspath("com.google.gms.google-services",
                                                       "com.google.gms:google-services",
                                                       "4.3.14",
                                                       listOf(moduleModel))
             assertThat(changed.size).isEqualTo(3)
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .contains("google-gms-google-services = { id = \"com.google.gms.google-services\", version.ref = \"")
             assertThat(project.getTextForFile("app/build.gradle"))
               .contains("alias(libs.plugins.google.gms.google.services)")

             val projectBuildContent = project.getTextForFile("build.gradle")

             assertThat(projectBuildContent)
               .contains("alias(libs.plugins.google.gms.google.services) apply false")

             assertThat(project.getTextForFile("settings.gradle")).doesNotContain("plugins")
             assertThat(projectBuildContent).doesNotContain("dependencies")
           })
  }


  @Test
  fun testSmartAddPluginToNewProject() {
    doTest(MINIMAL_CATALOG_APPLICATION,
           { _, moduleModel, helper ->
             val changed = helper.addPluginOrClasspath("com.google.gms.google-services",
                                                       "com.google.gms:google-services",
                                                       "4.3.14",
                                                       listOf(moduleModel))
             assertThat(changed.size).isEqualTo(3)
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .contains("google-gms-google-services = { id = \"com.google.gms.google-services\", version.ref = \"")
             assertThat(project.getTextForFile("app/build.gradle"))
               .contains("alias(libs.plugins.google.gms.google.services)")

             val projectBuildContent = project.getTextForFile("build.gradle")

             assertThat(projectBuildContent)
               .contains("alias(libs.plugins.google.gms.google.services) apply false")

             assertThat(project.getTextForFile("settings.gradle")).doesNotContain("plugins")
             assertThat(projectBuildContent).doesNotContain("dependencies") // no classpath
           })
  }

  @Test
  fun testSimpleAddWithCatalogIgnoreExistingDeclaration() {
    doTest(SIMPLE_APPLICATION_VERSION_CATALOG,
           { projectBuildModel, moduleModel, helper ->
             val catalogModel = projectBuildModel.versionCatalogsModel.getVersionCatalogModel("libs")!!
             catalogModel.pluginDeclarations().addDeclaration("libsPlugin", "com.example.libs:0.5")
             val projectModel = projectBuildModel.projectBuildModel
             assertThat(projectModel).isNotNull()
             helper.addPlugin("com.example.libs", "1.0", false, projectModel!!, moduleModel, FalsePluginMatcher())
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .contains("exampleLibs = { id = \"com.example.libs\", version.ref = \"")
             assertThat(project.getTextForFile("app/build.gradle"))
               .contains("alias(libs.plugins.exampleLibs)")
             assertThat(project.getTextForFile("build.gradle"))
               .contains("alias(libs.plugins.exampleLibs) apply false")
           })
  }

  private fun doTest(projectPath: String,
                     change: (projectBuildModel: ProjectBuildModel, model: GradleBuildModel, helper: CommonPluginsInserter) -> Unit,
                     assert: () -> Unit) {
    doTest(projectPath, {}, change, assert)
  }

  private fun doTest(projectPath: String,
                     updateFiles: () -> Unit,
                     change: (projectBuildModel: ProjectBuildModel, model: GradleBuildModel, helper: CommonPluginsInserter) -> Unit,
                     assert: () -> Unit) {
    projectRule.loadProject(projectPath) { projectRoot ->
      updateFiles()
      VfsUtil.markDirtyAndRefresh(false, true, true, findFileByIoFile(projectRoot, true))
    }

    fixture.allowTreeAccessForAllFiles()

    val projectBuildModel = ProjectBuildModel.get(project)
    val moduleModel: GradleBuildModel? =
      if (project.hasModule("app"))
        projectBuildModel.getModuleBuildModel(project.findModule("app"))
      else
        projectBuildModel.projectBuildModel
    //val moduleModel: GradleBuildModel? = projectBuildModel.getModuleBuildModel(module)
    assertThat(moduleModel).isNotNull()
    val helper = PluginsHelper.withModel(projectBuildModel)
    WriteCommandAction.runWriteCommandAction(project) {
      change.invoke(projectBuildModel, moduleModel!!, (helper as CommonPluginsInserter))
      projectBuildModel.applyChanges()
      moduleModel.applyChanges()
    }
    assert.invoke()
  }
}