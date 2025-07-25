/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.hitproviders

import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.idea.common.scene.HitProvider
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.ScenePicker
import com.android.tools.idea.common.scene.inlineDrawRect
import com.android.tools.idea.naveditor.model.popUpTo

abstract class NavActionHitProviderBase : HitProvider {
  final override fun addHit(
    component: SceneComponent,
    sceneTransform: SceneContext,
    picker: ScenePicker.Writer,
  ) {
    addShapeHit(component, sceneTransform, picker)

    if (component.nlComponent.popUpTo == null) {
      return
    }

    iconRectangle(component, sceneTransform)?.let {
      picker.addRect(
        component,
        0,
        it.x.toInt(),
        it.y.toInt(),
        (it.x + it.width).toInt(),
        (it.y + it.height).toInt(),
      )
    }
  }

  abstract fun addShapeHit(
    component: SceneComponent,
    sceneTransform: SceneContext,
    picker: ScenePicker.Writer,
  )

  abstract fun iconRectangle(
    component: SceneComponent,
    sceneTransform: SceneContext,
  ): SwingRectangle?

  protected fun sourceRectangle(
    component: SceneComponent,
    sceneTransform: SceneContext,
  ): SwingRectangle? {
    val source =
      component.nlComponent.parent?.let { component.scene.root?.getSceneComponent(it) }
        ?: return null
    return source.inlineDrawRect(sceneTransform)
  }
}
