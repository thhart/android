/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.ide.gradle.model.builder

import com.android.ide.gradle.model.GradlePropertiesModel
import com.android.ide.gradle.model.impl.GradlePropertiesModelImpl
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.util.GradleVersion
import java.util.Locale

/**
 * An injected Gradle tooling model builder to fetch Gradle properties.
 */
class GradlePropertiesModelBuilder : ToolingModelBuilder {

  override fun canBuild(modelName: String): Boolean {
    return modelName == GradlePropertiesModel::class.java.name
  }

  override fun buildAll(modelName: String, project: Project): GradlePropertiesModel {
    check(canBuild(modelName)) { "Unexpected model name requested: $modelName" }
    return GradlePropertiesModelImpl(
      useAndroidX = getGradlePropertyBooleanValue(USE_ANDROID_X_PROPERTY, project),
      excludeLibraryComponentsFromConstraints = getGradlePropertyBooleanValue(EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS_PROPERTY, project)
                                                ?: getGradlePropertyBooleanValue(
                                                  EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS_PROPERTY_EXPERIMENTAL, project),
      generateManifestClass = getGradlePropertyBooleanValue(GENERATE_MANIFEST_CLASS_PROPERTY, project),
    )
  }

  private fun getGradlePropertyBooleanValue(propertyName: String, project: Project): Boolean? {
    // Project isolation support for applying AndroidStudioToolingPlugin starts in 8.8, so it doesn't make a difference for choosing an
    // earlier gradle version here.
    return if (GradleVersion.current() >= GradleVersion.version("8.8")) {
      project.providers.gradleProperty(propertyName).orNull?.toBoolean()
    } else {
      project.findProperty(propertyName)?.toBoolean()
    }
  }

  // Modelled from AGP's logic in OptionParsers.kt
  private fun Any.toBoolean(): Boolean? = when (this) {
    is Boolean -> this
    is CharSequence ->
      when (toString().lowercase(Locale.US)) {
        "true" -> true
        "false" -> false
        else -> null
      }
    is Number ->
      when (toInt()) {
        0 -> false
        1 -> true
        else -> null
      }
    else -> null
  }
}

private const val USE_ANDROID_X_PROPERTY = "android.useAndroidX"
private const val EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS_PROPERTY = "android.dependency.excludeLibraryComponentsFromConstraints"
private const val EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS_PROPERTY_EXPERIMENTAL = "android.experimental.dependency.excludeLibraryComponentsFromConstraints"
private const val GENERATE_MANIFEST_CLASS_PROPERTY = "android.generateManifestClass"