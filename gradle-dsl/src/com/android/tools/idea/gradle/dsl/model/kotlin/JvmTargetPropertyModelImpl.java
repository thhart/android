/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.tools.idea.gradle.dsl.api.util.LanguageLevelUtil.parseFromGradleString;

import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.LanguageLevelUtil;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.ResolvedPropertyModelImpl;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class JvmTargetPropertyModelImpl extends ResolvedPropertyModelImpl implements LanguageLevelPropertyModel {
  private static final Logger LOG = Logger.getInstance(JvmTargetPropertyModelImpl.class);

  private static final String docLink = "https://kotlinlang.org/docs/gradle-compiler-options.html#attributes-specific-to-jvm";
  private static final ImmutableMap<Integer, String> allowedTargets = ImmutableMap.<Integer, String>builder()
    .put(6, "1.6")
    .put(8, "1.8")
    .put(9, "9")
    .put(10, "10")
    .put(11, "11")
    .put(12, "12")
    .put(13, "13")
    .put(14, "14")
    .put(15, "15")
    .put(16, "16")
    .put(17, "17")
    .put(18, "18")
    .put(19, "19")
    .put(20, "20")
    .put(21, "21")
    .put(22, "22")
    .build();

  public JvmTargetPropertyModelImpl(@NotNull GradlePropertyModelImpl realModel) {
    super(realModel);
  }

  @TestOnly
  @Nullable
  @Override
  public LanguageLevel toLanguageLevel() {
    String stringToParse = LanguageLevelUtil.getStringToParse(this);
    return stringToParse == null ? null : parseFromGradleString(stringToParse);
  }

  @Override
  public void setLanguageLevel(@NotNull LanguageLevel level) {
    int major = level.feature();
    if (allowedTargets.containsKey(major)) {
      setValue(allowedTargets.get(major));
      return;
    }
    LOG.warn(new IllegalArgumentException(
      "Kotlin jvmTarget does not support Java " + major + ". See " + docLink + " for a list of supported targets."));
  }
}
