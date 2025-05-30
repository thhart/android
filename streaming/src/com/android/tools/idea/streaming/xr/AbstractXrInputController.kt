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
package com.android.tools.idea.streaming.xr

import com.android.annotations.concurrency.UiThread
import com.android.emulator.control.XrOptions
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.EmulatorSettings
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import java.awt.Dimension
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_END
import java.awt.event.KeyEvent.VK_HOME
import java.awt.event.KeyEvent.VK_KP_DOWN
import java.awt.event.KeyEvent.VK_KP_LEFT
import java.awt.event.KeyEvent.VK_KP_RIGHT
import java.awt.event.KeyEvent.VK_KP_UP
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_PAGE_DOWN
import java.awt.event.KeyEvent.VK_PAGE_UP
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_UP
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import java.awt.event.MouseWheelEvent
import kotlin.math.PI

/**
 * Orchestrates mouse and keyboard input for XR devices. Keeps track of XR environment and passthrough.
 * Thread safe.
 */
internal abstract class AbstractXrInputController : Disposable {

  @Volatile var environment: XrOptions.Environment? = null
    set(value) {
      requireNotNull(value)
      if (field != value) {
        field = value
        ActivityTracker.getInstance().inc()
      }
    }
  @Volatile var passthroughCoefficient: Float = UNKNOWN_PASSTHROUGH_COEFFICIENT
    set(value) {
      require(value >= 0)
      if (field != value) {
        field = value
        ActivityTracker.getInstance().inc()
      }
    }

  @Volatile var inputMode: XrInputMode =
      if (StudioFlags.EMBEDDED_EMULATOR_XR_HAND_TRACKING.get()) XrInputMode.HAND else XrInputMode.HARDWARE
    @UiThread set(value) {
      if (field != value) {
        if (!areNavigationKeysEnabled(value)) {
          pressedKeysMask = 0 // Reset keyboard navigation state.
          mouseDragReferencePoint = null
        }
        field = value
      }
    }

  private var pressedKeysMask = 0
    set(value) {
      if (field != value) {
        field = value
        navigationMask = pressedKeysMaskToNavigationMask(value)
      }
    }

  private var navigationMask = 0
    set(value) {
      if (field != value) {
        val oldValue = field
        field = value
        sendVelocityUpdate(value, oldValue) // Send update to the emulator.
      }
    }

  protected var mouseDragReferencePoint: Point? = null
  private val emulatorSettings = EmulatorSettings.getInstance()
  private val controlKeys
    get() = emulatorSettings.cameraVelocityControls.keys

  /** Controls passthrough mode on the device. */
  abstract suspend fun setPassthrough(passthroughCoefficient: Float)

  /**
   * Notifies the controller that a key was pressed.
   * Returns true if the input event has been consumed.
   */
  @UiThread
  fun keyPressed(event: KeyEvent): Boolean {
    if (!areNavigationKeysEnabled(inputMode)) {
      return false
    }
    if (event.modifiersEx != 0) {
      pressedKeysMask = 0
      return false
    }
    val mask = keyToMask(event.keyCode)
    if (mask == 0) {
      return false
    }
    pressedKeysMask = pressedKeysMask or mask
    event.consume()
    return true
  }

  /**
   * Notifies the controller that a key was released.
   * Returns true if the input event has been consumed.
   */
  @UiThread
  fun keyReleased(event: KeyEvent): Boolean {
    if (!areNavigationKeysEnabled(inputMode)) {
      return false
    }
    if (event.modifiersEx != 0) {
      pressedKeysMask = 0
      return false
    }
    val mask = keyToMask(event.keyCode)
    if (mask == 0) {
      return false
    }
    pressedKeysMask = pressedKeysMask and mask.inv()
    event.consume()
    return true
  }

  /**
   * Notifies the controller that a mouse button was pressed.
   * Returns true if the input event has been consumed.
   *
   * @param event the AWT event
   * @param deviceDisplaySize the size of the device display in pixels
   * @param scaleFactor the ratio between the size of the device display and the size in logical
   *        pixels of its projection on the host screen
   */
  @UiThread
  fun mousePressed(event: MouseEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean {
    if (!isMouseUsedForNavigation(inputMode)) {
      return false
    }
    if (event.button == BUTTON1) {
      mouseDragReferencePoint = event.point
    }
    event.consume()
    return true
  }

  /**
   * Notifies the controller that a mouse button was released.
   * Returns true if the input event has been consumed.
   *
   * @param event the AWT event
   * @param deviceDisplaySize the size of the device display in pixels
   * @param scaleFactor the ratio between the size of the device display and the size in logical
   *        pixels of its projection on the host screen
   */
  @UiThread
  fun mouseReleased(event: MouseEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean {
    if (!isMouseUsedForNavigation(inputMode)) {
      return false
    }
    if (event.button == BUTTON1) {
      mouseDragReferencePoint = null
    }
    event.consume()
    return true
  }

  /**
   * Notifies the controller that the mouse entered the panel sending events to this controller.
   * Returns true if the input event has been consumed.
   *
   * @param event the AWT event
   * @param deviceDisplaySize the size of the device display in pixels
   * @param scaleFactor the ratio between the size of the device display and the size in logical
   *        pixels of its projection on the host screen
   */
  @UiThread
  fun mouseEntered(event: MouseEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean {
    if (!isMouseUsedForNavigation(inputMode)) {
      return false
    }
    if (event.modifiersEx and MouseEvent.BUTTON1_DOWN_MASK != 0) {
      mouseDragReferencePoint = event.point
    }
    event.consume()
    return true
  }

  /**
   * Notifies the controller that the mouse exited the panel sending events to this controller.
   * Returns true if the input event has been consumed.
   *
   * @param event the AWT event
   * @param deviceDisplaySize the size of the device display in pixels
   * @param scaleFactor the ratio between the size of the device display and the size in logical
   *        pixels of its projection on the host screen
   */
  @UiThread
  fun mouseExited(event: MouseEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean {
    return false
  }

  /**
   * Notifies the controller that the mouse button was dragged.
   * Returns true if the input event has been consumed.
   *
   * @param event the AWT event
   * @param deviceDisplaySize the size of the device display in pixels
   * @param scaleFactor the ratio between the size of the device display and the size in logical
   *        pixels of its projection on the host screen
   */
  @UiThread
  abstract fun mouseDragged(event: MouseEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean

  /**
   * Notifies the controller that the mouse was moved.
   * Returns true if the input event has been consumed.
   *
   * @param event the AWT event
   * @param deviceDisplaySize the size of the device display in pixels
   * @param scaleFactor the ratio between the size of the device display and the size in logical
   *        pixels of its projection on the host screen
   */
  @UiThread
  fun mouseMoved(event: MouseEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean {
    if (!isMouseUsedForNavigation(inputMode)) {
      return false
    }
    event.consume()
    return true
  }

  /**
   * Notifies the controller that the mouse wheel was moved.
   * Returns true if the input event has been consumed.
   *
   * @param event the AWT event
   * @param deviceDisplaySize the size of the device display in pixels
   * @param scaleFactor the ratio between the size of the device display and the size in logical
   *        pixels of its projection on the host screen
   */
  @UiThread
  abstract fun mouseWheelMoved(event: MouseWheelEvent, deviceDisplaySize: Dimension, scaleFactor: Double): Boolean

  private fun areNavigationKeysEnabled(inputMode: XrInputMode): Boolean {
    return when (inputMode) {
      XrInputMode.VIEW_DIRECTION, XrInputMode.LOCATION_IN_SPACE_XY, XrInputMode.LOCATION_IN_SPACE_Z -> true
      else -> false
    }
  }

  private fun keyToMask(keyCode: Int): Int {
    val index = controlKeys.indexOf(keyCode.toChar())
    if (index >= 0) {
      return 1 shl index
    }
    return when (keyCode) {
      VK_RIGHT, VK_KP_RIGHT -> 1 shl NavigationKey.ROTATE_RIGHT.ordinal
      VK_LEFT, VK_KP_LEFT -> 1 shl NavigationKey.ROTATE_LEFT.ordinal
      VK_UP, VK_KP_UP -> 1 shl NavigationKey.ROTATE_UP.ordinal
      VK_DOWN, VK_KP_DOWN -> 1 shl NavigationKey.ROTATE_DOWN.ordinal
      VK_PAGE_UP -> 1 shl NavigationKey.ROTATE_RIGHT_UP.ordinal
      VK_PAGE_DOWN -> 1 shl NavigationKey.ROTATE_RIGHT_DOWN.ordinal
      VK_HOME -> 1 shl NavigationKey.ROTATE_LEFT_UP.ordinal
      VK_END -> 1 shl NavigationKey.ROTATE_LEFT_DOWN.ordinal
      else -> 0
    }
  }

  /**
   * Replaces mask bits corresponding to the diagonal rotation numpad keys by combinations of bits
   * corresponding to horizontal and vertical rotation keys. Also cancels out keys that act in
   * opposite directions, e.g. [NavigationKey.ROTATE_RIGHT] and [NavigationKey.ROTATE_LEFT].
   */
  private fun pressedKeysMaskToNavigationMask(pressedKeysMask: Int): Int {
    var mask = pressedKeysMask and ((1 shl NavigationKey.ROTATE_RIGHT_UP.ordinal) - 1)
    if (pressedKeysMask and (1 shl NavigationKey.ROTATE_RIGHT_UP.ordinal) != 0) {
      mask = mask or (1 shl NavigationKey.ROTATE_RIGHT.ordinal) or (1 shl NavigationKey.ROTATE_UP.ordinal)
    }
    if (pressedKeysMask and (1 shl NavigationKey.ROTATE_RIGHT_DOWN.ordinal) != 0) {
      mask = mask or (1 shl NavigationKey.ROTATE_RIGHT.ordinal) or (1 shl NavigationKey.ROTATE_DOWN.ordinal)
    }
    if (pressedKeysMask and (1 shl NavigationKey.ROTATE_LEFT_UP.ordinal) != 0) {
      mask = mask or (1 shl NavigationKey.ROTATE_LEFT.ordinal) or (1 shl NavigationKey.ROTATE_UP.ordinal)
    }
    if (pressedKeysMask and (1 shl NavigationKey.ROTATE_LEFT_DOWN.ordinal) != 0) {
      mask = mask or (1 shl NavigationKey.ROTATE_LEFT.ordinal) or (1 shl NavigationKey.ROTATE_DOWN.ordinal)
    }
    // Cancel out keys acting in opposite directions.
    val opposites = intArrayOf(
        (1 shl NavigationKey.MOVE_RIGHT.ordinal) or (1 shl NavigationKey.MOVE_LEFT.ordinal),
        (1 shl NavigationKey.MOVE_UP.ordinal) or (1 shl NavigationKey.MOVE_DOWN.ordinal),
        (1 shl NavigationKey.MOVE_BACKWARD.ordinal) or (1 shl NavigationKey.MOVE_FORWARD.ordinal),
        (1 shl NavigationKey.ROTATE_RIGHT.ordinal) or (1 shl NavigationKey.ROTATE_LEFT.ordinal),
        (1 shl NavigationKey.ROTATE_UP.ordinal) or (1 shl NavigationKey.ROTATE_DOWN.ordinal))
    for (m in opposites) {
      if ((mask and m) == m) {
        mask = mask and m.inv()
      }
    }
    return mask
  }

  protected abstract fun sendVelocityUpdate(newMask: Int, oldMask: Int)

  protected fun isMouseUsedForNavigation(inputMode: XrInputMode): Boolean {
    return when (inputMode) {
      XrInputMode.VIEW_DIRECTION, XrInputMode.LOCATION_IN_SPACE_XY, XrInputMode.LOCATION_IN_SPACE_Z -> true
      else -> false
    }
  }

  companion object {
    internal const val UNKNOWN_PASSTHROUGH_COEFFICIENT = -1f

    const val MOUSE_WHEEL_NAVIGATION_FACTOR = 0.25F

    /** Distance of translational movement in meters when moving mouse across the device display. */
    const val TRANSLATION_SCALE = 4f
    /** Angle of rotation in radians when moving mouse across the device display. */
    const val ROTATION_SCALE = PI.toFloat()
    /** Translational velocity in meters per second. */
    const val VELOCITY = 1f
    /** Angular velocity in radians per second. */
    const val ANGULAR_VELOCITY = (PI / 6).toFloat()
  }

  protected enum class NavigationKey {
    MOVE_FORWARD,      // W
    MOVE_LEFT,         // A
    MOVE_BACKWARD,     // S
    MOVE_RIGHT,        // D
    MOVE_DOWN,         // Q
    MOVE_UP,           // E
    ROTATE_RIGHT,      // Right arrow
    ROTATE_LEFT,       // Left arrow
    ROTATE_UP,         // Up arrow
    ROTATE_DOWN,       // Down arrow
    ROTATE_RIGHT_UP,   // Page Up
    ROTATE_RIGHT_DOWN, // Page Down
    ROTATE_LEFT_UP,    // Home
    ROTATE_LEFT_DOWN;  // End

    companion object {
      const val TRANSLATION_MASK: Int = 0x3F
      const val ROTATION_MASK: Int = 0x3C0
    }
  }
}

internal enum class XrInputMode {
  /** Mouse is used to interact with running apps simulating hand tracking. */
  HAND,
  /** Mouse is used to interact with running apps simulating eye tracking. */
  EYE,
  /** Mouse and keyboard events are transparently forwarded to the device. */
  HARDWARE,
  /** Relative mouse coordinates control view direction. */
  VIEW_DIRECTION,
  /** Relative mouse coordinates control location in x-y plane. Mouse wheel controls moving forward and back. */
  LOCATION_IN_SPACE_XY,
  /** Relative mouse y coordinate controls moving forward and back. */
  LOCATION_IN_SPACE_Z,
}
