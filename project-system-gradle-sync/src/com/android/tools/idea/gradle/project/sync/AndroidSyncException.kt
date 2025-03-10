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
package com.android.tools.idea.gradle.project.sync

import com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.project.sync.AndroidSyncExceptionType.*
import java.util.regex.Pattern

/**
 * Marker interface for all exceptions that are triggered via Android specific errors in project import.
 */
open class AndroidSyncException(
  val type: AndroidSyncExceptionType,
  message: String,
  /**
   *  The root directory of the current build where this exception originated.
   *  For included build in a composite build, this points
   *  to the root directory(dir that hold settings.gradle) of the included build.
   *  For root build in a composite build, this points to the root directory of root build.
   */
  val buildPath: String? = null,

  /**
   * Path of the module that can be used as an identifier for module within a given build.
   */
  val modulePath: String? = null,

  /**
   * Issues encountered during the gradle sync.
   */
  val syncIssues: List<IdeSyncIssue>? = null,
) : RuntimeException(message)

enum class AndroidSyncExceptionType {
  AGP_VERSION_TOO_OLD,
  AGP_VERSION_TOO_NEW,
  AGP_VERSION_INCOMPATIBLE,
  AGP_VERSIONS_MISMATCH,
  NO_VALID_NATIVE_ABI_FOUND,
  NO_VARIANTS_FOUND,
}

class AgpVersionTooOld(agpVersion: AgpVersion) : AndroidSyncException(AGP_VERSION_TOO_OLD, generateMessage(agpVersion)) {
  companion object {
    private const val LEFT = "The project is using an incompatible version (AGP "
    private const val RIGHT = ") of the Android Gradle plugin. Minimum supported version is AGP $GRADLE_PLUGIN_MINIMUM_VERSION."
    private fun generateMessage(agpVersion: AgpVersion) = "$LEFT$agpVersion$RIGHT"
    val PATTERN: Pattern = Pattern.compile("${Pattern.quote(LEFT)}(.+)${Pattern.quote(RIGHT)}")
    val ALWAYS_PRESENT_STRINGS = listOf(LEFT, RIGHT)
  }
}

class AgpVersionTooNew(agpVersion: AgpVersion, latestSupportedVersion: AgpVersion) : AndroidSyncException(AGP_VERSION_TOO_NEW, generateMessage(agpVersion, latestSupportedVersion)) {
  companion object {
    private const val LEFT = "The project is using an incompatible version (AGP "
    private const val RIGHT = ") of the Android Gradle plugin. Latest supported version is AGP "
    private fun generateMessage(agpVersion: AgpVersion, latestSupportedVersion: AgpVersion) = "$LEFT$agpVersion$RIGHT$latestSupportedVersion"
    fun pattern(latestSupportedVersion: AgpVersion): Pattern =
      Pattern.compile("${Pattern.quote(LEFT)}(.+)${Pattern.quote(RIGHT)}${Pattern.quote("$latestSupportedVersion")}")
    val ALWAYS_PRESENT_STRINGS = listOf(LEFT, RIGHT)
  }
}

class AgpVersionIncompatible(agpVersion: AgpVersion, latestSupportedVersion: AgpVersion) : AndroidSyncException(AGP_VERSION_INCOMPATIBLE, generateMessage(agpVersion, latestSupportedVersion)) {
  companion object {
    private const val A = "The project is using an incompatible preview version (AGP "
    private const val B = ") of the Android Gradle plugin. Current compatible "
    private const val PREVIEW = "preview "
    private val C = "version is AGP"
    private fun generateMessage(agpVersion: AgpVersion, latestKnown: AgpVersion): String {
      return "$A$agpVersion$B${if (latestKnown.isPreview) PREVIEW else ""}$C $latestKnown."
    }

    fun pattern(latestSupportedVersion: AgpVersion): Pattern =
      Pattern.compile(
        "${Pattern.quote(A)}(.+)${Pattern.quote(B)}${if (latestSupportedVersion.isPreview) Pattern.quote(PREVIEW) else ""}${Pattern.quote("$C $latestSupportedVersion.")}")

    val ALWAYS_PRESENT_STRINGS = listOf(A, B, C)
  }
}

class AgpVersionsMismatch(agpVersions: List<Pair<String, String>>) : AndroidSyncException(AGP_VERSIONS_MISMATCH, generateMessage(agpVersions)) {
  companion object {
    private fun generateMessage(agpVersions: List<Pair<String, String>>): String {
      return "$MESSAGE_START ${agpVersions.map { it.first }.distinct()}" +
             " $MESSAGE_CORE.\n$MESSAGE_END ${agpVersions.map { it.second }.distinct()}.\n"
    }

    const val MESSAGE_START = "Using multiple versions of the Android Gradle Plugin"
    const val MESSAGE_CORE = "across Gradle builds is not allowed"
    const val MESSAGE_END = "Affected builds:"
    val MULTIPLE_AGP_VERSIONS = Pattern.compile("$MESSAGE_START (.*) $MESSAGE_CORE\\.\n$MESSAGE_END (.*)\\.\n")
  }
}