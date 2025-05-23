/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java;

import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.java.AndroidBlazeRules.RuleTypes;

/**
 * This is a utility class for methods concerning the target kinds {@link Kind} corresponding to the
 * Java blaze rules and the Android-specific blaze rules.
 */
public final class TargetKindUtil {
  public static boolean isLocalTest(Kind targetKind) {
    return isAndroidLocalTest(targetKind) || isJavaTest(targetKind);
  }

  public static boolean isAndroidLocalTest(Kind targetKind) {
    return targetKind != null
        && (targetKind.equals(RuleTypes.ANDROID_LOCAL_TEST.getKind())
            || targetKind.equals(RuleTypes.WRAPPED_ANDROID_LOCAL_TEST.getKind())
            || targetKind.equals(RuleTypes.KT_ANDROID_LOCAL_TEST.getKind()));
  }

  public static boolean isJavaTest(Kind targetKind) {
    return targetKind != null && targetKind.equals(JavaBlazeRules.RuleTypes.JAVA_TEST.getKind());
  }

  private TargetKindUtil() {}
}
