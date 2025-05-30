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
package com.android.tools.idea.gradle.dsl.model.kotlin;

import static com.android.tools.idea.gradle.dsl.model.kotlin.KotlinSourceSetDslElement.KOTLIN_SOURCE_SET;
import static com.android.tools.idea.gradle.dsl.model.kotlin.KotlinSourceSetsDslElement.KOTLIN_SOURCE_SETS;
import static com.android.tools.idea.gradle.dsl.parser.kotlin.CompilerOptionsDslElement.COMPILER_OPTIONS;

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.kotlin.CompilerOptionsModel;
import com.android.tools.idea.gradle.dsl.api.kotlin.KotlinModel;
import com.android.tools.idea.gradle.dsl.api.kotlin.KotlinSourceSetModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.kotlin.CompilerOptionsDslElement;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class KotlinModelImpl extends GradleDslBlockModel implements KotlinModel {

  @NonNls public static final String JVM_TOOLCHAIN = "mJvmToolchain";

  public KotlinModelImpl(@NotNull GradlePropertiesDslElement dslElement) {
    super(dslElement);
  }

  @Override
  public @NotNull ResolvedPropertyModel jvmToolchain() {
    return getLanguageModelForProperty(JVM_TOOLCHAIN);
  }

  @Override
  public @NotNull CompilerOptionsModel compilerOptions() {
    CompilerOptionsDslElement compilerOptionsDslElement = myDslElement.ensurePropertyElement(COMPILER_OPTIONS);
    return new CompilerOptionsModelImpl(compilerOptionsDslElement);
  }

  @Override
  public @NotNull List<KotlinSourceSetModel> sourceSets() {
    KotlinSourceSetsDslElement sourceSets = myDslElement.getPropertyElement(KOTLIN_SOURCE_SETS);
    return sourceSets == null ? ImmutableList.of() : sourceSets.get();
  }

  @Override
  public @NotNull KotlinSourceSetModel addSourceSet(@NotNull String name) {
    KotlinSourceSetsDslElement sourceSets = myDslElement.ensurePropertyElement(KOTLIN_SOURCE_SETS);
    KotlinSourceSetDslElement sourceSetElement = sourceSets.ensureNamedPropertyElement(KOTLIN_SOURCE_SET, GradleNameElement.create(name));
    return new KotlinSourceSetModelImpl(sourceSetElement);
  }

  @Override
  public void removeSourceSet(@NotNull String name) {
    KotlinSourceSetsDslElement sourceSets = myDslElement.getPropertyElement(KOTLIN_SOURCE_SETS);
    if (sourceSets != null) {
      sourceSets.removeProperty(name);
    }
  }
}