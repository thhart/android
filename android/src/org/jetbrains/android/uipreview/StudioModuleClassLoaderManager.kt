/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android.uipreview

import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.rendering.AndroidFacetRenderModelModule
import com.android.tools.idea.rendering.StudioModuleRenderContext
import com.android.tools.idea.util.androidFacet
import com.android.tools.rendering.classloading.ClassTransform
import com.android.tools.rendering.classloading.ModuleClassLoadedDiagnosticsImpl
import com.android.tools.rendering.classloading.ModuleClassLoader
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.rendering.classloading.NopModuleClassLoadedDiagnostics
import com.android.tools.rendering.classloading.combine
import com.android.tools.rendering.log.LogAnonymizerUtil.anonymize
import com.android.utils.reflection.qualifiedName
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.removeUserData
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.MultiMap
import org.jetbrains.android.uipreview.StudioModuleClassLoader.NON_PROJECT_CLASSES_DEFAULT_TRANSFORMS
import org.jetbrains.android.uipreview.StudioModuleClassLoader.PROJECT_DEFAULT_TRANSFORMS
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.lang.ref.WeakReference

private fun throwIfNotUnitTest(e: Exception) =
  if (!ApplicationManager.getApplication().isUnitTestMode) {
    throw e
  } else {
    Logger.getInstance(ModuleClassLoaderProjectHelperService::class.java)
      .info(
        "ModuleClassLoaderProjectHelperService is disabled for unit testing since there is no ProjectSystemBuildManager"
      )
  }

/** This helper service listens for builds and cleans the module cache after it finishes. */
@Service(Service.Level.PROJECT)
private class ModuleClassLoaderProjectHelperService(val project: Project) :
  ProjectSystemBuildManager.BuildListener, Disposable {
  init {
    // IntelliJ might sometimes create project services even when the project is in the
    // process of being disposed.
    if (!project.isDisposed) {
      try {
        ProjectSystemService.getInstance(project)
          .projectSystem
          .getBuildManager()
          .addBuildListener(this, this)
      }
      catch (e: IllegalStateException) {
        throwIfNotUnitTest(e)
      }
      catch (e: UnsupportedOperationException) {
        throwIfNotUnitTest(e)
      }
    }
  }

  @RequiresBackgroundThread
  private fun clearCaches() {
    StudioModuleClassLoaderManager.get().clearCache(project)
  }

  override fun beforeBuildCompleted(result: ProjectSystemBuildManager.BuildResult) {
    if (
      result.status == ProjectSystemBuildManager.BuildStatus.SUCCESS &&
        result.mode == ProjectSystemBuildManager.BuildMode.COMPILE_OR_ASSEMBLE
    ) {
      ApplicationManager.getApplication().executeOnPooledThread { clearCaches() }
    }
  }

  override fun dispose() {
    // Dispose usually runs on the EDT thread. Calling clearCaches on it can cause a deadlock
    // since the StudioModuleClassLoaderManager might hold the lock and try to acquire the read lock.
    ApplicationManager.getApplication().executeOnPooledThread {
      clearCaches()
    }
  }
}

private val PRELOADER: Key<StudioPreloader> =
  Key.create(::PRELOADER.qualifiedName<StudioModuleClassLoaderManager>())
val HATCHERY: Key<ModuleClassLoaderHatchery> =
  Key.create(::HATCHERY.qualifiedName<StudioModuleClassLoaderManager>())

private fun <T> UserDataHolder.getOrCreate(key: Key<T>, factory: () -> T): T {
  getUserData(key)?.let {
    return it
  }
  return factory().also { putUserData(key, it) }
}

@VisibleForTesting
private fun Module.getOrCreateHatchery() =
  getOrCreate(HATCHERY) {
    if (!isDisposed) ModuleClassLoaderHatchery(parentDisposable = this)
    else throw AlreadyDisposedException("Module was already disposed")
  }

/** A [ClassLoader] for the [Module] dependencies. */
class StudioModuleClassLoaderManager :
  ModuleClassLoaderManager<StudioModuleClassLoader>, Disposable {
  // MutableSet is backed by the WeakHashMap in prod so we do not retain the holders
  /**
   * Creates a [MultiMap] to be used as a storage of [ModuleClassLoader] holders. We would like the
   * implementation to be different in prod and in tests:
   *
   * In Prod, it should be a Set of value WEAK references. So that in case we do not release the
   * holder (due to some unexpected flow) it is not retained by the [StudioModuleClassLoaderManager]
   *
   * In Tests, we would like it to be a Set of STRONG references. So that any unreleased references
   * got caught by the LeakHunter.
   */
  private val holders: MultiMap<StudioModuleClassLoader, ModuleClassLoaderManager.Reference<*>> =
    if (ApplicationManager.getApplication().isUnitTestMode) WeakMultiMap.create()
    else WeakMultiMap.createWithWeakValues()

  override fun dispose() {
    val iterator = holders.keySet().iterator()
    for (key in iterator) {
      iterator.remove()
      key.module?.let { m -> clearModuleData(m) }
    }
  }

  @TestOnly
  fun assertNoClassLoadersHeld() {
    if (!holders.isEmpty) {
      val referencesString =
        holders.entrySet().joinToString { "${it.key} held by ${it.value.joinToString(", ")}" }
      throw AssertionError(
        "Class loaders were not released correctly by the tests\n$referencesString\n"
      )
    }
  }

  /**
   * Returns a project class loader to use for rendering. May cache instances across render
   * sessions.
   */
  @Synchronized
  @JvmOverloads
  fun getShared(
    parent: ClassLoader?,
    moduleRenderContext: StudioModuleRenderContext,
    additionalProjectTransformation: ClassTransform = ClassTransform.identity,
    additionalNonProjectTransformation: ClassTransform = ClassTransform.identity,
    onNewModuleClassLoader: Runnable = Runnable {},
  ): ModuleClassLoaderManager.Reference<StudioModuleClassLoader> {
    val module: Module? = moduleRenderContext.buildTargetReference.moduleIfNotDisposed
    var moduleClassLoader = module?.getUserData(PRELOADER)?.getClassLoader()
    val combinedProjectTransformations: ClassTransform by lazy {
      combine(PROJECT_DEFAULT_TRANSFORMS, additionalProjectTransformation)
    }
    val combinedNonProjectTransformations: ClassTransform by lazy {
      combine(NON_PROJECT_CLASSES_DEFAULT_TRANSFORMS, additionalNonProjectTransformation)
    }

    var oldClassLoader: StudioModuleClassLoader? = null
    if (moduleClassLoader != null) {
      val invalidate =
        moduleClassLoader.isDisposed ||
          !moduleClassLoader.isCompatible(
            parent,
            combinedProjectTransformations,
            combinedNonProjectTransformations,
          ) ||
          !moduleClassLoader.isUserCodeUpToDate

      if (invalidate) {
        oldClassLoader = moduleClassLoader
        moduleClassLoader = null
      }
    }

    if (moduleClassLoader == null) {
      // Make sure the helper service is initialized
      module
        ?.project
        ?.getService(ModuleClassLoaderProjectHelperService::class.java)
      if (LOG.isDebugEnabled) {
        LOG.debug {
          "Loading new class loader for module ${module?.androidFacet?.let { anonymize(AndroidFacetRenderModelModule.loggingId(it)) }}"
        }
      }
      val preloadedClassLoader: StudioModuleClassLoader? =
        module
          ?.getOrCreateHatchery()
          ?.requestClassLoader(
            parent,
            combinedProjectTransformations,
            combinedNonProjectTransformations,
          )
      moduleClassLoader =
        preloadedClassLoader
          ?: StudioModuleClassLoader(
            parent,
            moduleRenderContext,
            combinedProjectTransformations,
            combinedNonProjectTransformations,
            createDiagnostics(),
          )
      module?.putUserData(PRELOADER, StudioPreloader(moduleClassLoader))
      onNewModuleClassLoader.run()
    }

    oldClassLoader?.let {
      if (stopManagingIfNotHeld(it)) {
        it.dispose()
      }
    }
    val newModuleClassLoaderReference =
      ModuleClassLoaderManager.Reference(moduleClassLoader, ModuleClassLoaderReferenceReleaser(this)).also {
        LOG.debug { "New ModuleClassLoader reference $it to $moduleClassLoader" }
      }
    holders.putValue(moduleClassLoader, newModuleClassLoaderReference)
    return newModuleClassLoaderReference
  }

  /**
   * Return a [StudioModuleClassLoader] for a [Module] to be used for rendering. Similar to
   * [getShared] but guarantees that the returned [StudioModuleClassLoader] is not shared and the
   * caller has full ownership of it.
   */
  @Synchronized
  @JvmOverloads
  fun getPrivate(
    parent: ClassLoader?,
    moduleRenderContext: StudioModuleRenderContext,
    additionalProjectTransformation: ClassTransform  = ClassTransform.identity,
    additionalNonProjectTransformation: ClassTransform  = ClassTransform.identity,
  ): ModuleClassLoaderManager.Reference<StudioModuleClassLoader> {
    // Make sure the helper service is initialized
    val module: Module? = moduleRenderContext.buildTargetReference.moduleIfNotDisposed
    module
      ?.project
      ?.getService(ModuleClassLoaderProjectHelperService::class.java)

    val combinedProjectTransformations =
      combine(PROJECT_DEFAULT_TRANSFORMS, additionalProjectTransformation)
    val combinedNonProjectTransformations =
      combine(NON_PROJECT_CLASSES_DEFAULT_TRANSFORMS, additionalNonProjectTransformation)
    val preloadedClassLoader: StudioModuleClassLoader? =
      module
        ?.getOrCreateHatchery()
        ?.requestClassLoader(
          parent,
          combinedProjectTransformations,
          combinedNonProjectTransformations,
        )
    return (preloadedClassLoader
        ?: StudioModuleClassLoader(
          parent,
          moduleRenderContext,
          combinedProjectTransformations,
          combinedNonProjectTransformations,
          createDiagnostics(),
        ))
      .let {
        val newModuleClassLoaderReference = ModuleClassLoaderManager.Reference(it, ModuleClassLoaderReferenceReleaser(this))
        LOG.debug { "New ModuleClassLoader reference $newModuleClassLoaderReference to $it" }
        holders.putValue(it, newModuleClassLoaderReference)
        newModuleClassLoaderReference
      }
  }

  @VisibleForTesting
  fun createCopy(mcl: StudioModuleClassLoader): StudioModuleClassLoader? =
    mcl.copy(createDiagnostics())

  private fun clearModuleData(module: Module) {
    module.removeUserData(PRELOADER)?.dispose()
    module.getUserData(HATCHERY)?.destroy()
  }

  private fun clearCaches(filter: (Module) -> Boolean) {
    val modules = holders
      .keySet()
      .toList() // Convert to list since we will be removing elements later
      .mapNotNull { it.module?.let { m -> m to it } }
      .filter { it.first.isDisposed || filter(it.first) }
      .onEach {
        holders.remove(it.second)
      }
      .mapTo(mutableSetOf()) { it.first }
    modules.forEach(::clearModuleData)

    // Clear entries for class loaders that are disposed or are pointing to disposed modules.
    holders.entrySet().toList()
      .onEach {
        if (it.key.isDisposed || it.key.module == null) {
          holders.remove(it.key)
        }
      }
  }

  /**
   * Clears the cached [StudioModuleClassLoader] for the class loaders associated to this [Project].
   */
  @Synchronized
  internal fun clearCache(project: Project) {
    clearCaches {
      it.project == project
    }
  }

  /**
   * Clears the cached [StudioModuleClassLoader] for the class loader associated to this [Module].
   */
  @Synchronized
  override fun clearCache(module: Module) {
    clearModuleData(module)
    clearCaches {
      it.getModuleSystem().getHolderModule() == module.getModuleSystem().getHolderModule()
    }
  }

  @Synchronized
  private fun unHold(moduleClassLoader: ModuleClassLoaderManager.Reference<*>) {
    holders.remove(moduleClassLoader.classLoader as StudioModuleClassLoader, moduleClassLoader)
  }

  @Synchronized
  private fun stopManagingIfNotHeld(moduleClassLoader: StudioModuleClassLoader): Boolean {
    if (holders.containsKey(moduleClassLoader)) {
      return false
    }
    // If that was a shared ModuleClassLoader that is no longer used, we have to destroy the old one
    // to free the resources, but we also
    // recreate a new one for faster load next time
    moduleClassLoader.module?.let { module ->
      if (module.isDisposed) {
        return@let
      }
      if (module.getUserData(PRELOADER)?.isLoadingFor(moduleClassLoader) != true) {
        if (!holders.isEmpty) {
          StudioModuleClassLoaderCreationContext.fromClassLoader(moduleClassLoader)?.let {
            module.getOrCreateHatchery().incubateIfNeeded(it) { donorInformation ->
              StudioModuleClassLoader(
                donorInformation.parent,
                donorInformation.moduleRenderContext,
                donorInformation.projectTransform,
                donorInformation.nonProjectTransformation,
                createDiagnostics(),
              )
            }
          }
        }
      } else {
        module.removeUserData(PRELOADER)?.cancel()
        val newClassLoader = createCopy(moduleClassLoader) ?: return@let
        // We first load dependencies classes and then project classes since the latter reference
        // the former and not vice versa
        val classesToLoad =
          moduleClassLoader.nonProjectLoadedClasses + moduleClassLoader.projectLoadedClasses
        module.putUserData(PRELOADER, StudioPreloader(newClassLoader, classesToLoad))
      }
      if (
        holders.isEmpty
      ) { // If there are no more users of ModuleClassLoader destroy the hatchery to free the
          // resources
        module.getUserData(HATCHERY)?.destroy()
      }
    }
    return true
  }

  /**
   * Inform [StudioModuleClassLoaderManager] that [ModuleClassLoader] is not used anymore and
   * therefore can be disposed if no longer managed.
   */
  fun release(moduleClassLoaderReference: ModuleClassLoaderManager.Reference<*>) {
    LOG.debug { "release reference $moduleClassLoaderReference" }
    val classLoader = moduleClassLoaderReference.classLoader as StudioModuleClassLoader
    unHold(moduleClassLoaderReference)
    if (stopManagingIfNotHeld(classLoader)) {
      classLoader.dispose()
    }
  }

  companion object {
    @JvmStatic val LOG = Logger.getInstance(StudioModuleClassLoaderManager::class.java)

    private var captureDiagnostics = false

    /**
     * If set to true, any class loaders instantiated after this call will record diagnostics about
     * the load time and load counts.
     */
    @TestOnly
    @Synchronized
    fun setCaptureClassLoadingDiagnostics(enabled: Boolean) {
      captureDiagnostics = enabled
    }

    internal fun createDiagnostics() =
      if (captureDiagnostics) ModuleClassLoadedDiagnosticsImpl()
      else NopModuleClassLoadedDiagnostics

    @JvmStatic
    fun get(): StudioModuleClassLoaderManager = application.service<StudioModuleClassLoaderManager>()
  }
}

private class ModuleClassLoaderReferenceReleaser(moduleManager: StudioModuleClassLoaderManager): (ModuleClassLoaderManager.Reference<*>) -> Unit {
  private val moduleRef = WeakReference(moduleManager)

  override fun invoke(reference: ModuleClassLoaderManager.Reference<*>) {
    moduleRef.get()?.release(reference)
  }
}