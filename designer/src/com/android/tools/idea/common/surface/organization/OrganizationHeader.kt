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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.compose.StudioTheme
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.enableNewSwingCompositing
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import javax.swing.JComponent

private val toolbarSpacing = 6.dp
private const val descriptionOpened = "Hide preview group"
private const val descriptionClosed = "Show preview group"

@Composable
fun OrganizationHeader(group: OrganizationGroup) {
  val opened = group.isOpened.collectAsState()

  IconButton(
    modifier =
      Modifier.testTag("openButton")
        .height(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height.dp),
    onClick = { group.setOpened(!opened.value) },
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (opened.value)
        Icon(AllIconsKeys.General.ChevronDown, contentDescription = descriptionOpened)
      else Icon(AllIconsKeys.General.ChevronRight, contentDescription = descriptionClosed)

      Spacer(Modifier.width(toolbarSpacing))

      group.groupType.iconKey?.let { it ->
        Icon(key = it, contentDescription = null, modifier = Modifier.testTag("groupIcon"))
        Spacer(Modifier.width(toolbarSpacing))
      }

      Text(
        group.displayName,
        modifier = Modifier.testTag("displayName"),
        color = AdtUiUtils.HEADER_COLOR.toComposeColor(),
        fontSize = TextUnit(UIUtil.getFontSize(UIUtil.FontSize.SMALL), TextUnitType.Sp),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.width(toolbarSpacing))
    }
  }
}

/** Wraps [OrganizationHeader] into [JComponent]. */
@OptIn(ExperimentalJewelApi::class)
fun createOrganizationHeader(group: OrganizationGroup): JComponent {
  enableNewSwingCompositing()
  return ComposePanel().apply { setContent { StudioTheme { OrganizationHeader(group) } } }
}

fun createTestOrganizationHeader(group: OrganizationGroup): JComponent {
  return JBLabel(group.displayName)
}
