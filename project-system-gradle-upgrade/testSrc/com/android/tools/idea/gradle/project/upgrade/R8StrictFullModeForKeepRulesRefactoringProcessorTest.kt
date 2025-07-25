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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

@RunsInEdt
class R8StrictFullModeForKeepRulesRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  override val projectRule = AndroidProjectRule.onDisk()

  @Test
  fun `Read More Url is correct`() {
    val project = projectRule.project
    val processor = R8StrictFullModeForKeepRulesDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    assertEquals("https://developer.android.com/r/tools/upgrade-assistant/r8-strict-full-mode-for-keep-rules", processor.getReadMoreUrl())
  }

  @Test
  fun `Property file created when not present`() {
    val project = projectRule.project
    val processor = R8StrictFullModeForKeepRulesDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    assertThat(project.findGradleProperties()).isNull()
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains("android.r8.strictFullModeForKeepRules=false")
  }

  @Test
  fun `Property added when not present`() {
    val project = projectRule.project
    projectRule.fixture.addFileToProject("gradle.properties", "")
    val processor = R8StrictFullModeForKeepRulesDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).doesNotContain("android.r8.strictFullModeForKeepRules=false")
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains("android.r8.strictFullModeForKeepRules=false")
  }

  @Test
  fun `Property kept if present and is false`() {
    val project = projectRule.project
    projectRule.fixture.addFileToProject("gradle.properties", "android.r8.strictFullModeForKeepRules=false")
    val processor = R8StrictFullModeForKeepRulesDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains("android.r8.strictFullModeForKeepRules=false")
  }

  @Test
  fun `Property kept if present and is true`() {
    val project = projectRule.project
    projectRule.fixture.addFileToProject("gradle.properties", "android.r8.strictFullModeForKeepRules=true")
    val processor = R8StrictFullModeForKeepRulesDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains("android.r8.strictFullModeForKeepRules=true")
  }

  @Test
  fun `Refactoring enabled for 9_0_0-alpha01`() {
    val project = projectRule.project
    val processor = R8StrictFullModeForKeepRulesDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0-alpha01"))
    assertTrue(processor.isEnabled)
  }

  private fun Project.findGradleProperties(): VirtualFile? = guessProjectDir()?.findChild("gradle.properties")
}