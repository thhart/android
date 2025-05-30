/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run.configuration

import com.intellij.psi.PsiElement

/**
 * Producer of [AndroidTileConfiguration] for classes that extend `androidx.wear.tiles.TileService`.
 */
class AndroidTileRunConfigurationProducer :
  AndroidWearRunConfigurationProducer<AndroidTileConfiguration>(AndroidTileConfigurationType::class.java) {

  override fun isValidService(psiElement: PsiElement): Boolean = psiElement.isValidTileService()
}

internal fun PsiElement.isValidTileService(): Boolean {
  return WearBaseClasses.TILES.any { wearBase -> isSubtypeOf(wearBase) }
}
