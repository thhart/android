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
package com.android.tools.idea.uibuilder.visual

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.UiThread
import com.android.resources.ResourceFolderType
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.common.AdtPrimaryPanel
import com.android.tools.adtui.common.border
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.error.IssueListener
import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.common.layout.SceneViewAlignment
import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceIssueListenerImpl
import com.android.tools.idea.common.surface.LayoutScannerEnabled
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener
import com.android.tools.idea.res.getFolderType
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager
import com.android.tools.idea.uibuilder.layout.option.GridLayoutManager
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.android.tools.idea.uibuilder.surface.NlSupportedActions
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.android.tools.idea.uibuilder.visual.analytics.trackOpenConfigSet
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.intellij.CommonBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.Alarm
import com.intellij.util.ArrayUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.DefaultFocusTraversalPolicy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

/**
 * Form of layout visualization which offers multiple previews for different devices in the same
 * time. It provides a convenient way to user to preview the layout in different devices.
 */
class VisualizationForm(
  private val project: Project,
  parentDisposable: Disposable,
  private val initializer: ContentInitializer,
) : VisualizationContent, ConfigurationSetListener, ResourceChangeListener {
  private val scope = AndroidCoroutineScope(this)
  private val surface: NlDesignSurface
  private val myWorkBench: WorkBench<DesignSurface<*>>
  private val myRoot = JPanel(BorderLayout())
  private var myFile: VirtualFile? = null
  private val myResourceNotifyingFilesLock = ReentrantLock()

  @GuardedBy("myResourceNotifyingFilesLock")
  private val myResourceNotifyingFiles: MutableSet<VirtualFile> = HashSet()
  private var isActive = false
  private var myContentPanel: JComponent? = null
  private val myActionToolbarPanel: JComponent
  private val myCancelRenderingTaskLock = ReentrantLock()

  @GuardedBy("myCancelRenderingTaskLock") private var myCancelRenderingTask: Runnable? = null

  /**
   * Contains the editor that is currently being loaded. Once the file is loaded, myPendingEditor
   * will be null.
   */
  private var myPendingEditor: FileEditor? = null
  private var myEditor: FileEditor? = null
  private var myCurrentConfigurationSet: ConfigurationSet
  private var myCurrentModelsProvider: VisualizationModelsProvider
  private val myLayoutOption =
    SurfaceLayoutOption(
      "Layout",
      { GridLayoutManager() },
      false,
      SceneViewAlignment.LEFT,
      SurfaceLayoutOption.LayoutType.OrganizationGrid,
    )
  private val myUpdateQueue: MergingUpdateQueue

  private var myCancelPendingModelLoad = AtomicBoolean(false)
  private val myProgressIndicator = EmptyProgressIndicator()
  private val analyticsManager: NlAnalyticsManager
    get() = surface.analyticsManager as NlAnalyticsManager

  var editor: FileEditor?
    get() = myEditor
    private set(editor) {
      if (editor !== myEditor) {
        myEditor = editor
        surface.fileEditorDelegate = editor
        updateActionToolbar(myActionToolbarPanel)
      }
    }

  val component: JComponent = myRoot

  private val visualLintHandler: VisualizationFormVisualLintHandler
  private val issueListener: IssueListener

  init {
    Disposer.register(parentDisposable, this)
    myCurrentConfigurationSet =
      VisualizationToolSettings.getInstance().globalState.lastSelectedConfigurationSet
    myCurrentModelsProvider = myCurrentConfigurationSet.createModelsProvider(this)
    val config = LayoutScannerEnabled()
    // Custom issue panel integration used.
    config.isIntegrateWithDefaultIssuePanel = false
    surface =
      NlSurfaceBuilder.builder(project, this@VisualizationForm) { surface, model ->
          LayoutlibSceneManager(model, surface, layoutScannerConfig = config).apply {
            listenResourceChange = false
            updateAndRenderWhenActivated = false
            sceneRenderConfiguration.let {
              it.showDecorations =
                VisualizationToolSettings.getInstance().globalState.showDecoration
              it.useImagePool = false
              // 0.5f makes it spend 50% memory.
              it.quality = 0.5f
              it.logRenderErrors = false
            }
          }
        }
        .waitForRenderBeforeRestoringZoom(true)
        .setActionManagerProvider { surface: DesignSurface<*> ->
          VisualizationActionManager((surface as NlDesignSurface?)!!) { myCurrentModelsProvider }
        }
        .setInteractionHandlerProvider { surface: DesignSurface<*> ->
          VisualizationInteractionHandler(surface) { myCurrentModelsProvider }
        }
        .setLayoutOption(myLayoutOption)
        .setSupportedActions(VISUALIZATION_SUPPORTED_ACTIONS)
        .setDelegateDataProvider {
          when {
            VIRTUAL_FILE.`is`(it) -> myFile
            VISUALIZATION_FORM.`is`(it) -> this
            else -> null
          }
        }
        .build()
    surface.setSceneViewAlignment(SceneViewAlignment.LEFT)
    issueListener = DesignSurfaceIssueListenerImpl(surface).apply { surface.addIssueListener(this) }
    surface.setScreenViewProvider(NlScreenViewProvider.VISUALIZATION, false)
    surface.name = VISUALIZATION_DESIGN_SURFACE_NAME
    surface.zoomController.storeId = VISUALIZATION_DESIGN_SURFACE_NAME
    myWorkBench = WorkBench(project, "Visualization", null, this)
    myWorkBench.setLoadingText(CommonBundle.getLoadingTreeNodeText())
    myWorkBench.setToolContext(surface)
    myActionToolbarPanel = createToolbarPanel()
    myRoot.add(myActionToolbarPanel, BorderLayout.NORTH)
    myRoot.add(myWorkBench, BorderLayout.CENTER)
    myRoot.isFocusCycleRoot = true
    myRoot.focusTraversalPolicy = VisualizationTraversalPolicy(surface)
    myUpdateQueue =
      MergingUpdateQueue(
        "visualization.form.update",
        NlModel.DELAY_AFTER_TYPING_MS,
        true,
        null,
        this,
        null,
        Alarm.ThreadToUse.POOLED_THREAD,
      )
    myUpdateQueue.setRestartTimerOnAdd(true)

    scope.launch {
      surface.zoomChanged.collect {
        VisualizationToolProjectSettings.getInstance(project).projectState.scale =
          surface.zoomController.scale
      }
    }

    visualLintHandler = VisualizationFormVisualLintHandler(this, project, surface.issueModel)
  }

  private fun createToolbarPanel(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())
    panel.border = BorderFactory.createMatteBorder(0, 0, 1, 0, border)
    updateActionToolbar(panel)
    return panel
  }

  private fun updateActionToolbar(toolbarPanel: JComponent) {
    toolbarPanel.removeAll()
    val group = DefaultActionGroup()
    val virtualFile = myFile
    val fileName = virtualFile?.name ?: ""
    // Add an empty action and disable it permanently for displaying file name.
    group.add(TextLabelAction(fileName))
    group.addSeparator()
    group.add(ConfigurationSetMenuAction(myCurrentConfigurationSet))
    group.addAll(myCurrentModelsProvider.createActions())
    val viewOptions = DropDownAction(null, "View Options", StudioIcons.Common.VISIBILITY_INLINE)
    viewOptions.add(ToggleShowDecorationAction())
    viewOptions.isPopup = true
    group.add(viewOptions)
    group.add(AddCustomConfigurationSetAction())
    group.add(RemoveCustomConfigurationSetAction(myCurrentConfigurationSet))
    // Use ActionPlaces.EDITOR_TOOLBAR as place to update the ui when appearance is changed.
    // In IJ's implementation, only the actions in ActionPlaces.EDITOR_TOOLBAR toolbar will be
    // tweaked when ui is changed.
    // See com.intellij.openapi.actionSystem.impl.ActionToolbarImpl.tweakActionComponentUI()
    val actionToolbar =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, group, true)
    actionToolbar.setTargetComponent(surface)
    ActionToolbarUtil.makeToolbarNavigable(actionToolbar)
    val toolbarComponent = actionToolbar.component
    toolbarComponent.border = BorderFactory.createEmptyBorder(0, 6, 0, 0)
    toolbarPanel.add(toolbarComponent, BorderLayout.CENTER)
    val lintGroup = DefaultActionGroup()
    lintGroup.add(IssuePanelToggleAction())
    val lintToolbar =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, lintGroup, true)
    lintToolbar.setTargetComponent(surface)
    ActionToolbarUtil.makeToolbarNavigable(lintToolbar)
    toolbarPanel.add(lintToolbar.component, BorderLayout.EAST)
  }

  override fun dispose() {
    deactivate()
    val registeredFiles: Set<VirtualFile>
    myResourceNotifyingFilesLock.lock()
    try {
      registeredFiles = HashSet(myResourceNotifyingFiles)
      myResourceNotifyingFiles.clear()
    } finally {
      myResourceNotifyingFilesLock.unlock()
    }
    for (file in registeredFiles) {
      scope.launch(Dispatchers.Default) { unregisterResourceNotification(file) }
    }
    removeModels(surface.models)
    surface.removeIssueListener(issueListener)
  }

  private fun removeModels(models: List<NlModel>) {
    visualLintHandler.clearIssueProvider()
    surface.removeModels(models)
  }

  /**
   * Specifies the next editor the preview should be shown for. The update of the preview may be
   * delayed.
   *
   * @return true on success. False if the preview update is not possible (e.g. the file for the
   *   editor cannot be found).
   */
  override fun setNextEditor(editor: FileEditor): Boolean {
    if (getFolderType(editor.file) != ResourceFolderType.LAYOUT) {
      return false
    }
    myPendingEditor = editor
    myFile = editor.file
    myCancelPendingModelLoad.set(true)
    if (isActive) {
      if (myContentPanel == null) {
        initializer.initContent(project, this) { initModel() }
      } else {
        initModel()
      }
    }
    return true
  }

  /** Called by [VisualizationForm.ContentInitializer] to initialize the content panel. */
  fun createContentPanel() {
    if (Disposer.isDisposed(this)) {
      return
    }
    if (myContentPanel == null) {
      val panel = JPanel(BorderLayout())
      panel.add(surface, BorderLayout.CENTER)
      myContentPanel = panel
      myWorkBench.init(myContentPanel!!, surface, ImmutableList.of(), false)
      // The toolbar is in the root panel which contains myWorkBench. To traverse to toolbar we need
      // to traverse out from myWorkBench.
      myWorkBench.isFocusCycleRoot = false
    }
  }

  /** Called by [VisualizationForm.ContentInitializer] when content is still loading. */
  fun showLoadingMessage() {
    myWorkBench.setLoadingText("Waiting for build to finish...")
  }

  /**
   * Called by [VisualizationForm.ContentInitializer] when build was either cancelled or there was
   * an error.
   */
  fun showErrorMessage() {
    myWorkBench.loadingStopped("Previews are unavailable until after a successful project sync")
  }

  private fun initModel() {
    DumbService.getInstance(project).smartInvokeLater { initNeleModelWhenSmart() }
  }

  @UiThread
  private fun initNeleModelWhenSmart() {
    setNoActiveModel()
    interruptRendering()
    if (myFile == null) {
      return
    }
    val targetFile = myFile!!
    val file = PsiManager.getInstance(project).findFile(targetFile)
    updateActionToolbar(myActionToolbarPanel)

    // isRequestCancelled allows us to cancel the ongoing computation if it is not needed anymore.
    val isRequestCancelled = AtomicBoolean(false)
    myCancelPendingModelLoad = isRequestCancelled
    // Asynchronously load the model and refresh the preview once it's ready
    scope.launch {
      val facet = if (file != null) AndroidFacet.getInstance(file) else null
      val models =
        facet?.let {
          myCurrentModelsProvider.createNlModels(
            this@VisualizationForm,
            file!!,
            AndroidBuildTargetReference.from(it, targetFile),
          )
        } ?: emptyList()
      if (models.isEmpty()) myWorkBench.showLoading("No Device Found")
      if (models.isEmpty() || isRequestCancelled.get()) {
        withContext(Dispatchers.Default) { unregisterResourceNotification(myFile) }
      } else {
        myWorkBench.showContent()
        interruptRendering()
        ApplicationManager.getApplication().invokeLater {
          surface.registerIndicator(myProgressIndicator)
        }
        // In visualization tool, we add model and layout the scroll pane before rendering
        CompletableFuture.allOf(
            *models.map { model -> surface.addModelWithoutRender(model) }.toTypedArray()
          )
          .await()
      }

      renderCurrentModels()

      // Re-layout and set scale after rendering.
      // This may be processed delayed but we have
      // known the preview number and rendering provides the right sizes
      // and the models are added, so it would layout correctly.
      withContext(Dispatchers.EDT) { relayoutAndNotifyZoomToFit() }

      ApplicationManager.getApplication().invokeLater {
        surface.unregisterIndicator(myProgressIndicator)
      }
      if (!isRequestCancelled.get() && facet?.isDisposed == false) {
        withContext(Dispatchers.EDT) { activateEditor(models.isNotEmpty()) }
      } else {
        removeModels(models)
      }
    }
  }

  private fun relayoutAndNotifyZoomToFit() {
    surface.invalidate()
    if (surface.zoomController.canZoomToFit()) {
      // On every mode change we want to set zoom-to-fit, apply zoom-to-fit if it is not set
      // already.
      surface.notifyZoomToFit()
    }
  }

  // A file editor was closed. If our editor no longer exists, cleanup our state.
  override fun fileClosed(editorManager: FileEditorManager, file: VirtualFile) {
    if (myEditor == null) {
      setNoActiveModel()
    } else if (file == myFile) {
      if (ArrayUtil.find(editorManager.getAllEditors(file), myEditor) < 0) {
        setNoActiveModel()
      }
    }
    if (myPendingEditor != null && file == myPendingEditor!!.file) {
      if (ArrayUtil.find(editorManager.getAllEditors(file), myPendingEditor) < 0) {
        myPendingEditor = null
      }
    }
  }

  override fun getConfigurationSet(): ConfigurationSet = myCurrentConfigurationSet

  override fun setConfigurationSet(configurationSet: ConfigurationSet) {
    // TODO: We should avoid calling the callback function actively. ConfigurationSetListener needs
    // to be refactored.
    onSelectedConfigurationSetChanged(configurationSet)
  }

  private fun setNoActiveModel() {
    myCancelPendingModelLoad.set(true)
    editor = null
    myWorkBench.setFileEditor(null)
    removeModels(surface.models)
  }

  private suspend fun activateEditor(hasModel: Boolean) {
    myCancelPendingModelLoad.set(true)
    if (!hasModel) {
      editor = null
      myWorkBench.setFileEditor(null)
    } else {
      editor = myPendingEditor
      myPendingEditor = null
      withContext(Dispatchers.Default) { registerResourceNotification(myFile) }
      myWorkBench.setFileEditor(myEditor)
    }
  }

  private fun registerResourceNotification(file: VirtualFile?) {
    if (file == null) {
      return
    }
    val facet = AndroidFacet.getInstance(file, project)
    if (facet != null) {
      myResourceNotifyingFilesLock.lock()
      try {
        if (!myResourceNotifyingFiles.add(file)) {
          // File is registered already.
          return
        }
      } finally {
        myResourceNotifyingFilesLock.unlock()
      }
      val manager = ResourceNotificationManager.getInstance(project)
      manager.addListener(this, facet, file, null)
    }
  }

  private fun unregisterResourceNotification(file: VirtualFile?) {
    if (file == null) {
      return
    }
    val facet = AndroidFacet.getInstance(file, project)
    if (facet != null) {
      myResourceNotifyingFilesLock.lock()
      try {
        if (!myResourceNotifyingFiles.remove(file)) {
          // File is not registered.
          return
        }
      } finally {
        myResourceNotifyingFilesLock.unlock()
      }
      val manager = ResourceNotificationManager.getInstance(project)
      manager.removeListener(this, facet, myFile, null)
    }
  }

  override fun resourcesChanged(reasons: ImmutableSet<ResourceNotificationManager.Reason>) {
    var needsRenderModels = false
    for (reason in reasons) {
      when (reason) {
        ResourceNotificationManager.Reason.RESOURCE_EDIT,
        ResourceNotificationManager.Reason.EDIT,
        ResourceNotificationManager.Reason.IMAGE_RESOURCE_CHANGED,
        ResourceNotificationManager.Reason.GRADLE_SYNC,
        ResourceNotificationManager.Reason.PROJECT_BUILD,
        ResourceNotificationManager.Reason.VARIANT_CHANGED,
        ResourceNotificationManager.Reason.SDK_CHANGED,
        ResourceNotificationManager.Reason.CONFIGURATION_CHANGED -> {
          needsRenderModels = true
          break
        }
      }
    }
    if (needsRenderModels) {
      myUpdateQueue.queue(
        object : Update("update") {
          override fun run() {
            // Show and hide progress indicator during rendering.
            ApplicationManager.getApplication().invokeLater {
              surface.registerIndicator(myProgressIndicator)
            }
            scope.launch {
              renderCurrentModels()
              ApplicationManager.getApplication().invokeLater {
                surface.unregisterIndicator(myProgressIndicator)
              }
            }
          }

          override fun canEat(update: Update): Boolean {
            return true
          }
        }
      )
    }
  }

  private suspend fun renderCurrentModels() {
    interruptRendering()
    val isRenderingCanceled = AtomicBoolean(false)
    val cancelTask = Runnable { isRenderingCanceled.set(true) }
    myCancelRenderingTaskLock.lock()
    myCancelRenderingTask =
      try {
        cancelTask
      } finally {
        myCancelRenderingTaskLock.unlock()
      }
    visualLintHandler.clearIssueProviderAndBaseConfigurationIssue()

    // This render the added components.
    for (manager in surface.sceneManagers) {
      if (!isRenderingCanceled.get()) {
        manager.sceneRenderConfiguration.needsInflation.set(true)
        manager.requestRenderAndWait()
        scope.launch {
          visualLintHandler.afterRenderCompleted(manager) { !isActive || isRenderingCanceled.get() }
        }
      }
    }
    surface.issueModel.updateErrorsList()
  }

  private fun interruptRendering() {
    myCancelRenderingTaskLock.lock()
    val task: Runnable? =
      try {
        myCancelRenderingTask
      } finally {
        myCancelRenderingTaskLock.unlock()
      }
    task?.run()
  }

  /** Re-enables updates for this preview form. See [.deactivate] */
  override fun activate() {
    if (isActive) {
      return
    }
    scope.launch { onActivate() }
  }

  private suspend fun onActivate() {
    withContext(Dispatchers.Default) {
      registerResourceNotification(myFile)
      isActive = true
    }
    withContext(Dispatchers.EDT) {
      if (myContentPanel == null) {
        initializer.initContent(project, this@VisualizationForm) { initModel() }
      } else {
        initModel()
      }
      surface.activate()
      analyticsManager.trackVisualizationToolWindow(true)
      visualLintHandler.onActivate()
      IssuePanelService.getDesignerCommonIssuePanel(project)
        ?.addIssueSelectionListener(surface.issueListener, surface)
    }
  }

  /**
   * Disables the updates for this preview form. Any changes to resources or the layout won't update
   * this preview until [.activate] is called.
   */
  override fun deactivate() {
    interruptRendering()
    if (!isActive) {
      return
    }
    myCancelPendingModelLoad.set(true)
    surface.deactivate()
    isActive = false
    scope.launch(Dispatchers.Default) { unregisterResourceNotification(myFile) }
    if (myContentPanel != null) {
      setNoActiveModel()
    }
    analyticsManager.trackVisualizationToolWindow(false)
    visualLintHandler.onDeactivate()
    IssuePanelService.getDesignerCommonIssuePanel(project)
      ?.removeIssueSelectionListener(surface.issueListener)
    myFile?.let { FileEditorManager.getInstance(project).getSelectedEditor(it)?.selectNotify() }
  }

  override fun onSelectedConfigurationSetChanged(newConfigurationSet: ConfigurationSet) {
    if (myCurrentConfigurationSet !== newConfigurationSet) {
      myCurrentConfigurationSet = newConfigurationSet
      trackOpenConfigSet(surface, myCurrentConfigurationSet)
      VisualizationToolSettings.getInstance().globalState.lastSelectedConfigurationSet =
        newConfigurationSet
      myCurrentModelsProvider = newConfigurationSet.createModelsProvider(this)
      surface.layoutManagerSwitcher?.currentLayoutOption?.value = myLayoutOption
      surface.resetZoomToFitNotifier(false)
      refresh()
    }
  }

  override fun onCurrentConfigurationSetUpdated() {
    refresh()
  }

  /** Refresh the previews. This recreates the [NlModel]s from the current [ConfigurationSet]. */
  private fun refresh() {
    updateActionToolbar(myActionToolbarPanel)
    // Dispose old models and create new models with new configuration set.
    initModel()
  }

  /** A disabled action for displaying text in action toolbar. It does nothing. */
  private class TextLabelAction(private val text: String) : AnAction(null as String?) {

    override fun actionPerformed(e: AnActionEvent) = Unit

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.setText(text, false)
      e.presentation.isEnabled = false
      e.presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
    }
  }

  private inner class ToggleShowDecorationAction : ToggleAction("Show System UI") {
    override fun isSelected(e: AnActionEvent): Boolean =
      VisualizationToolSettings.getInstance().globalState.showDecoration

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      VisualizationToolSettings.getInstance().globalState.showDecoration = state
      val surface = e.getData(DESIGN_SURFACE) as? NlDesignSurface ?: return
      val visualizationForm = e.getData(VISUALIZATION_FORM) ?: return
      surface.models
        .mapNotNull { model: NlModel -> surface.getSceneManager(model) }
        .forEach { manager -> manager.sceneRenderConfiguration.showDecorations = state }
      scope.launch {
        surface.sceneManagers.forEach { it.requestRenderAndWait() }
        if (!Disposer.isDisposed(visualizationForm.myWorkBench)) {
          visualizationForm.myWorkBench.showContent()
        }
      }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private class VisualizationTraversalPolicy(private val mySurface: DesignSurface<*>) :
    DefaultFocusTraversalPolicy() {
    override fun getDefaultComponent(aContainer: Container): Component {
      return mySurface.layeredPane
    }
  }

  private class EmptyProgressIndicator : AbstractProgressIndicatorBase() {
    override fun isRunning(): Boolean = true
  }

  interface ContentInitializer {
    fun initContent(project: Project, form: VisualizationForm, onComplete: () -> Unit)
  }

  @TestOnly
  fun getDesignSurfaceForTestOnly(): NlDesignSurface {
    return surface
  }

  @TestOnly
  fun refreshForTestOnly() {
    relayoutAndNotifyZoomToFit()
  }

  companion object {
    @VisibleForTesting const val VISUALIZATION_DESIGN_SURFACE_NAME = "Layout Validation"
    private val VISUALIZATION_SUPPORTED_ACTIONS: Set<NlSupportedActions> =
      ImmutableSet.of(NlSupportedActions.TOGGLE_ISSUE_PANEL)

    @JvmField
    val VISUALIZATION_FORM = DataKey.create<VisualizationForm>(VisualizationForm::class.java.name)
  }
}
