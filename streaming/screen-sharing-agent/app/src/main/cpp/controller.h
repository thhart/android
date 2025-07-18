/*
 * Copyright (C) 2021 The Android Open Source Project
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

#pragma once

#include <atomic>
#include <chrono>
#include <map>
#include <mutex>
#include <vector>

#include "accessors/clipboard_manager.h"
#include "accessors/device_state_manager.h"
#include "accessors/display_manager.h"
#include "accessors/key_character_map.h"
#include "accessors/key_event.h"
#include "accessors/motion_event.h"
#include "accessors/pointer_helper.h"
#include "accessors/xr_simulated_input_manager.h"
#include "base128_input_stream.h"
#include "common.h"
#include "control_messages.h"
#include "geom.h"
#include "jvm.h"
#include "ui_settings.h"
#include "virtual_input_device.h"

namespace screensharing {

// Processes control socket commands.
class Controller : private DisplayManager::DisplayListener, ClipboardManager::ClipboardListener, DeviceStateManager::DeviceStateListener,
                           XrSimulatedInputManager::EnvironmentListener {
public:
  explicit Controller(int socket_fd);
  virtual ~Controller();

  void Run();
  // Stops the controller asynchronously. The controller can't be restarted one stopped.
  // May be called on any thread.
  void Stop();
  // Requests to power the display OFF or reset it to a power state it supposed to have. Requires API 35+.
  // The state parameter is one of DisplayInfo::STATE_OFF (to turn display off), DisplayInfo::STATE_UNKNOWN
  // (to reset the display to its default state). Returns true if successful, false otherwise.
  static bool ControlDisplayPower(Jni jni, int state);

private:
  struct DisplayEvent {
    enum Type { ADDED, CHANGED, REMOVED };

    DisplayEvent(int32_t displayId, Type type)
        : display_id(displayId),
          type(type) {
    }

    int32_t display_id;
    Type type;
  };

  void Initialize();
  void InitializeVirtualKeyboard();
  [[nodiscard]] VirtualMouse& GetVirtualMouse(int32_t display_id);
  [[nodiscard]] VirtualTouchscreen& GetVirtualTouchscreen(int32_t display_id, int32_t width, int32_t height);
  [[nodiscard]] VirtualTablet& GetVirtualTablet(int32_t display_id, int32_t width, int32_t height);
  void ProcessMessage(const ControlMessage& message);
  void ProcessMotionEvent(const MotionEventMessage& message);
  void ProcessKeyboardEvent(const KeyEventMessage& message) {
    ProcessKeyboardEvent(jni_, message);
  }
  void ProcessKeyboardEvent(Jni jni, const KeyEventMessage& message);
  void ProcessTextInput(const TextInputMessage& message);
  void InjectMotionEvent(const MotionEvent& input_event);
  void InjectKeyEvent(const KeyEvent& input_event);
  void InjectInputEvent(const JObject& input_event);

  static void ProcessSetDeviceOrientation(const SetDeviceOrientationMessage& message);
  static void ProcessSetMaxVideoResolution(const SetMaxVideoResolutionMessage& message);
  void StartVideoStream(const StartVideoStreamMessage& message);
  static void StopVideoStream(const StopVideoStreamMessage& message);
  static void StartAudioStream(const StartAudioStreamMessage& message);
  static void StopAudioStream(const StopAudioStreamMessage& message);
  void WakeUpDevice();

  void StartClipboardSync(const StartClipboardSyncMessage& message);
  void StopClipboardSync();
  virtual void OnPrimaryClipChanged() override;
  void SendClipboardChangedNotification();

  void ProcessXrRotation(const XrRotationMessage& message);
  void ProcessXrTranslation(const XrTranslationMessage& message);
  void ProcessXrAngularVelocity(const XrAngularVelocityMessage& message);
  void ProcessXrVelocity(const XrVelocityMessage& message);
  void XrRecenter(const XrRecenterMessage& message);
  void XrSetPassthroughCoefficient(const XrSetPassthroughCoefficientMessage& message);
  void XrSetEnvironment(const XrSetEnvironmentMessage& message);
  virtual void OnPassthroughCoefficientChanged(float passthrough_coefficient) override;
  virtual void OnEnvironmentChanged(int32_t environment) override;
  void SendXrEnvironmentNotification();
  void InjectXrMotionEvent(const JObject& motion_event);

  void RequestDeviceState(const RequestDeviceStateMessage& message);
  virtual void OnDeviceStateChanged(int32_t device_state) override;
  void SendDeviceStateNotification();

  void SendDisplayConfigurations(const DisplayConfigurationRequest& request);
  void OnDisplayAdded(int32_t display_id) override;
  void OnDisplayRemoved(int32_t display_id) override;
  void OnDisplayChanged(int32_t display_id) override;
  void SendPendingDisplayEvents();

  void SendUiSettings(const UiSettingsRequest& request);
  void ChangeUiSetting(const UiSettingsChangeRequest& request);
  void ResetUiSettings(const ResetUiSettingsRequest& request);

  void StartDisplayPolling();
  void StopDisplayPolling();
  void PollDisplays();
  std::map<int32_t, DisplayInfo> GetDisplays();

  Jni jni_ = nullptr;
  int socket_fd_;  // Owned.
  Base128InputStream input_stream_;
  Base128OutputStream output_stream_;
  volatile bool stopped = false;
  PointerHelper* pointer_helper_ = nullptr;  // Owned.
  JObjectArray pointer_properties_;  // MotionEvent.PointerProperties[]
  JObjectArray pointer_coordinates_;  // MotionEvent.PointerCoords[]
  bool input_event_injection_disabled_ = false;
  VirtualKeyboard* virtual_keyboard_ = nullptr;
  VirtualMouse* virtual_mouse_ = nullptr;
  int32_t virtual_mouse_display_id_ = -1;
  // Virtual touchscreens keyed by display IDs.
  std::map<int32_t, std::unique_ptr<VirtualTouchscreen>> virtual_touchscreens_;
  // Virtual drawing tablets keyed by display IDs.
  std::map<int32_t, std::unique_ptr<VirtualTablet>> virtual_tablets_;
  int64_t motion_event_start_time_ = 0;
  KeyCharacterMap* key_character_map_ = nullptr;  // Owned.

  int max_synced_clipboard_length_ = 0;
  std::string last_clipboard_text_;
  std::atomic_bool clipboard_changed_ = false;

  bool device_supports_multiple_states_ = false;
  std::atomic_int32_t device_state_identifier_ = DeviceStateManager::INVALID_DEVICE_STATE_IDENTIFIER;
  int32_t sent_device_state_ = DeviceStateManager::INVALID_DEVICE_STATE_IDENTIFIER;

  std::atomic<float> xr_passthrough_coefficient_ = XrSimulatedInputManager::UNKNOWN_PASSTHROUGH_COEFFICIENT;
  float sent_xr_passthrough_coefficient_ = XrSimulatedInputManager::UNKNOWN_PASSTHROUGH_COEFFICIENT;
  std::atomic_int32_t xr_environment_ = XrSimulatedInputManager::UNKNOWN_ENVIRONMENT;
  int32_t sent_xr_environment_ = XrSimulatedInputManager::UNKNOWN_ENVIRONMENT;

  std::mutex display_events_mutex_;
  std::vector<DisplayEvent> pending_display_events_;  // GUARDED_BY(display_events_mutex_)
  std::map<int32_t, DisplayInfo> current_displays_;

  UiSettings ui_settings_;

  std::chrono::steady_clock::time_point poll_displays_until_;

  DISALLOW_COPY_AND_ASSIGN(Controller);
};

}  // namespace screensharing
