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
package com.android.tools.idea.gservices

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val STUDIO_FLAG_NAME = "studio"
private const val NOTIFICATION_GROUP_NAME = "StudioDeprecationNotification"
private const val DEPRECATION_DATE_PROPERTIES_KEY =
  "com.android.tools.idea.gservices.deprecation.last.date.checked"
private const val DEPRECATION_DATE_PATTERN = "yyyy-MM-dd"
private const val SHOW_NOTIFICATION_THRESHOLD = 30

class StudioDeprecationChecker : ProjectActivity {

  private val notificationGroup: NotificationGroup
    get() =
      NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_NAME)
        ?: throw RuntimeException("NotificationGroup not found")

  override suspend fun execute(project: Project) {
    val deprecationData =
      service<DevServicesDeprecationDataProvider>().getCurrentDeprecationData(STUDIO_FLAG_NAME)
    if (deprecationData.isSupported()) return

    if (deprecationData.isDeprecated()) {
      if (deprecationData.date == null) {
        thisLogger().warn("Deprecation date not provided")
        return
      }
      if (checkDateDiff(deprecationData.date)) {
        thisLogger()
          .info(
            "Skip showing deprecation notification because diff is more than $SHOW_NOTIFICATION_THRESHOLD days"
          )
        return
      }
      if (hasShownForDate(deprecationData.date)) {
        thisLogger()
          .info("Skip showing deprecation notification because notification already shown")
        return
      }
    }

    val notification =
      notificationGroup.createNotification(
        deprecationData.header,
        deprecationData.description,
        deprecationData.getNotificationType(),
      )

    if (deprecationData.showUpdateAction) {
      // Shows the update action as a button.
      notification.isSuggestionType = true

      notification.addAction(
        NotificationAction.createSimpleExpiring("Update Android Studio") {
          UpdateChecker.updateAndShowResult(project)
        }
      )
    }

    if (deprecationData.moreInfoUrl.isNotEmpty()) {
      notification.addAction(
        NotificationAction.createSimple("More info") {
          BrowserUtil.browse(deprecationData.moreInfoUrl)
        }
      )
    }

    notification
      .setImportant(true)
      .setIcon(deprecationData.getNotificationIcon())
      .whenExpired { maybeStoreDeprecationDate(deprecationData) }
      .notify(project)

    invokeLater {
      notification.balloon?.addListener(
        object : JBPopupListener {
          override fun onClosed(event: LightweightWindowEvent) {
            super.onClosed(event)
            notification.expire()
          }
        }
      )
    }
  }

  private fun checkDateDiff(deprecationDate: LocalDate) =
    ChronoUnit.DAYS.between(LocalDate.now(), deprecationDate) > SHOW_NOTIFICATION_THRESHOLD

  private fun hasShownForDate(deprecationDate: LocalDate): Boolean {
    val lastCheckedDateProp =
      PropertiesComponent.getInstance().getValue(DEPRECATION_DATE_PROPERTIES_KEY, "")
    if (lastCheckedDateProp.isNotEmpty()) {
      val formatter = createDateFormatter()
      val lastCheckedDate = LocalDate.parse(lastCheckedDateProp, formatter)
      if (lastCheckedDate.equals(deprecationDate)) {
        return true
      }
    }
    return false
  }

  private fun maybeStoreDeprecationDate(deprecationData: DevServicesDeprecationData) {
    if (deprecationData.isUnsupported()) return
    val formatter = createDateFormatter()
    val date = deprecationData.date?.format(formatter) ?: return
    PropertiesComponent.getInstance().setValue(DEPRECATION_DATE_PROPERTIES_KEY, date)
  }

  private fun createDateFormatter() = DateTimeFormatter.ofPattern(DEPRECATION_DATE_PATTERN)

  private fun DevServicesDeprecationData.getNotificationIcon() =
    when {
      isUnsupported() -> AllIcons.General.Error
      isDeprecated() -> AllIcons.General.Warning
      else -> throw IllegalStateException("Cannot request notification icon for $this")
    }

  private fun DevServicesDeprecationData.getNotificationType() =
    when {
      isUnsupported() -> NotificationType.ERROR
      isDeprecated() -> NotificationType.WARNING
      else -> throw IllegalStateException("Cannot request notification type for $this")
    }
}
