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
package com.android.tools.idea.editing.documentation.target

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember

internal sealed class AndroidSdkMemberDocumentationTarget<T : PsiMember>(
  targetElement: T,
  protected val containingClass: PsiClass,
  sourceElement: PsiElement?,
  url: String,
  localJavaDocInfo: String?,
) : AndroidSdkDocumentationTarget<T>(targetElement, sourceElement, url, localJavaDocInfo) {
  override val displayName = containingClass.qualifiedName + "#" + targetElement.name

  override fun create(
    targetElement: T,
    sourceElement: PsiElement?,
    url: String,
    localJavaDocInfo: String?,
  ) =
    targetElement.containingClass?.let {
      create(targetElement, it, sourceElement, url, localJavaDocInfo)
    }

  /** Creates a new [AndroidSdkMemberDocumentationTarget] with the given parameters. */
  protected abstract fun create(
    targetElement: T,
    containingClass: PsiClass,
    sourceElement: PsiElement?,
    url: String,
    localJavaDocInfo: String?,
  ): AndroidSdkMemberDocumentationTarget<T>?
}
