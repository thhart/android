package com.android.tools.idea.gradle.project.upgrade

import com.android.SdkConstants
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.util.CompatibleGradleVersion.Companion.getCompatibleGradleVersion
import com.google.common.truth.Expect
import com.intellij.testFramework.LightPlatformTestCase
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AgpCompatibleVersionTest : LightPlatformTestCase() {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun testCompatibleVersions() {
    val data = mapOf(
      /**
       * This test intentionally does not use the symbolic constant SdkConstants.GRADLE_MINIMUM_VERSION, because any change to that should
       * involve a careful look at the compatibility tables used by [getCompatibleGradleVersion] (which failing this test and reading
       * this comment should encourage the brave maintainer to do.  Changes to GRADLE_LATEST_VERSION are both less likely to be disruptive
       * and more likely to be noticed quickly.
       */
      "3.1" to GradleVersion.version("6.1.1"),
      "3.2" to GradleVersion.version("6.1.1"),
      "3.3" to GradleVersion.version("6.1.1"),
      "3.4" to GradleVersion.version("6.1.1"),
      "3.5" to GradleVersion.version("6.1.1"),
      "3.6" to GradleVersion.version("6.1.1"),
      "4.0" to GradleVersion.version("6.1.1"),
      "4.1" to GradleVersion.version("6.5"),
      "4.2" to GradleVersion.version("6.7.1"),
      "7.0" to GradleVersion.version("7.0.2"),
      "7.1" to GradleVersion.version("7.2"),
      "7.2" to GradleVersion.version("7.3.3"),
      "7.3" to GradleVersion.version("7.4"),
      "7.4" to GradleVersion.version("7.5"),
      "8.0" to GradleVersion.version("8.0"),
      "8.1" to GradleVersion.version("8.0"),
      "8.2" to GradleVersion.version("8.2"),
      "8.3" to GradleVersion.version("8.4"),
      "8.4" to GradleVersion.version("8.6"),
      "8.5" to GradleVersion.version("8.7"),
      "8.6" to GradleVersion.version("8.7"),
      "8.7" to GradleVersion.version("8.9"),
      "8.8" to GradleVersion.version("8.10.2"),
      "8.9" to GradleVersion.version("8.11.1"),
      "8.10" to GradleVersion.version("8.11.1"),
      "8.11" to GradleVersion.version(SdkConstants.GRADLE_LATEST_VERSION),
    )
    fun String.toBetaVersionString() = when (this) {
      "3.1" -> "$this.0-beta2"
      else -> "$this.0-beta02"
    }
    fun checkGetCompatibleGradleVersion(from: AgpVersion, to: GradleVersion) {
      expect.that(getCompatibleGradleVersion(from).version).named("getCompatibleGradleVersion(agpVersion=$from)").isEqualTo(to)
    }
    data.forEach { (agpBase, expected) ->
      checkGetCompatibleGradleVersion(AgpVersion.parse("$agpBase.0"), expected)
      checkGetCompatibleGradleVersion(AgpVersion.parse("$agpBase.9"), expected)
      checkGetCompatibleGradleVersion(AgpVersion.parse("$agpBase.0-alpha01"), expected)
      checkGetCompatibleGradleVersion(AgpVersion.parse(agpBase.toBetaVersionString()), expected)
      checkGetCompatibleGradleVersion(AgpVersion.parse("$agpBase.0-rc03"), expected)
    }
  }
}