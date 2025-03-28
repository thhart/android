/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.property.panel.api

/** Listener for [PropertiesModel] events. */
interface PropertiesModelListener<P : PropertyItem> {
  /**
   * The property items in the [PropertiesModel] were (re)generated.
   *
   * There may be different reasons why this happened. One example: the user selected different
   * object to display properties for.
   */
  fun propertiesGenerated(model: PropertiesModel<P>) {}

  /**
   * An update to 1 or more property values happened.
   *
   * The property items in the [PropertiesModel] are still the same.
   */
  fun propertyValuesChanged(model: PropertiesModel<P>) {}
}
