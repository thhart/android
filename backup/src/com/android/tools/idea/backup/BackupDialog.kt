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

package com.android.tools.idea.backup

import com.android.backup.BackupType
import com.android.backup.BackupType.DEVICE_TO_DEVICE
import com.android.tools.idea.flags.StudioFlags
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.UIBundle
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.SwingHelper
import java.awt.Dimension
import java.awt.event.ItemEvent
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.GroupLayout
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants.HORIZONTAL
import javax.swing.event.DocumentEvent
import javax.swing.event.HyperlinkEvent.EventType.ACTIVATED
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import org.jetbrains.annotations.VisibleForTesting

internal class BackupDialog(
  private val project: Project,
  initialApplicationId: String,
  private val isBackupEnabled: Boolean,
) : DialogWrapper(project) {
  private val applicationIds = buildList {
    if (StudioFlags.BACKUP_ALLOW_NON_PROJECT_APPS.get()) {
      add(initialApplicationId)
    }
    addAll(project.getService(ProjectAppsProvider::class.java).getApplicationIds())
  }

  private val applicationIdComboBox =
    ComboBox(DefaultComboBoxModel(applicationIds.sorted().toTypedArray())).apply {
      name = "applicationIdComboBox"
      maximumSize = Dimension(APPLICATION_ID_FIELD_WIDTH, maximumSize.height)
    }
  private val typeComboBox =
    ComboBox(DefaultComboBoxModel(BackupType.entries.toTypedArray())).apply {
      name = "typeComboBox"
      maximumSize = Dimension(TYPE_FIELD_WIDTH, preferredSize.height)
    }
  private val backupNotEnabledWarning =
    SwingHelper.createHtmlViewer(false, JLabel().font, null, null).apply {
      name = "backupNotEnabledWarning"
      isEditable = false
      isOpaque = false
      putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
      isVisible = !isBackupEnabled
      addHyperlinkListener { it ->
        if (it.eventType == ACTIVATED) {
          BackupManagerImpl.openBackupDisabledLearnMoreLink()
        }
      }
    }
  private val fileTextField =
    BackupFileTextField.createFileSaver(project) { fileSetByChooser = true }
      .apply {
        textComponent.document.addDocumentListener(
          object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
              updateOkAction()
            }
          }
        )
        name = "fileTextField"
        minimumSize = Dimension(PATH_FIELD_WIDTH, minimumSize.height)
      }
  private var fileSetByChooser = false
  private val properties
    get() = PropertiesComponent.getInstance(project)

  val applicationId: String
    get() = applicationIdComboBox.item

  val backupPath: Path
    get() {
      val path = Path.of(fileTextField.text).absoluteInProject(project)
      return path.withBackupExtension()
    }

  val type: BackupType
    get() = typeComboBox.item

  init {
    init()
    title = "Backup App Data"
    if (applicationIds.contains(initialApplicationId)) {
      applicationIdComboBox.item = initialApplicationId
    }
    typeComboBox.item = getLastUsedType()
    typeComboBox.renderer = ListCellRenderer { _, value, _, _, _ -> JLabel(value.displayName) }

    if (!isBackupEnabled) {
      fun doUpdate() {
        backupNotEnabledWarning.text =
          if (typeComboBox.item == DEVICE_TO_DEVICE) WARNING_DTD else WARNING_CLOUD
        updateOkAction()
      }

      val itemListener: (ItemEvent) -> Unit = {
        doUpdate()
        pack()
      }
      doUpdate()
      typeComboBox.addItemListener(itemListener)
    }
    pack()
    isResizable = false
  }

  override fun createCenterPanel(): JComponent {
    val panel = JPanel(null)
    val layout = GroupLayout(panel)
    layout.autoCreateContainerGaps = true
    layout.autoCreateGaps = true

    val applicationIdLabel = JLabel("Application ID:")
    val typeLabel = JLabel("Backup type:")
    val fileLabel = JLabel("Backup file:")

    fileTextField.text = getLastUsedFile()
    with(layout) {
      setHorizontalGroup(
        createParallelGroup(GroupLayout.Alignment.LEADING)
          .addGroup(
            createSequentialGroup()
              .addComponent(applicationIdLabel)
              .addComponent(applicationIdComboBox)
          )
          .addGroup(
            createSequentialGroup()
              .addComponent(typeLabel)
              .addComponent(typeComboBox)
              .addComponent(backupNotEnabledWarning)
          )
          .addGroup(createSequentialGroup().addComponent(fileLabel).addComponent(fileTextField))
      )
      setVerticalGroup(
        createSequentialGroup()
          .addGroup(
            createParallelGroup(GroupLayout.Alignment.CENTER)
              .addComponent(applicationIdLabel)
              .addComponent(applicationIdComboBox)
          )
          .addGroup(
            createParallelGroup(GroupLayout.Alignment.LEADING)
              .addGroup(
                createParallelGroup(GroupLayout.Alignment.CENTER)
                  .addComponent(typeLabel)
                  .addComponent(typeComboBox)
              )
              .addComponent(backupNotEnabledWarning)
          )
          .addGroup(
            createParallelGroup(GroupLayout.Alignment.CENTER)
              .addComponent(fileLabel)
              .addComponent(fileTextField)
          )
      )
      linkSize(HORIZONTAL, applicationIdLabel, typeLabel, fileLabel)
    }

    panel.layout = layout
    return panel
  }

  override fun doOKAction() {
    setLastUsedFile(backupPath.relativeToProject(project).pathString)
    setLastUsedType(typeComboBox.item)
    if (backupPath.exists() && !fileSetByChooser) {
      @Suppress("DialogTitleCapitalization")
      val result =
        Messages.showYesNoDialog(
          UIBundle.message("file.chooser.save.dialog.confirmation", backupPath.fileName),
          UIBundle.message("file.chooser.save.dialog.confirmation.title"),
          Messages.getWarningIcon(),
        )
      if (result != Messages.YES) {
        return
      }
    }
    fileTextField.text = Path.of(fileTextField.text).withBackupExtension().pathString
    fileTextField.addCurrentTextToHistory()
    super.doOKAction()
  }

  private fun getLastUsedFile(): String {
    return properties.getValue(LAST_USED_FILE_KEY) ?: DEFAULT_BACKUP_FILENAME
  }

  private fun setLastUsedFile(path: String) {
    properties.setValue(LAST_USED_FILE_KEY, path)
  }

  private fun getLastUsedType(): BackupType {
    return when (val name = properties.getValue(LAST_USED_TYPE_KEY)) {
      null -> DEVICE_TO_DEVICE
      else -> BackupType.valueOf(name)
    }
  }

  private fun setLastUsedType(type: BackupType) {
    properties.setValue(LAST_USED_TYPE_KEY, type.name)
  }

  companion object {
    private val APPLICATION_ID_FIELD_WIDTH
      get() = JBUIScale.scale(300)

    private val TYPE_FIELD_WIDTH
      get() = JBUIScale.scale(100)

    private val PATH_FIELD_WIDTH
      get() = JBUIScale.scale(500)

    private val DEFAULT_BACKUP_FILENAME = "application.${BackupFileType.defaultExtension}"

    private val WARNING_DTD =
      """
      App-data won't be backed up as allowBackup property is false.<br>
      Backup may contain Restore Keys, if present for the app.<br>
      (<a href='http://bar.com/'>Learn more</a>)
    """
        .trimIndent()

    private val WARNING_CLOUD =
      """
      App-data won't be backed up as allowBackup property is false.<br>
      Restore Keys backup is not supported via this tool for Cloud.<br>
      backup type. (<a href='http://bar.com/'>Learn more</a>)
    """
        .trimIndent()

    @VisibleForTesting internal const val LAST_USED_FILE_KEY = "Backup.Last.Used.File"

    @VisibleForTesting internal const val LAST_USED_TYPE_KEY = "Backup.Last.Used.Type"
  }

  fun updateOkAction() {
    try {
      val path = Path.of(fileTextField.text)
      isOKActionEnabled =
        path.isValid() &&
          !path.isDirectory() &&
          (typeComboBox.item == DEVICE_TO_DEVICE || isBackupEnabled)
    } catch (_: InvalidPathException) {
      isOKActionEnabled = false
    }
  }
}

private fun Path.withBackupExtension() =
  when (extension) {
    BackupFileType.defaultExtension -> this
    else -> Path.of("${pathString}.${BackupFileType.defaultExtension}")
  }
