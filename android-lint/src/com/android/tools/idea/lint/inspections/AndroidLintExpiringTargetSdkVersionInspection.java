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
package com.android.tools.idea.lint.inspections;

import com.android.tools.idea.lint.AndroidLintBundle;
import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.LintIdeQuickFix;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLintExpiringTargetSdkVersionInspection extends AndroidLintInspectionBase {
  public AndroidLintExpiringTargetSdkVersionInspection() {
    super(AndroidLintBundle.message("android.lint.inspections.expiring.target.sdk.version"), GradleDetector.EXPIRING_TARGET_SDK_VERSION);
  }

  @NotNull
  @Override
  public LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                         @NotNull PsiElement endElement,
                                         @NotNull String message,
                                         @Nullable LintFix fixData) {
    return new LintIdeQuickFix[]{new LaunchTargetSdkVersionAssistantFix(fixData)};
  }
}
