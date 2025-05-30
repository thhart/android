/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.res.ids

/**
 * [ResourceIdManager] implementation with fixed final ids. Convenient for testing.
 *
 * @param useRBytecodeParsing When true, the R classes belonging to this Module will be loaded using
 *   bytecode parsing and not reflection.
 * @param frameworkResourceIdsProvider [FrameworkResourceIdsProvider] used to obtain the framework R class ids.
 */
open class StubbedResourceIdManager internal constructor(
  useRBytecodeParsing: Boolean,
  frameworkResourceIdsProvider: FrameworkResourceIdsProvider
) : ResourceIdManagerBase(ResourceIdManagerModelModule.noNamespacingApp(useRBytecodeParsing), false, frameworkResourceIdsProvider) {
  constructor(useRBytecodeParsing: Boolean): this(useRBytecodeParsing, FrameworkResourceIdsProvider.getInstance())
}
