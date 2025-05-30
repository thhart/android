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
package com.android.tools.idea.gradle.dsl.model.repositories

import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel
import com.android.tools.idea.gradle.dsl.parser.elements.EmptyGradleBlockModel

class EmptyRepositoriesModelImpl : EmptyGradleBlockModel(), RepositoriesModel {
  override fun repositories(): List<RepositoryModel> = listOf()

  override fun removeRepository(repository: RepositoryModel) =
    throw UnsupportedOperationException("Call is not supported for Declarative")

  override fun addRepositoryByMethodName(methodName: String): Boolean = false

  override fun addFlatDirRepository(dirName: String) =
    throw UnsupportedOperationException("Call is not supported for Declarative")

  override fun containsMethodCall(methodName: String): Boolean = false

  override fun addMavenRepositoryByUrl(url: String, name: String?) =
    throw UnsupportedOperationException("Call is not supported for Declarative")

  override fun containsMavenRepositoryByUrl(repositoryUrl: String): Boolean = false

  override fun removeRepositoryByUrl(repositoryUrl: String): Boolean =
    throw UnsupportedOperationException("Call is not supported for Declarative")

  override fun hasGoogleMavenRepository(): Boolean = false

  override fun addGoogleMavenRepository() =
    throw UnsupportedOperationException("Call is not supported for Declarative")
}