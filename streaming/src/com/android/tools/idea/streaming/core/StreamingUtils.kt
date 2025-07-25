/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.android.sdklib.SystemImageTags
import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.adtui.util.scaled
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.actions.StreamingAction
import com.intellij.ide.actions.ShowLogAction
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionHolder
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.builder.components.DslLabelType
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.nio.ByteBuffer
import javax.swing.Icon
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun ByteBuffer.getUInt(): UInt =
   getInt().toUInt()

fun ByteBuffer.putUInt(value: UInt): ByteBuffer =
   putInt(value.toInt())

/**
 * If this [AnActionEvent] is associated with an [ActionButtonComponent], returns that component.
 * Otherwise, returns the first found component associated with the given action belonging to
 * the Running Devices tool window.
 */
fun AnActionEvent.findComponentForAction(action: AnAction): Component? =
    findComponentForAction(action, RUNNING_DEVICES_TOOL_WINDOW_ID)

/**
 * If this [AnActionEvent] is associated with an [ActionButtonComponent], returns that component.
 * Otherwise, returns the first found component associated with the given action belonging to
 * the given tool window.
 */
private fun AnActionEvent.findComponentForAction(action: AnAction, toolWindowId: String): Component? {
  val project = project ?: return null
  val component = inputEvent?.component
  if (component is ActionButtonComponent) {
    return component
  }
  val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId) ?: return null
  return toolWindow.component.parent?.findComponentForAction(action)
}

/**
 * Searches the AWT component tree starting from this [Component] for a component that is
 * an [AnActionHolder] and holds the given action.
 */
private fun Component.findComponentForAction(action: AnAction): Component? {
  if (isComponentForAction(action)) {
    return this
  }
  if (this is Container) {
    val queue = ArrayDeque<Container>().also { it.add(this) }
    while (queue.isNotEmpty()) {
      for (child in queue.removeFirst().components) {
        if (child.isComponentForAction(action)) {
          return child
        }
        if (child is Container) {
          queue.add(child)
        }
      }
    }
  }
  return null
}

private fun Component.isComponentForAction(action: AnAction): Boolean =
    this is AnActionHolder && this.action.isEquivalentTo(action)

private fun AnAction.isEquivalentTo(action: AnAction): Boolean =
    this === action || (this is StreamingAction && mayDelegate(action))

internal inline fun <reified T : Component> Component.findContainingComponent(): T? {
  var component = parent
  while (component != null) {
    if (component is T) {
      return component
    }
    component = component.parent
  }
  return null
}

// TODO(b/289230363): use DeviceHandle.state.properties.icon, since it is the source of truth for device icons.
internal val AvdInfo.icon: Icon
  get() {
    return when {
      SystemImageTags.isTvImage(tags) -> StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_TV
      SystemImageTags.isAutomotiveImage(tags) -> StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_CAR
      SystemImageTags.isWearImage(tags) -> StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR
      SystemImageTags.isXrImage(tags) -> StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_HEADSET
      else -> StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE
    }
  }

/**
 * Returns this integer multiplied by [scale] and rounded down towards zero.
 */
internal fun Int.scaledDown(scale: Double): Int =
    (this * scale).toInt()

/**
 * Returns this integer scaled and rounded up away from zero.
 *
 * @param scale the scale factor
 */
internal fun Int.scaledUp(scale: Double): Int =
    ceil(this * scale).roundToInt()

/**
 * Returns this integer scaled by multiplying by [numerator] and then dividing by [denominator].
 */
internal fun Int.scaledDown(numerator: Int, denominator: Int): Int =
    ((this.toLong() * numerator) / denominator).toInt()

/**
 * Converts this value from the `[0, fromRange-1]` interval to the `[0, toRange - 1]`interval by scaling by
 * the [toRange]/[fromRange] factor while maintaining symmetry with respect to the centers of the two intervals.
 *
 * The conversion is reversible, i.e. if `fromRange <= toRange`, then for every `i` in the `[0, fromRange-1]`
 * interval `i.scaledUnbiased(fromRange, toRange).scaledUnbiased(toRange, fromRange) = i`.
 */
internal fun Int.scaledUnbiased(fromRange: Int, toRange: Int): Int =
    ((this * 2L + 1) * toRange / (2 * fromRange)).toInt()

internal fun Point.scaledUnbiased(fromDim: Dimension, toDim: Dimension): Point =
    Point(x.scaledUnbiased(fromDim.width, toDim.width), y.scaledUnbiased(fromDim.height, toDim.height))

/**
 * Checks if the ratio between [width1] and [height1] is the same as the ratio between
 * [width2] and [height2] within the given relative [tolerance].
 */
internal fun isSameAspectRatio(width1: Int, height1: Int, width2: Int, height2: Int, tolerance: Double): Boolean {
  val a = width1.toDouble() * height2
  val b = width2.toDouble() * height1
  val d = a - b
  return abs(d) <= tolerance * abs(a + b) / 2
}

/**
 * Returns this Dimension if both its components are not greater than the [maximumValue], otherwise
 * returns this Dimension scaled down to satisfy this requirement while preserving the aspect ratio.
 */
internal fun Dimension.coerceAtMost(maximumValue: Dimension): Dimension {
  if (width <= maximumValue.width && height <= maximumValue.height) {
    return this
  }
  val scale = min(maximumValue.width.toDouble() / width, maximumValue.height.toDouble() / height).coerceAtMost(1.0)
  return Dimension(width.scaled(scale).coerceAtMost(maximumValue.width), height.scaled(scale).coerceAtMost(maximumValue.height))
}

internal val Container.sizeWithoutInsets: Dimension
  get() = Dimension(max(width - insets.left - insets.right, 0), max(height - insets.top - insets.bottom, 0))

internal fun Point.constrainInside(d: Dimension) =
    if (this in d) this else Point(x.coerceIn(0, d.width - 1), y.coerceIn(0, d.height - 1))

internal operator fun Dimension.contains(p: Point) = p.x in 0 until width && p.y in 0 until height

internal val Rectangle.right: Int
  get() = x + width

internal val Rectangle.bottom: Int
  get() = y + height

internal val MouseEvent.location: Point
  get() = Point(x, y)

/** Wraps the string with &lt;font color=...>, &lt;/font> tags. */
internal fun String.htmlColored(color: Color): String =
    "<font color=${(color.rgb and 0xFFFFFF).toString(16)}>$this</font>"

/** Returns an HTML hyperlink for showing the log. */
internal fun getShowLogHyperlink(): String =
    if (ShowLogAction.isSupported()) "<a href='ShowLog'>log</a>".htmlColored(JBUI.CurrentTheme.Link.Foreground.ENABLED) else "log"

/** Returns a hyperlink listener for showing the log. */
internal fun createShowLogHyperlinkListener(): HyperlinkListener {
  return HyperlinkListener { event ->
    if (event.eventType == HyperlinkEvent.EventType.ACTIVATED && event.description == "ShowLog") {
      ShowLogAction.showLog()
    }
  }
}

/** Creates a text component supporting HTML. */
internal fun textComponent(
  @NlsContexts.Label text: String,
  maxLineLength: Int = MAX_LINE_LENGTH_WORD_WRAP,
  action: HyperlinkEventAction = HyperlinkEventAction.HTML_HYPERLINK_INSTANCE
): JEditorPane {
  @Suppress("UnstableApiUsage")
  return DslLabel(DslLabelType.LABEL).apply {
    this.action = action
    this.maxLineLength = maxLineLength
    if (maxLineLength == MAX_LINE_LENGTH_WORD_WRAP) {
      limitPreferredSize = true
    }
    this.text = text
  }
}

internal fun Component.containsFocus(): Boolean {
  var component = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
  while (component != null) {
    if (component == this) {
      return true
    }
    component = component.parent
  }
  return false
}