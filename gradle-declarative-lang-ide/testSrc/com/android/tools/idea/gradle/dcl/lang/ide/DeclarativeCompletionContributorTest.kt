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
import com.android.tools.idea.gradle.dcl.lang.sync.DataClassRef
import com.android.tools.idea.gradle.dcl.lang.sync.DataProperty
import com.android.tools.idea.gradle.dcl.lang.sync.Entry
import com.android.tools.idea.gradle.dcl.lang.sync.SimpleTypeRef
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth
import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.PsiFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

// Test is based on generated schema files.
@RunWith(JUnit4::class)
@RunsInEdt
class DeclarativeCompletionContributorTest : UsefulTestCase() {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk().onEdt()
  private val fixture by lazy { projectRule.fixture }

  @Before
  fun before() {
    DeclarativeIdeSupport.override(true)
  }

  @After
  fun onAfter() = DeclarativeIdeSupport.clearOverride()

  @Test
  fun testBasicRootCompletion() {
    doTest("and$caret") { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "androidApp" to "Block element", "androidLibrary" to "Block element"
      )
    }
    doTest("and$caret { }") { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "androidApp" to "Block element", "androidLibrary" to "Block element"
      )
    }
  }

  @Test
  fun testInsideBlockCompletion() {
    doTest("""
      androidApp{
        a$caret
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "buildFeatures" to "Block element", "defaultConfig" to "Block element", "getDefaultProguardFile" to "Factory",
        "namespace" to "String", "productFlavors" to "Block element", "testNamespace" to "String"
      )
    }
  }

  @Test
  fun testBeforeOpenBlock() {
    doNoSuggestionTest("""
      androidApp $caret{
        
      }
      """)
    doNoSuggestionTest("""
      androidApp$caret{
        
      }
      """)
  }

  @Test
  fun testPluginBlockCompletion() {
    doTest("""
      pl$caret
      """, "settings.gradle.dcl") { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "pluginManagement" to "Block element", "plugins" to "Block element"
      )
    }
  }

  @Test
  fun testInsideApplicationBlockCompletion() {
    doTest("""
      androidApp{
        a$caret
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "buildFeatures" to "Block element", "defaultConfig" to "Block element", "getDefaultProguardFile" to "Factory",
        "namespace" to "String", "productFlavors" to "Block element", "testNamespace" to "String"
      )
    }
  }

  @Test
  fun testAfterPropertyCompletion() {
    doNoSuggestionTest("""
      androidLibrary {
          compileSdk = 1$caret
        }""".trimIndent()
    )

    doNoSuggestionTest("""
      androidLibrary {
          compileSdk = 1${caret}3
        }""".trimIndent()
    )
  }

  @Test
  fun testInsideApplicationBlockCompletionNoTyping() {
    doTest("""
      androidApp {
        $caret
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.map { it.first }.toList()).containsExactly(
        "buildFeatures", "buildOutputs", "buildTypes", "bundle", "compileOptions",
        "compileSdk", "defaultConfig", "dependenciesDcl", "getDefaultProguardFile", "lint", "namespace",
        "productFlavors", "signingConfigs", "sourceSets", "testBuildType", "testNamespace"
      )
    }
  }

  @Test
  fun testListProperty() {
    doTestOnPatchedSchema("""
      androidApp {
        buildTypes{
          buildType("debug"){
            matching$caret
        }
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "matchingFallbacks = listOf()" to "Expression", "matchingFallbacks += listOf()" to "Expression"
      )
    }
  }

  @Test
  fun testListPropertyEditing() {
    doTestOnPatchedSchema("""
      androidApp {
        buildTypes{
          buildType("debug"){
            matchingFall${caret}backs += listOf()
        }
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "matchingFallbacks" to "Property"
      )
    }
  }

  @Test
  fun testForLayoutPropertyCompletion() {
    doCompletionTestPatchedSchema("""
      androidApp {
        fakeFileList = listOf(layo$caret)
      }
      """, """
      androidApp {
        fakeFileList = listOf(layout.$caret)
      }
      """)
  }

  @Test
  fun testForPropertyAsFunctionCallArgument() {
    doTestOnPatchedSchema("""
      androidApp {
        fakeFileList = listOf(layout.$caret)
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "projectDirectory" to "Property", "settingsDirectory" to "Property"
      )
    }
    doTestOnPatchedSchema("""
      androidApp {
        fakeFileList = listOf(layout.projectDirectory.$caret)
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "dir" to "Factory", "file" to "Factory"
      )
    }
  }

  @Test
  fun testCompletionLayoutFileAsArgument() {
    doCompletionTestPatchedSchema("""
      androidApp {
        fakeFileList = listOf(layout.projectDirectory.f$caret)
      }
      """, """
      androidApp {
        fakeFileList = listOf(layout.projectDirectory.file($caret))
      }
      """)
  }

  @Test
  fun testNoSuggestionAfterIllegalAppend() {
    doTestOnPatchedSchema("""
      androidApp {
        compileSdk += ${caret}1
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).isEmpty()
    }
  }

  @Test
  fun testNoSuggestionAfterIllegalAppend2() {
    doTestOnPatchedSchema("""
      androidApp {
        compileSdk += 1${caret}
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).isEmpty()
    }
  }

  @Test
  fun testNoSuggestionAfterIllegalAppendPropertyValue() {
    doTestOnPatchedSchema("""
      androidApp {
        bundle  {
          integrityConfigDir += l$caret
        }
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).isEmpty()
    }
  }

  @Test
  fun testCompletionProjectDirectoryAsArgument() {
    doCompletionTestPatchedSchema("""
      androidApp {
        fakeFileList = listOf(layout.p$caret)
      }
      """, """
      androidApp {
        fakeFileList = listOf(layout.projectDirectory.$caret)
      }
      """)
  }

  @Test
  fun testAssignObjectType() {
    doTest("""
      androidApp {
        buildTypes{
          buildType("debug"){
            matchingFallbacks = $caret
          }
        }
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "layout" to "Property", "listOf" to "Factory", "mapOf" to "Factory"
      )
    }
  }

  @Test
  fun testAssignList() {
    doCompletionTestPatchedSchema("""
      androidApp {
        buildTypes{
          buildType("debug"){
            matchingFallbacks = li$caret
          }
        }
      }
      """.trimIndent(), """
      androidApp {
        buildTypes{
          buildType("debug"){
            matchingFallbacks = listOf($caret)
          }
        }
      }
      """.trimIndent())
  }

  @Test
  fun testAssignMapCompletion() {
    doCompletionTestPatchedSchema("""
      androidApp {
        defaultConfig {
          testInstrumentationRunnerArguments = ma$caret
        }
      }
      """.trimIndent(), """
      androidApp {
        defaultConfig {
          testInstrumentationRunnerArguments = mapOf($caret)
        }
      }
      """.trimIndent())
  }

  @Test
  fun testAssignMapSuggestion() {
    doTestOnPatchedSchema("""
      androidApp {
        defaultConfig {
          testInstrumentationRunnerArguments = $caret
        }
      }
      """.trimIndent())
    { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "layout" to "Property", "listOf" to "Factory", "mapOf" to "Factory"
      )
    }
  }

  @Test
  fun testAppendList() {
    doCompletionTestPatchedSchema("""
      androidApp {
        buildTypes{
          buildType("debug"){
            matchingFallbacks += li$caret
          }
        }
      }
      """.trimIndent(), """
      androidApp {
        buildTypes{
          buildType("debug"){
            matchingFallbacks += listOf($caret)
          }
        }
      }
      """.trimIndent())
  }

  @Test
  fun testInsideFileCompletionNoTyping() {
    doTest("""
        $caret
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "androidApp" to "Block element", "androidLibrary" to "Block element", "listOf" to "Factory", "mapOf" to "Factory"
      )
    }
  }

  @Test
  fun testSuggestionOfFactoryBlock() {
    doTest("""
    androidApp {
      buildTypes{
        $caret
      }
    }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly("buildType" to "Factory block")
    }
  }

  @Test
  fun testSuggestionRegularFile() {
    doCompletionTest("""
    androidApp {
      bundle  {
        deviceTarg$caret
      }
    }
    """ ,"""
    androidApp {
      bundle  {
        deviceTargetingConfig = $caret
      }
    }
    """
    )
  }

  @Test
  fun testSuggestionInFactoryBlock() {
    doTest("""
    androidApp {
      buildTypes{
        buildType("new"){
          $caret
        }
      }
    }
      """) { suggestions ->
      Truth.assertThat(suggestions.map { it.first }.toList()).containsExactly(
        "applicationIdSuffix", "buildConfigField", "dependencies", "isMinifyEnabled", "multiDexEnabled", "versionNameSuffix")
    }
  }

  @Test
  fun testSuggestionInFactoryBlockWithPatchedSchema() {
    // basically patched version has matchingFallbacks as immutable list instead of mutable list as in AGP
    doTestOnPatchedSchema("""
    androidApp {
      buildTypes{
        buildType("new"){
          $caret
        }
      }
    }
      """) { suggestions ->
      Truth.assertThat(suggestions.map { it.first }.toList()).containsExactly(
        "applicationIdSuffix", "buildConfigField", "dependencies", "isMinifyEnabled",
        "matchingFallbacks += listOf()", "matchingFallbacks = listOf()", "multiDexEnabled",
        "proguardFile", "proguardFiles += listOf()", "proguardFiles = listOf()", "versionNameSuffix")
    }
  }

  @Test
  fun testEnumIsSuggestion() {
    doTest("""
    androidApp {
        compileOptions {
           $caret
        }
      }
    }
      """) { suggestions ->
      // sourceCompatibility and targetCompatibility are enums
      Truth.assertThat(suggestions.toList()).containsExactly(
        "encoding" to "String", "isCoreLibraryDesugaringEnabled" to "Boolean",
        "sourceCompatibility" to "Enum", "targetCompatibility" to "Enum"
      )
    }
  }

  @Test
  fun testLayoutFileFactory() {
    // test first level
    doTest("""
      androidApp {
        bundle {
          deviceTargetingConfig = $caret
        }
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "layout" to "Property", "listOf" to "Factory", "mapOf" to "Factory"
      )
    }

    // test second level
    doTest("""
      androidApp {
        bundle {
          deviceTargetingConfig = layout.$caret
        }
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "projectDirectory" to "Property", "settingsDirectory" to "Property"
      )
    }
    // test last level
    doTest("""
      androidApp {
        bundle {
          deviceTargetingConfig = layout.projectDirectory.$caret
        }
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "dir" to "Factory", "file" to "Factory"
      )
    }
  }

  @Test
  fun testCompletionStringProperty() {
    doCompletionTest("""
      androidApp {
        defaultConfig {
          versionNameS$caret
        }
      }
      """, """
      androidApp {
        defaultConfig {
          versionNameSuffix = "$caret"
        }
      }
      """)
  }

  @Test
  fun testCompletionBlock() {
    // inserting caret with indent position (default 4 whitespaces)
    doCompletionTest(
      "androidApp$caret",
      """
        androidApp {
            $caret
        }
        """.trimIndent())
  }

  @Test
  fun testCompletionBlockWithUpdatedIndent() {
    doCompletionTest(
      "androidApp$caret",
      """
        androidApp {
              $caret
        }
        """.trimIndent()) { psiFile ->
      CodeStyle.getIndentOptions(psiFile).INDENT_SIZE = 6
    }
  }

  @Test
  fun testCompletionBlock2() {
    doCompletionTest(
      """
        androidLibrary {
          dependenci$caret
        }""".trimIndent(),
      """
        androidLibrary {
          dependenciesDcl {
              $caret
          }
        }""".trimIndent())
  }

  @Test
  fun testCompletionInt() {
    doCompletionTest("""
     androidApp {
       defaultConfig {
         maxSd$caret
       }
     }
     """, """
     androidApp {
       defaultConfig {
         maxSdk = $caret
       }
     }
     """)
  }

  @Test
  fun testCompletionBoolean() {
    doCompletionTest("""
    androidLibrary {
      defaultConfig {
        multiDexEnab$caret
      }
    }
    """, """
    androidLibrary {
      defaultConfig {
        multiDexEnabled = $caret
      }
    }
    """)
  }

  @Test
  fun testCompletionFunction() {
    doCompletionTest("""
      androidLibrary {
        dependenciesDcl {
          androidTestIm$caret
        }
      }
    """.trimIndent(), """
    androidLibrary {
      dependenciesDcl {
        androidTestImplementation($caret)
      }
    }
    """.trimIndent())
  }

  @Test
  fun testCompletionEnum() {
    doCompletionTest("""
      androidApp {
        compileOptions {
          sourceCom$caret
        }
      }
    """.trimIndent(), """
    androidApp {
      compileOptions {
        sourceCompatibility = $caret
      }
    }
    """.trimIndent())
  }

  @Test
  fun testCompletionUriValues() {
    doCompletionTest("""
     dependencyResolutionManagement {
       repositories {
         maven {
            ur$caret
         }
       }
     }
     """,
     "settings.gradle.dcl", """
     dependencyResolutionManagement {
       repositories {
         maven {
            url = uri("$caret")
         }
       }
     }
     """)
  }

  @Test
  fun testCompletionUriValues2() {
    doCompletionTest("""
   dependencyResolutionManagement {
     repositories {
       maven {
          url = ur$caret
       }
     }
   }
   """, "settings.gradle.dcl", """
   dependencyResolutionManagement {
     repositories {
       maven {
          url = uri($caret)
       }
     }
   }
   """)
  }

  @Test
  fun testCompletionEnumValues() {
    doTest("""
      androidApp {
        compileOptions {
          sourceCompatibility = $caret
        }
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsAllIn(
        listOf("VERSION_1_1", "VERSION_1_2", "VERSION_26", "VERSION_27", "VERSION_HIGHER").zip((1..5).map { "Enum Constant" })
      )
    }
    doCompletionTest("""
      androidApp {
        compileOptions {
          sourceCompatibility = VERSION_H$caret
        }
      }
    """, "build.gradle.dcl", """
      androidApp {
        compileOptions {
          sourceCompatibility = VERSION_HIGHER$caret
        }
      }
    """)

  }

  @Test
  fun testBooleanProperty() {
    doTest("""
      androidApp {
        buildFeatures {
          dataBinding = $caret
        }
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly("true" to "Boolean", "false" to "Boolean")
    }

    doCompletionTest("""
      androidApp {
        buildFeatures {
          dataBinding = tr$caret
        }
      }
   """, """
      androidApp {
        buildFeatures {
          dataBinding = true$caret
        }
      }
   """)
  }

  @Test
  fun testSuggestionsUriFunction() {
    doTest("""
    dependencyResolutionManagement {
      repositories {
        maven {
          url = $caret
        }
      }
    }
    """, "settings.gradle.dcl") { suggestions ->
      Truth.assertThat(suggestions.toList())
        .containsExactly("layout" to "Property", "listOf" to "Factory", "mapOf" to "Factory",
                         "rootProject" to "Property", "uri" to "Factory")
    }
  }

  @Test
  fun testSuggestionsFunctionArguments() {
    doTestOnPatchedSchema("""
    androidApp {
      buildTypes {
        buildType("debug") {
          proguardFiles = listOf($caret)
        }
      }
    }
    """) { suggestions ->
      Truth.assertThat(suggestions.map { it.first }.toList()).containsExactly(
        "buildConfigField", "layout", "listOf", "mapOf", "proguardFile")
    }
  }

  @Test
  fun testSuggestionsFunctionArgumentsWithAppend() {
    doTestOnPatchedSchema("""
    androidApp {
      buildTypes {
        buildType("debug") {
          proguardFiles += listOf($caret)
        }
      }
    }
    """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "buildConfigField" to "Factory", "layout" to "Property", "listOf" to "Factory", "mapOf" to "Factory", "proguardFile" to "Factory")
    }
  }


  @Test
  fun testSuggestionsFunctionSecondArgument() {
    doTestOnPatchedSchema("""
    androidApp {
      buildTypes {
        buildType("debug") {
          proguardFiles = listOf(proguardFile("a"), $caret)
        }
      }
    }
    """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "buildConfigField" to "Factory", "layout" to "Property", "listOf" to "Factory", "mapOf" to "Factory", "proguardFile" to "Factory"
      )
    }
  }

  @Test
  fun testSuggestionsFunctionSecondArgumentWithAppend() {
    doTestOnPatchedSchema("""
    androidApp {
      buildTypes {
        buildType("debug") {
          proguardFiles += listOf(proguardFile("a"), $caret)
        }
      }
    }
    """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "buildConfigField" to "Factory", "layout" to "Property", "listOf" to "Factory", "mapOf" to "Factory", "proguardFile" to "Factory"
      )
    }
  }

  @Test
  fun testCompletionFunctionArguments() {
    doCompletionTestPatchedSchema("""
    androidApp {
      buildTypes {
        buildType("debug") {
          proguardFiles = listOf(proguard$caret)
        }
      }
    }
    """, """
    androidApp {
      buildTypes {
        buildType("debug") {
          proguardFiles = listOf(proguardFile($caret))
        }
      }
    }
    """)
  }

  @Test
  fun testCompletionFunctionSecondArguments() {
    doCompletionTestPatchedSchema("""
    androidApp {
      buildTypes {
        buildType("debug") {
          proguardFiles = listOf(proguardFile("a"), prog$caret)
        }
      }
    }
    """, """
    androidApp {
      buildTypes {
        buildType("debug") {
          proguardFiles = listOf(proguardFile("a"), proguardFile($caret))
        }
      }
    }
    """)
  }


  @Test
  fun testSuggestionsPlugins() {
    doTest("""
    plugins {
      id("some").$caret
    }
    """, "settings.gradle.dcl") { suggestions ->
      Truth.assertThat(suggestions.toList()).containsAllIn(listOf("version" to "Factory", "apply" to "Factory"))
    }
  }

  @Test
  fun testRootProjects() {
    doTest("""
    rootProject.$caret
    """, "settings.gradle.dcl") { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly("name" to "String")
    }
  }

  @Test
  fun testListOfHasLayoutSuggestion() {
    doTestOnPatchedSchema("""
    androidApp {
      fakeFileList = listOf($caret)
    }
    """) { suggestions ->
      Truth.assertThat(suggestions.toList()).contains("layout" to "Property")
    }
    doTestOnPatchedSchema("""
    androidApp {
      fakeFileList = listOf(layout.projectDirectory.file("aaa"), $caret)
    }
    """) { suggestions ->
      Truth.assertThat(suggestions.toList()).contains("layout" to "Property")
    }
    doTestOnPatchedSchema("""
    androidApp {
      fakeFileList = listOf($caret
    }
    """) { suggestions ->
      Truth.assertThat(suggestions.toList()).contains("layout" to "Property")
    }
    doTestOnPatchedSchema("""
    androidApp {
      fakeFileList = listOf(layout.projectDirectory.file("aaa"), $caret
    }
    """) { suggestions ->
      Truth.assertThat(suggestions.toList()).contains("layout"  to "Property")
    }
  }

  @Test
  fun testMapOfPair() {
    doTestOnPatchedSchema("""
       androidApp {
        defaultConfig {
          testInstrumentationRunnerArguments = mapOf("a"$caret)
        }
       }
    """) { suggestions ->
      Truth.assertThat(suggestions.toList()).contains("to" to "Pair Factory")
    }

    doTestOnPatchedSchema("""
       androidApp {
        defaultConfig {
          testInstrumentationRunnerArguments = mapOf("a" $caret)
        }
       }
    """) { suggestions ->
      Truth.assertThat(suggestions.toList()).contains("to" to "Pair Factory")
    }
  }

  @Test
  fun testMapOfPair2() {
    doTestOnPatchedSchema("""
       androidApp {
        defaultConfig {
          testInstrumentationRunnerArguments = mapOf("a" t$caret)
        }
       }
    """) { suggestions ->
      Truth.assertThat(suggestions.toList()).contains("to" to "Pair Factory")
    }

    doTestOnPatchedSchema("""
       androidApp {
        defaultConfig {
          testInstrumentationRunnerArguments = mapOf("a" t$caret, "c" to mapOf())
        }
       }
    """) { suggestions ->
      Truth.assertThat(suggestions.toList()).contains("to" to "Pair Factory")
    }
  }

  @Test
  fun testRootProject() {
    doCompletionTest("""
    rootProje$caret
   """, "settings.gradle.dcl", """
    rootProject.name = "$caret"
   """)
  }

  @Test
  fun testRootProject2() {
    doCompletionTest("""
    rootProject$caret
   """, "settings.gradle.dcl", """
    rootProject.name = "$caret"
   """)
  }

  @Test
  // root data property should not have assignable data property
  // except rootProject.name
  // once this test fail we need to add more completion logic
  fun testNoDataPropertyForDataProperty() {
    registerTestDeclarativeService(projectRule.project, fixture.testRootDisposable)

    val knownPaths = listOf(listOf("rootProject", "name"))
    val schema = DeclarativeService.getInstance(fixture.project).getDeclarativeSchema() ?: return

    fun EntryWithContext.check(path: List<String>) {
      val maybeDataProperty = entry
      if (maybeDataProperty is DataProperty) {
        val type = maybeDataProperty.valueType
        if (type is DataClassRef)
          getNextLevel().forEach { nextLevelElement ->
            (nextLevelElement.entry as? DataProperty)?.let {
              if(it.valueType is SimpleTypeRef) Truth.assertThat(path + it.name).isIn(knownPaths)
            }
          }
      }
    }

    fun EntryWithContext.iterate(path: List<String>, seen: List<Entry>) {
      check(path)
      getNextLevel().forEach {
        if (!seen.contains(it.entry)) it.iterate(path + it.entry.simpleName,seen + it.entry)
      }
    }

    // looking for root properties that has simple props like rootProject.name
    schema.getTopLevelEntries("settings.gradle.dcl").forEach { it.iterate(listOf(it.entry.simpleName),listOf(it.entry)) }
  }

  private fun doTest(declarativeFile: String, check: (List<Pair<String, String>>) -> Unit) {
    doTest(declarativeFile, "build.gradle.dcl", check)
  }

  private fun doTestOnPatchedSchema(declarativeFile: String, check: (List<Pair<String, String>>) -> Unit) {
    doTestOnPatchedSchema(declarativeFile, "build.gradle.dcl", check)
  }

  private fun doTestOnPatchedSchema(declarativeFile: String, fileName: String, check: (List<Pair<String, String>>) -> Unit) {
    registerTestDeclarativeServicePatchedSchema(projectRule.project, fixture.testRootDisposable)
    _doTest(declarativeFile, fileName, check)
  }

  private fun doTest(declarativeFile: String, fileName: String, check: (List<Pair<String, String>>) -> Unit) {
    registerTestDeclarativeService(projectRule.project, fixture.testRootDisposable)
    _doTest(declarativeFile, fileName, check)
  }

  private fun _doTest(declarativeFile: String, fileName: String, check: (List<Pair<String, String>>) -> Unit) {
    val buildFile = fixture.addFileToProject(
      fileName, declarativeFile)
    fixture.configureFromExistingVirtualFile(buildFile.virtualFile)
    fixture.completeBasic()
    val list: List<Pair<String, String>> = fixture.lookupElements!!.map {
      val presentation = LookupElementPresentation()
      it.renderElement(presentation)
      it.lookupString to (presentation.typeText ?: "")
    }
    check.invoke(list)
  }

  private fun doNoSuggestionTest(declarativeFile: String) {
    registerTestDeclarativeService(projectRule.project, fixture.testRootDisposable)

    val buildFile = fixture.addFileToProject(
      "build.gradle.dcl", declarativeFile)
    fixture.configureFromExistingVirtualFile(buildFile.virtualFile)
    fixture.completeBasic()
    Truth.assertThat(fixture.lookup).isNull()
  }

  private fun doCompletionTest(declarativeFile: String, fileAfter: String, update:(PsiFile) -> Unit = {}) =
    doCompletionTest(declarativeFile, "build.gradle.dcl", fileAfter, update )

  private fun doCompletionTestPatchedSchema(declarativeFile: String, fileAfter: String, update:(PsiFile) -> Unit = {}) {
    registerTestDeclarativeServicePatchedSchema(projectRule.project, fixture.testRootDisposable)
    _doCompletionTest(declarativeFile, "build.gradle.dcl", fileAfter, update)
  }

  private fun doCompletionTest(declarativeFile: String, fileName: String, fileAfter: String, update:(PsiFile) -> Unit = {}) {
    registerTestDeclarativeService(projectRule.project, fixture.testRootDisposable)
    _doCompletionTest(declarativeFile, fileName, fileAfter, update)
  }
  private fun _doCompletionTest(declarativeFile: String, fileName: String, fileAfter: String, update:(PsiFile) -> Unit = {}){
    val buildFile = fixture.addFileToProject(fileName, declarativeFile)
    update(buildFile)
    fixture.configureFromExistingVirtualFile(buildFile.virtualFile)
    fixture.completeBasic()

    val caretOffset = fileAfter.indexOf(caret)
    val cleanFileAfter = fileAfter.replace(caret, "")

    Truth.assertThat(buildFile.text).isEqualTo(cleanFileAfter)
    Truth.assertThat(fixture.editor.caretModel.offset).isEqualTo(caretOffset)
  }
}
