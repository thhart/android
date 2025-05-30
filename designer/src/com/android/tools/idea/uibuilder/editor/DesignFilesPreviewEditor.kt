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
package com.android.tools.idea.uibuilder.editor

import android.graphics.drawable.AnimationDrawable
import android.widget.ImageView
import com.android.SdkConstants
import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.configurations.ConfigurationListener
import com.android.tools.idea.actions.ANIMATION_TOOLBAR
import com.android.tools.idea.common.editor.DEFAULT_MODEL_PROVIDER
import com.android.tools.idea.common.editor.DesignToolsSplitEditor
import com.android.tools.idea.common.editor.DesignerEditor
import com.android.tools.idea.common.editor.DesignerEditorPanel
import com.android.tools.idea.common.editor.ModelProvider
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceSettings
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.android.tools.idea.uibuilder.actions.DrawableScreenViewProvider
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.android.tools.idea.uibuilder.type.AdaptiveIconFileType
import com.android.tools.idea.uibuilder.type.AnimatedImageFileType
import com.android.tools.idea.uibuilder.type.AnimatedStateListFileType
import com.android.tools.idea.uibuilder.type.AnimatedStateListTempFileType
import com.android.tools.idea.uibuilder.type.AnimatedVectorFileType
import com.android.tools.idea.uibuilder.type.AnimationListFileType
import com.android.tools.idea.uibuilder.type.DrawableFileType
import com.android.tools.idea.uibuilder.type.getPreviewConfig
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Consumer
import javax.swing.JPanel
import org.jetbrains.android.uipreview.AndroidEditorSettings

private const val WORKBENCH_NAME = "DESIGN_FILES_PREVIEW_EDITOR"

const val DESIGN_FILES_PREVIEW_EDITOR_ID = "android-preview-designer"

/**
 * [DesignerEditor] containing a [NlDesignSurface] without a border layer and a [WorkBench] without
 * any tool windows. It should be used as the preview portion of [DesignToolsSplitEditor] and to
 * open non-editable [DesignerEditorFileType] files, such as fonts and drawables.
 */
class DesignFilesPreviewEditor(
  file: VirtualFile,
  project: Project,
  fileType: DesignerEditorFileType,
) : DesignerEditor(file, project, fileType) {

  // Used when previewing animated selector file, to provide the required data for
  // AnimatedSelectorToolbar.
  private var animatedSelectorModel: AnimatedSelectorModel? = null

  override fun getEditorId() = DESIGN_FILES_PREVIEW_EDITOR_ID

  @UiThread
  override fun createEditorPanel(): DesignerEditorPanel {
    val workBench = WorkBench<DesignSurface<*>>(myProject, WORKBENCH_NAME, this, this)
    val surface: (panel: DesignerEditorPanel) -> DesignSurface<*> = {
      NlSurfaceBuilder.builder(myProject, this)
        .setActionManagerProvider { surface ->
          PreviewEditorActionManagerProvider(surface as NlDesignSurface, fileType)
        }
        .build()
        .apply {
          val screenViewProvider =
            when (fileType) {
              is AdaptiveIconFileType,
              is DrawableFileType -> {
                val lastBackgroundType =
                  DesignSurfaceSettings.getInstance(project)
                    .surfaceState
                    .loadDrawableBackgroundType(project, file!!)
                DrawableScreenViewProvider(lastBackgroundType)
              }
              else -> NlScreenViewProvider.RENDER
            }
          setScreenViewProvider(screenViewProvider, false)
          // Make DesignSurface be focused when mouse clicked. This make the DataContext is provided
          // from it while user clicks it.
          interactionPane.addMouseListener(
            object : MouseAdapter() {
              override fun mousePressed(e: MouseEvent) {
                interactionPane.requestFocus()
              }
            }
          )
        }
    }

    val modelProvider =
      if (fileType is AnimatedStateListFileType) MyAnimatedSelectorModelProvider()
      else DEFAULT_MODEL_PROVIDER

    return DesignerEditorPanel(
      this,
      myProject,
      myFile,
      workBench,
      surface,
      NlComponentRegistrar,
      modelProvider,
      { emptyList() },
      { panel, model -> addAnimationToolbar(panel, model) },
      AndroidEditorSettings.getInstance().globalState.preferredDrawableSurfaceState(),
    )
  }

  private inner class MyAnimatedSelectorModelProvider : ModelProvider {
    override fun createModel(
      parentDisposable: Disposable,
      project: Project,
      buildTarget: AndroidBuildTargetReference,
      componentRegistrar: Consumer<NlComponent>,
      file: VirtualFile,
    ): NlModel {
      val config =
        ConfigurationManager.getOrCreateInstance(buildTarget.module).getPreviewConfig(file)
      animatedSelectorModel =
        WriteCommandAction.runWriteCommandAction(
          project,
          Computable {
            AnimatedSelectorModel(
              file,
              parentDisposable,
              project,
              buildTarget,
              componentRegistrar,
              config,
            )
          },
        )
      return animatedSelectorModel!!.getNlModel()
    }
  }

  private fun addAnimationToolbar(panel: DesignerEditorPanel, model: NlModel?): JPanel? {
    val surface = panel.surface
    val toolbar =
      if (model?.type is AnimatedStateListTempFileType) {
        AnimatedSelectorToolbar.createToolbar(
          this,
          animatedSelectorModel!!,
          AnimatedSelectorListener(surface),
          16,
          0L,
        )
      } else if (model?.type is AnimatedVectorFileType || model?.type is AnimatedImageFileType) {
        // If opening an animated vector or image, add an unlimited animation bar
        AnimationToolbar.createUnlimitedAnimationToolbar(
          this,
          AnimatedDrawableListener(surface),
          16,
          0L,
        )
      } else if (model?.type is AnimationListFileType) {
        // If opening an animation list, add an animation bar with progress
        val animationDrawable =
          (surface.getSceneManager(model) as? LayoutlibSceneManager)?.let {
            findAnimationDrawable(it)
          }
        val maxTimeMs: Long =
          animationDrawable?.let { drawable ->
            (0 until drawable.numberOfFrames).sumOf { index ->
              drawable.getDuration(index).toLong()
            }
          } ?: 0L
        val oneShotString = animationDrawable?.isOneShot ?: false
        AnimationToolbar.createAnimationToolbar(
            this,
            AnimationListListener(surface),
            16,
            0,
            maxTimeMs,
          )
          .apply { setLooping(!oneShotString) }
      } else {
        null
      }
    // Clear the existing provider first, which happens when another toolbar is created.
    DataManager.removeDataProvider(panel)
    DataManager.registerDataProvider(panel) { if (ANIMATION_TOOLBAR.`is`(it)) toolbar else null }
    if (toolbar != null) {
      myProject.messageBus
        .connect(toolbar)
        .subscribe(
          FileEditorManagerListener.FILE_EDITOR_MANAGER,
          object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
              if (
                (event.oldEditor as? DesignToolsSplitEditor)?.designerEditor ==
                  this@DesignFilesPreviewEditor
              ) {
                // pause the animation when this editor loses the focus.
                if (toolbar.getPlayStatus() == PlayStatus.PLAY) {
                  toolbar.pause()
                }
              } else if (
                (event.newEditor as? DesignToolsSplitEditor)?.designerEditor ==
                  this@DesignFilesPreviewEditor
              ) {
                // Needs to reinflate when grabbing the focus back. This makes sure the elapsed
                // frame time is correct when animation resuming.
                toolbar.forceElapsedReset = true
              }
            }
          },
        )
      val configurationChangeListener = ConfigurationListener { flags ->
        if ((flags and ConfigurationListener.CFG_THEME) > 0) {
          // Reset the toolbar on configuration change
          toolbar.setFrameMs(0)
        }
        return@ConfigurationListener true
      }
      surface.configurations.firstOrNull()?.let {
        it.addListener(configurationChangeListener)
        // Remove the listener when disposing the panel, so the configuration won't leak
        Disposer.register(panel) { it.removeListener(configurationChangeListener) }
      }
    }
    return toolbar
  }

  override fun getName() = "Design"
}

/**
 * The [com.android.tools.idea.common.editor.ActionManager] for [DesignFilesPreviewEditor]. This
 * gives the chance to have different actions depends on the give [fileType].
 */
class PreviewEditorActionManagerProvider(
  surface: NlDesignSurface,
  private val fileType: DesignerEditorFileType?,
) : NlActionManager(surface) {
  override fun getSceneViewContextToolbarActions(): List<AnAction> {
    return when (fileType) {
      is AnimatedImageFileType,
      is AnimatedStateListFileType,
      is AnimatedStateListTempFileType,
      is AnimatedVectorFileType,
      is AnimationListFileType -> emptyList()
      else -> super.getSceneViewContextToolbarActions()
    }
  }
}

/** Animation listener for <animated-vector> or <animated-image>. */
private class AnimatedDrawableListener(val surface: DesignSurface<*>) : AnimationListener {
  override fun animateTo(controller: AnimationController, framePositionMs: Long) {
    (surface.model?.let { surface.getSceneManager(it) } as? LayoutlibSceneManager)?.let {
      // Some frames may be dropped as consequence of the queueing/scheduling logic in the render
      // pipeline, but this is acceptable when playing the animation.
      it.sceneRenderConfiguration.elapsedFrameTimeMs = framePositionMs.coerceAtLeast(0L)
      if (framePositionMs <= 0L || controller.forceElapsedReset) {
        it.sceneRenderConfiguration.needsInflation.set(true)
        if (framePositionMs > 0L) controller.forceElapsedReset = false
      }
      it.requestRender()
    }
  }
}

/** Animation listener for <animation-list>. */
private class AnimationListListener(val surface: DesignSurface<*>) : AnimationListener {
  private var currentAnimationDrawable: AnimationDrawable? = null
  private var modelTimeMap = listOf<Long>()

  override fun animateTo(controller: AnimationController, framePositionMs: Long) {
    (surface.model?.let { surface.getSceneManager(it) } as? LayoutlibSceneManager)?.let {
      sceneManager ->
      val imageView =
        sceneManager.renderResult?.rootViews?.firstOrNull()?.viewObject as ImageView? ?: return
      val animationDrawable = imageView.drawable as? AnimationDrawable ?: return
      if (currentAnimationDrawable != animationDrawable) {
        updateAnimationDrawableInformation(controller, sceneManager)
        currentAnimationDrawable = animationDrawable
      }

      val targetImageIndex = findTargetDuration(animationDrawable, framePositionMs)
      animationDrawable.currentIndex = targetImageIndex
      sceneManager.requestRender()
    }
  }

  /**
   * Update the maximum time and repeating to toolbar. Pre-process a time map to find the target
   * Drawable Frame when playing animation.
   */
  private fun updateAnimationDrawableInformation(
    controller: AnimationController,
    manager: LayoutlibSceneManager,
  ) {
    val animationDrawable = findAnimationDrawable(manager)
    modelTimeMap =
      if (animationDrawable != null) {
        val timeMap = mutableListOf<Long>()
        var durationSum = 0L
        repeat(animationDrawable.numberOfFrames) { index ->
          durationSum += animationDrawable.getDuration(index)
          timeMap.add(durationSum)
        }
        controller.setLooping(!animationDrawable.isOneShot)
        controller.setMaxTimeMs(durationSum)
        timeMap
      } else {
        emptyList()
      }
  }

  private fun findTargetDuration(animationDrawable: AnimationDrawable, framePositionMs: Long): Int {
    return binarySearch(modelTimeMap, framePositionMs, 0, animationDrawable.numberOfFrames - 1)
  }

  /**
   * Binary search to find an index which [modelTimeMap].get(index - 1) <= [target] <
   * [modelTimeMap].get(index)`. If [target] is larger than [modelTimeMap].get([modelTimeMap].size -
   * 1), then we return [modelTimeMap].size - 1, because AnimationDrawable stays at last image when
   * animation ends.
   */
  private fun binarySearch(map: List<Long>, target: Long, start: Int, end: Int): Int {
    if (end <= start) {
      return end
    }
    val mid = (start + end) / 2
    return when {
      map[mid] < target -> binarySearch(map, target, mid + 1, end)
      target < map[mid] -> binarySearch(map, target, start, mid)
      else -> mid + 1 // map[mid] == target
    }
  }
}

/**
 * Animation listener for <animated-selector> file. <animated-selector> may have embedded
 * <animated-vector> and/or <animation-list>.
 */
private class AnimatedSelectorListener(val surface: DesignSurface<*>) : AnimationListener {
  private val animatedVectorDelegate = AnimatedDrawableListener(surface)
  private val animationListDelegate = AnimationListListener(surface)

  override fun animateTo(controller: AnimationController, framePositionMs: Long) {
    (surface.model?.let { surface.getSceneManager(it) } as? LayoutlibSceneManager)?.let {
      when (it.model.file.rootTag?.name) {
        SdkConstants.TAG_ANIMATED_VECTOR -> {
          animatedVectorDelegate.animateTo(controller, framePositionMs)
        }
        SdkConstants.TAG_ANIMATION_LIST ->
          animationListDelegate.animateTo(controller, framePositionMs)
        else -> {
          it.sceneRenderConfiguration.elapsedFrameTimeMs = framePositionMs
          it.requestRender()
        }
      }
    }
  }
}

private fun findAnimationDrawable(sceneManager: LayoutlibSceneManager): AnimationDrawable? {
  return (sceneManager.renderResult?.rootViews?.firstOrNull()?.viewObject as ImageView?)?.drawable
    as? AnimationDrawable
}

fun AndroidEditorSettings.GlobalState.preferredDrawableSurfaceState() =
  when (preferredResourcesEditorMode) {
    AndroidEditorSettings.EditorMode.CODE -> DesignerEditorPanel.State.DEACTIVATED
    AndroidEditorSettings.EditorMode.SPLIT -> DesignerEditorPanel.State.SPLIT
    AndroidEditorSettings.EditorMode.DESIGN -> DesignerEditorPanel.State.FULL
    null -> throw IllegalStateException("preferredResourcesEditorMode should not be null")
  }
