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
package com.android.tools.idea.common.surface.organization

import com.android.tools.idea.common.surface.SceneView
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet

/**
 * Find [OrganizationGroup]s for target list of [SceneViewHeader], each group should have size > 1.
 */
fun Collection<SceneView>.findGroups(): ImmutableSet<OrganizationGroup> =
  this.mapNotNull { it.sceneManager.model.organizationGroup }
    .groupingBy { it }
    .eachCount()
    .filterValues { count -> count > 1 }
    .map { it.key }
    .toImmutableSet()
