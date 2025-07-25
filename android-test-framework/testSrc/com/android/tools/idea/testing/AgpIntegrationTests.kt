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
package com.android.tools.idea.testing

import com.android.testutils.junit4.OldAgpSuite
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaSdkVersion.JDK_11
import com.intellij.openapi.projectRoots.JavaSdkVersion.JDK_17
import com.intellij.openapi.projectRoots.JavaSdkVersion.JDK_1_8

const val GRADLE_SNAPSHOT_VERSION = "9.0.0-20250605001524+0000"
const val GRADLE_DECLARATIVE_SNAPSHOT_VERSION = "8.14-milestone-4"
// For available versions: https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/org/jetbrains/kotlin/kotlin-compiler/maven-metadata.xml
const val KOTLIN_SNAPSHOT_VERSION = "2.2.20-dev-4982"

/**
 * An AGP Version definition to be used in AGP integration tests.
 */
enum class AgpVersionSoftwareEnvironmentDescriptor(
  /**
   * The version of the AG. `null` means the current `-dev` version.
   */
  override val agpVersion: String?,

  /**
   * The version of Gradle to be used in integration tests for this AGP version. `null` means the latest/default version.
   */
  override val gradleVersion: String?,

  /**
   * The version of the JDK to launch Gradle with. `null` means the current version used by the IDE.
   */
  override val jdkVersion: JavaSdkVersion? = null,

  /**
   * The version of the Gradle Kotlin plugin to be used in integration tests for this AGP version. `null` means the default version used by
   * Android Studio.
   */
  override val kotlinVersion: String? = null,

  /**
   * The compileSdk to use in this test. `null` means the project default.
   */
  override val compileSdk: String,

  override val targetSdk: String = compileSdk,

  /**
   * Builder model version to query.
   */
  override val modelVersion: ModelVersion = ModelVersion.V2
) : AgpVersionSoftwareEnvironment {
  AGP_31(agpVersion = "3.1.4", gradleVersion = "5.3.1", jdkVersion = JDK_11, kotlinVersion = "1.4.32", modelVersion = ModelVersion.V1, compileSdk = "32"),
  AGP_33_WITH_5_3_1(agpVersion = "3.3.2", gradleVersion = "5.3.1", jdkVersion = JDK_11, kotlinVersion = "1.4.32", modelVersion = ModelVersion.V1, compileSdk = "32"),
  AGP_33(agpVersion = "3.3.2", gradleVersion = "5.5", jdkVersion = JDK_11, kotlinVersion = "1.4.32", modelVersion = ModelVersion.V1, compileSdk = "32"),
  AGP_35_JDK_8(agpVersion = "3.5.0", gradleVersion = "5.5", jdkVersion = JDK_1_8, kotlinVersion = "1.4.32", modelVersion = ModelVersion.V1, compileSdk = "32"),
  AGP_35(agpVersion = "3.5.0", gradleVersion = "5.5", jdkVersion = JDK_11, kotlinVersion = "1.4.32", modelVersion = ModelVersion.V1, compileSdk = "32"),

  AGP_40(agpVersion = "4.0.0", gradleVersion = "6.1.1", jdkVersion = JDK_11, kotlinVersion = "1.5.21", modelVersion = ModelVersion.V1, compileSdk = "32"),
  AGP_41(agpVersion = "4.1.0", gradleVersion = "6.7.1", jdkVersion = JDK_11, kotlinVersion = "1.7.20", modelVersion = ModelVersion.V1, compileSdk = "32"),
  AGP_42(agpVersion = "4.2.0", gradleVersion = "6.7.1", jdkVersion = JDK_11, kotlinVersion = "1.7.20", modelVersion = ModelVersion.V1, compileSdk = "32"),

  // Version constraints set by KGP:
  //   - KGP 1.8 only supports Gradle 6.8.3+
  //   - KGP 2.0 only supports AGP 7.1.3+
  //   - KGP 2.1 only supports AGP 7.3.1+ and Gradle 7.6.3+
  AGP_70(agpVersion = "7.0.0", gradleVersion = "7.0.2", jdkVersion = JDK_11, kotlinVersion = "1.9.23", modelVersion = ModelVersion.V1, compileSdk = "32"),
  AGP_71(agpVersion = "7.1.0", gradleVersion = "7.2", jdkVersion = JDK_17, kotlinVersion = "1.9.23", modelVersion = ModelVersion.V1, compileSdk = "32"),
  AGP_72_V1(agpVersion = "7.2.0", gradleVersion = "7.3.3", jdkVersion = JDK_17, kotlinVersion = "1.9.23", modelVersion = ModelVersion.V1, compileSdk = "32"),
  AGP_72(agpVersion = "7.2.0", gradleVersion = "7.3.3", jdkVersion = JDK_17, kotlinVersion = "1.9.23", modelVersion = ModelVersion.V2, compileSdk = "32"),
  AGP_73(agpVersion = "7.3.0", gradleVersion = "7.4", jdkVersion = JDK_17, kotlinVersion = "1.9.23", modelVersion = ModelVersion.V2, compileSdk = "34"),
  AGP_74(agpVersion = "7.4.1", gradleVersion = "7.5", jdkVersion = JDK_17, kotlinVersion = "1.9.23", modelVersion = ModelVersion.V2, compileSdk = "34"),

  AGP_80(agpVersion = "8.0.2", gradleVersion = "8.0", jdkVersion = JDK_17, modelVersion = ModelVersion.V2, compileSdk = "34"),
  AGP_81(agpVersion = "8.1.0", gradleVersion = "8.0", jdkVersion = JDK_17, modelVersion = ModelVersion.V2, compileSdk = "34"),
  AGP_82(agpVersion = "8.2.0", gradleVersion = "8.2", jdkVersion = JDK_17, modelVersion = ModelVersion.V2, compileSdk = "34"),
  AGP_83(agpVersion = "8.3.1", gradleVersion = "8.4", jdkVersion = JDK_17, compileSdk = "34"),
  AGP_84(agpVersion = "8.4.0", gradleVersion = "8.6", jdkVersion = JDK_17, compileSdk = "34"),
  AGP_85(agpVersion = "8.5.0", gradleVersion = "8.7", jdkVersion = JDK_17, compileSdk = "34"),
  AGP_86(agpVersion = "8.6.0", gradleVersion = "8.7", jdkVersion = JDK_17, compileSdk = "35"),
  AGP_87(agpVersion = "8.7.0", gradleVersion = "8.9", jdkVersion = JDK_17, compileSdk = "35"),
  AGP_88(agpVersion = "8.8.0", gradleVersion = "8.10.2", jdkVersion = JDK_17, compileSdk = "35"),
  AGP_89(agpVersion = "8.9.0", gradleVersion = "8.11.1", jdkVersion = JDK_17, compileSdk = "35"),
  AGP_8_10(agpVersion = "8.10.0-beta01", gradleVersion = "8.11.1", jdkVersion = JDK_17, compileSdk = "35"),


  AGP_LATEST_KOTLIN_SNAPSHOT(agpVersion = null, gradleVersion = null, kotlinVersion = KOTLIN_SNAPSHOT_VERSION, compileSdk = "34"),
  AGP_LATEST_GRADLE_SNAPSHOT(agpVersion = null, gradleVersion = GRADLE_SNAPSHOT_VERSION, compileSdk = "34"),
  AGP_DECLARATIVE_GRADLE_SNAPSHOT(agpVersion = null, gradleVersion = GRADLE_DECLARATIVE_SNAPSHOT_VERSION, compileSdk = "34"),
  // Must be last to represent the newest version.
  AGP_LATEST(null, gradleVersion = null, compileSdk = "34");
  override fun toString(): String {
    return listOfNotNull(
      (agpVersion ?: "current"),
      gradleVersion?.let { "g=$it" },
      jdkVersion?.let { "j=$it" },
      kotlinVersion?.let { "k=$it" },
      modelVersion.takeIf { it != ModelVersion.V2 }?.let { "m=$it" },
      compileSdk.takeIf { it != "34" }?.let { "c=$it" },
    ).joinToString(",", "Agp(", ")")
  }
  companion object {
    @JvmField
    val AGP_CURRENT = AGP_LATEST
    val selected: AgpVersionSoftwareEnvironmentDescriptor
      get() {
        if (OldAgpSuite.AGP_VERSION == null && OldAgpSuite.GRADLE_VERSION == null) return AGP_CURRENT
        val applicableAgpVersions = applicableAgpVersions()
        return applicableAgpVersions.singleOrNull()
          ?: error("Multiple AGP versions selected: $applicableAgpVersions. A parameterised test is required.")
      }
  }
}

enum class ModelVersion {
  V1,
  V2;

  companion object {
    val selected: ModelVersion get() = if (StudioFlags.GRADLE_SYNC_USE_V2_MODEL.get()) V2 else V1
  }
}

class SnapshotContext(
  projectName: String,
  agpVersion: AgpVersionSoftwareEnvironmentDescriptor,
  override val snapshotDirectoryWorkspaceRelativePath: String,
) : SnapshotComparisonTest {

  private val name: String =
    "$projectName${agpVersion.agpSuffix()}${agpVersion.gradleSuffix()}${agpVersion.modelVersion}"

  override fun getName(): String = name
}

interface AgpIntegrationTestDefinition {
  val name: String
  val agpVersion: AgpVersionSoftwareEnvironmentDescriptor
  fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): AgpIntegrationTestDefinition
  fun displayName(): String = "$name${if (agpVersion != AGP_CURRENT) "-${agpVersion}" else ""}"
  fun isCompatible(): Boolean = agpVersion > AgpVersionSoftwareEnvironmentDescriptor.AGP_33_WITH_5_3_1 /* Not supported special cases */
}

/**
 * Applies AGP versions selected for testing in the current test target to the list of test definitions.
 */
fun List<AgpIntegrationTestDefinition>.applySelectedAgpVersions(): List<AgpIntegrationTestDefinition> =
  applicableAgpVersions()
    .flatMap { version -> this.map { it.withAgpVersion(version) } }
    .filter { it.isCompatible() }
    .sortedWith(compareBy({ it.agpVersion.gradleVersion }, { it.agpVersion.agpVersion }))

fun applicableAgpVersions() = AgpVersionSoftwareEnvironmentDescriptor.values()
  .filter {
    val pass = (OldAgpSuite.AGP_VERSION == null || (it.agpVersion ?: "LATEST") == OldAgpSuite.AGP_VERSION) &&
      (OldAgpSuite.GRADLE_VERSION == null || (it.gradleVersion ?: "LATEST") == OldAgpSuite.GRADLE_VERSION)
    println("${it.name}($it) : $pass")
    pass
  }

/**
 * Prints a message describing the currently running test to the standard output.
 */
fun IntegrationTestEnvironment.outputCurrentlyRunningTest(testDefinition: AgpIntegrationTestDefinition) {
  println("Testing: ${this.javaClass.simpleName}[${testDefinition.displayName()}]")
}

private fun AgpVersionSoftwareEnvironmentDescriptor.agpSuffix(): String = when (this) {
  AgpVersionSoftwareEnvironmentDescriptor.AGP_LATEST,
  AgpVersionSoftwareEnvironmentDescriptor.AGP_DECLARATIVE_GRADLE_SNAPSHOT,
  AgpVersionSoftwareEnvironmentDescriptor.AGP_LATEST_GRADLE_SNAPSHOT,
  AgpVersionSoftwareEnvironmentDescriptor.AGP_LATEST_KOTLIN_SNAPSHOT -> "_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_8_10 -> "_Agp_8.10_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_89 -> "_Agp_8.9_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_88 -> "_Agp_8.8_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_87 -> "_Agp_8.7_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_86 -> "_Agp_8.6_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_85 -> "_Agp_8.5_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_84 -> "_Agp_8.4_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_83 -> "_Agp_8.3_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_82 -> "_Agp_8.2_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_81 -> "_Agp_8.1_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_80 -> "_Agp_8.0_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_31 -> "_Agp_3.1_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_33_WITH_5_3_1 -> "_Agp_3.3_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_33 -> "_Agp_3.3_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_35_JDK_8 -> "_Agp_3.5_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_35 -> "_Agp_3.5_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_40 -> "_Agp_4.0_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_41 -> "_Agp_4.1_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_42 -> "_Agp_4.2_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_70 -> "_Agp_7.0_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_71 -> "_Agp_7.1_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_72_V1 -> "_Agp_7.2_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_72 -> "_Agp_7.2_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_73 -> "_Agp_7.3_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_74 -> "_Agp_7.4_"

}

private fun AgpVersionSoftwareEnvironmentDescriptor.gradleSuffix(): String {
  return gradleVersion?.let { "Gradle_${it}_" }.orEmpty()
}
