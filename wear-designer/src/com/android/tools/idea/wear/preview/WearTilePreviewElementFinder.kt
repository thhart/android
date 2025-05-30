/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.wear.preview

import com.android.SdkConstants
import com.android.annotations.concurrency.Slow
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.preview.find.AnnotationPreviewNameHelper
import com.android.tools.idea.preview.find.FilePreviewElementFinder
import com.android.tools.idea.preview.find.NodeInfo
import com.android.tools.idea.preview.find.UAnnotationSubtreeInfo
import com.android.tools.idea.preview.find.UastAnnotatedMethod
import com.android.tools.idea.preview.find.UastAnnotationAttributesProvider
import com.android.tools.idea.preview.find.findAllAnnotationsInGraph
import com.android.tools.idea.preview.find.findPreviewDefaultValues
import com.android.tools.idea.preview.find.toSmartPsiPointer
import com.android.tools.wear.preview.previewAnnotationToWearTilePreviewElement
import com.android.utils.cache.ChangeTracker
import com.android.utils.cache.ChangeTrackerCachedValue
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.removeUserData
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.idea.util.projectStructure.getModule
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement

private const val TILE_PREVIEW_ANNOTATION_NAME = "Preview"
const val TILE_PREVIEW_ANNOTATION_FQ_NAME =
  "androidx.wear.tiles.tooling.preview.$TILE_PREVIEW_ANNOTATION_NAME"
const val TILE_PREVIEW_DATA_FQ_NAME = "androidx.wear.tiles.tooling.preview.TilePreviewData"

// For these keys we store Deferred<T> instead of just T as it can take a while to calculate the
// values. However, creating the deferred itself should be very fast, meaning it can be cached
// fast and re-used fast.
// This way additional calls made before the value has finished being calculated won't result
// in additional calculations being "piled on". Instead, they can just wait on the deferred.
private val hasPreviewElementsCacheKey =
  Key<ChangeTrackerCachedValue<Deferred<Boolean>>>("hasPreviewElements")
private val previewElementsCacheKey =
  Key<ChangeTrackerCachedValue<Deferred<Collection<PsiWearTilePreviewElement>>>>("previewElements")
private val uMethodsWithTilePreviewSignatureCacheKey =
  Key<ChangeTrackerCachedValue<Deferred<List<UMethod>>>>("uMethodsWithTilePreviewSignature")
private val isTileAnnotationUsedCacheKey =
  Key<ChangeTrackerCachedValue<Deferred<Boolean>>>("isTileAnnotationUsed")

/**
 * Object that can detect wear tile preview elements in a file.
 *
 * @param findMethods a function that returns all [PsiMethod]s and [KtNamedFunction]s for a given
 *   [PsiFile]. The method will be invoked under a read lock.
 */
internal class WearTilePreviewElementFinder(
  @RequiresReadLock
  private val findMethods: (PsiFile?) -> Collection<PsiElement> = { psiFile ->
    PsiTreeUtil.findChildrenOfAnyType(psiFile, PsiMethod::class.java, KtNamedFunction::class.java)
  }
) : FilePreviewElementFinder<PsiWearTilePreviewElement> {

  /**
   * Checks if a given [vFile] contains any [PsiWearTilePreviewElement]s. Results of this method
   * will be cached until there are changes to any java or kotlin files in the given [project] or
   * when there are smart mode changes to the [project]. It's also possible for the cached value to
   * be garbage collected, in which case the results will be recomputed.
   */
  override suspend fun hasPreviewElements(project: Project, vFile: VirtualFile): Boolean {
    return try {
      withContext(workerThread) {
        if (!isTileAnnotationUsed(project, vFile)) {
          return@withContext false
        }
        cachedAsyncValue(
          vFile,
          hasPreviewElementsCacheKey,
          project.javaKotlinAndDumbChangeTrackers(),
        ) {
          findUMethodsWithTilePreviewSignature(project, vFile, findMethods).any {
            it.findAllTilePreviewAnnotations().firstOrNull() != null
          }
        }
      }
    } catch (_: CancellationException) {
      // cached async calls can be cancelled when an async call is out-of-date
      false
    }
  }

  /**
   * Retrieves all [PsiWearTilePreviewElement] in a given [vFile]. Results of this method will be
   * cached until there are changes to any java or kotlin files in the given [project] or when there
   * are smart mode changes to the [project]. It's also possible for the cached value to be garbage
   * collected, in which case the results will be recomputed.
   */
  override suspend fun findPreviewElements(
    project: Project,
    vFile: VirtualFile,
  ): Collection<PsiWearTilePreviewElement> {
    return try {
      withContext(workerThread) {
        cachedAsyncValue(
          vFile,
          previewElementsCacheKey,
          project.javaKotlinAndDumbChangeTrackers(),
        ) {
          findUMethodsWithTilePreviewSignature(project, vFile, findMethods)
            .flatMap { method ->
              ProgressManager.checkCanceled()
              method
                .findAllTilePreviewAnnotations()
                .mapNotNull { it.asTilePreviewNode(method) }
                .toList()
            }
            .distinct()
        }
      }
    } catch (_: CancellationException) {
      // cached async calls can be cancelled when an async call is out-of-date
      emptyList()
    }
  }
}

/**
 * Returns true if a [UMethod] or [UAnnotation] is not null is annotated with a Tile Preview
 * annotation, either directly or through a Multi-Preview annotation.
 */
@RequiresBackgroundThread
fun UElement?.hasTilePreviewAnnotation(): Boolean {
  assert(this is UMethod? || this is UAnnotation?) {
    "The UElement should be either a UMethod or a UAnnotation"
  }
  // TODO(b/381827960): avoid using runBlockingCancellable
  return runBlockingCancellable {
    this@hasTilePreviewAnnotation?.findAllTilePreviewAnnotations()?.firstOrNull() != null
  }
}

/**
 * Checks if a [UAnnotation] is a Wear Tile `@Preview` annotation.
 *
 * This method must be called under a read lock.
 */
@RequiresReadLock
internal fun UAnnotation.isTilePreviewAnnotation() =
  this.qualifiedName == TILE_PREVIEW_ANNOTATION_FQ_NAME

/**
 * Returns true if the [UElement] is a `@Preview` annotation.
 *
 * This method must be called under a read lock.
 */
@RequiresReadLock
private fun UElement?.isWearTilePreviewAnnotation() =
  (this as? UAnnotation)?.isTilePreviewAnnotation() == true

@Slow
private suspend fun NodeInfo<UAnnotationSubtreeInfo>.asTilePreviewNode(
  uMethod: UMethod
): PsiWearTilePreviewElement? {
  val annotation = element as UAnnotation
  if (readAction { !annotation.isTilePreviewAnnotation() }) return null

  val defaultValues = readAction { annotation.findPreviewDefaultValues() }

  val rootAnnotation = subtreeInfo?.topLevelAnnotation ?: annotation
  val attributesProvider = UastAnnotationAttributesProvider(annotation, defaultValues)
  val previewElementDefinitionPsi = readAction { rootAnnotation.toSmartPsiPointer() }
  val annotatedMethod =
    UastAnnotatedMethod(
      method = uMethod,
      // currently preview parameters are not supported for tile previews
      previewParameterAnnotationFqns = setOf(),
    )
  val nameHelper =
    AnnotationPreviewNameHelper.create(this, readAction { annotatedMethod.name }) {
      readAction { isWearTilePreviewAnnotation() }
    }

  return readAction {
    previewAnnotationToWearTilePreviewElement(
      attributesProvider = attributesProvider,
      annotatedMethod = annotatedMethod,
      previewElementDefinition = previewElementDefinitionPsi,
      buildPreviewName = nameHelper::buildPreviewName,
      buildParameterName = nameHelper::buildParameterName,
    )
  }
}

/**
 * Retrieves all [UMethod]s in a given [virtualFile] that have a Tile Preview signature. Results of
 * this method will be cached until there are changes to any java or kotlin files in the given
 * [project] or when there are smart mode changes to the [project]. It's also possible for the
 * cached value to be garbage collected, in which case the results will be recomputed.
 *
 * @see isMethodWithTilePreviewSignature for details on what a tile preview signature should be
 */
@Slow
private suspend fun CoroutineScope.findUMethodsWithTilePreviewSignature(
  project: Project,
  virtualFile: VirtualFile,
  @RequiresReadLock findMethods: (PsiFile?) -> Collection<PsiElement>,
): List<UMethod> {
  return cachedAsyncValue(
    virtualFile,
    uMethodsWithTilePreviewSignatureCacheKey,
    project.javaKotlinAndDumbChangeTrackers(),
  ) {
    findUMethodsWithTilePreviewSignatureNonCached(project, virtualFile, findMethods)
  }
}

@Slow
private suspend fun findUMethodsWithTilePreviewSignatureNonCached(
  project: Project,
  virtualFile: VirtualFile,
  @RequiresReadLock findMethods: (PsiFile?) -> Collection<PsiElement>,
): List<UMethod> {
  val pointerManager = SmartPointerManager.getInstance(project)
  return smartReadAction(project) {
      if (!virtualFile.isValid) {
        return@smartReadAction emptyList()
      }
      findMethods(virtualFile.toPsiFile(project))
        .filter { it.isValid }
        .map {
          ProgressManager.checkCanceled()
          pointerManager.createSmartPsiElementPointer(it)
        }
    }
    .filter { smartReadAction(project) { it.element?.isMethodWithTilePreviewSignature() } ?: false }
    .mapNotNull { smartReadAction(project) { it.element.toUElement(UMethod::class.java) } }
}

private fun UElement.findAllTilePreviewAnnotations() = findAllAnnotationsInGraph {
  readAction { it.isTilePreviewAnnotation() }
}

/**
 * Checks if a [PsiElement] is a method with the signature required for a Tile Preview. The expected
 * signature of a Tile Preview method is to have the return type [TILE_PREVIEW_DATA_FQ_NAME] and to
 * have either no parameters or single parameter of type [SdkConstants.CLASS_CONTEXT].
 *
 * To be considered a method, the [PsiElement] should be either a [PsiMethod] or a
 * [KtNamedFunction].
 */
@RequiresReadLock
internal fun PsiElement?.isMethodWithTilePreviewSignature(): Boolean {
  ProgressManager.checkCanceled()
  val hasValidReturnType =
    when (val sourcePsi = this) {
      is PsiMethod -> sourcePsi.returnType?.equalsToText(TILE_PREVIEW_DATA_FQ_NAME) == true
      is KtNamedFunction -> {
        analyze(sourcePsi) {
          val symbol = sourcePsi.symbol
          val returnType = symbol.returnType as? KaClassType
          returnType?.classId?.asSingleFqName()?.asString() == TILE_PREVIEW_DATA_FQ_NAME
        }
      }
      else -> false
    }

  if (!hasValidReturnType) {
    return false
  }

  ProgressManager.checkCanceled()
  val hasNoParameters =
    when (this) {
      is PsiMethod -> !hasParameters()
      is KtNamedFunction -> getValueParameters().isEmpty()
      else -> false
    }
  if (hasNoParameters) {
    return true
  }

  ProgressManager.checkCanceled()
  val hasSingleContextParameter =
    when (this) {
      is PsiMethod ->
        parameterList.parametersCount == 1 &&
          parameterList.getParameter(0)?.type?.equalsToText(SdkConstants.CLASS_CONTEXT) == true
      is KtNamedFunction -> {
        val typeReference = valueParameters.singleOrNull()?.typeReference
        if (typeReference != null) {
          analyze(typeReference) {
            val ktType = typeReference.type as? KaClassType
            ktType?.classId?.asSingleFqName()?.asString() == SdkConstants.CLASS_CONTEXT
          }
        } else false
      }
      else -> false
    }
  return hasSingleContextParameter
}

private fun <T> UserDataHolder.getOrCreateCachedValue(
  key: Key<ChangeTrackerCachedValue<T>>,
  create: () -> ChangeTrackerCachedValue<T>,
) = getUserData(key) ?: create().also { putUserData(key, it) }

private fun Project.javaKotlinAndDumbChangeTrackers() =
  ChangeTracker(
    ChangeTracker {
      PsiModificationTracker.getInstance(this)
        .forLanguages { lang ->
          lang.`is`(KotlinLanguage.INSTANCE) || lang.`is`(JavaLanguage.INSTANCE)
        }
        .modificationCount
    },
    ChangeTracker { DumbService.getInstance(this).modificationTracker.modificationCount },
  )

/**
 * This method looks up the annotations indexes in order to see if the tile preview annotation is
 * used. If the annotation is never used, then we don't need to iterate over methods to search for
 * previews. We search the module as well as its dependencies and libraries as a preview can be
 * using a Multi-Preview declared in another module or library.
 */
@Slow
private suspend fun CoroutineScope.isTileAnnotationUsed(
  project: Project,
  vFile: VirtualFile,
): Boolean {
  val module = vFile.getModule(project) ?: return false
  return cachedAsyncValue(
    module,
    isTileAnnotationUsedCacheKey,
    project.javaKotlinAndDumbChangeTrackers(),
  ) {
    smartReadAction(project) {
      val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
      KotlinAnnotationsIndex[TILE_PREVIEW_ANNOTATION_NAME, project, scope].any() ||
        JavaAnnotationIndex.getInstance()
          .getAnnotations(TILE_PREVIEW_ANNOTATION_NAME, project, scope)
          .any()
    }
  }
}

private suspend fun <T> CoroutineScope.cachedAsyncValue(
  dataHolder: UserDataHolder,
  cacheKey: Key<ChangeTrackerCachedValue<Deferred<T>>>,
  vararg dependencies: ChangeTracker,
  valueProvider: suspend () -> T,
): T {
  val cachedValue =
    dataHolder.getOrCreateCachedValue(cacheKey) {
      // weakReferences get cleared almost immediately by the gc, it's best to use softReference
      ChangeTrackerCachedValue.softReference()
    }
  return ChangeTrackerCachedValue.get(
      cachedValue,
      {
        async { valueProvider() }
          .also {
            it.invokeOnCompletion { throwable ->
              if (throwable != null) {
                // ensure we don't cache a failed deferred
                dataHolder.removeUserData(cacheKey)
              }
            }
          }
      },
      ChangeTracker(*dependencies),
    )
    .await()
}
