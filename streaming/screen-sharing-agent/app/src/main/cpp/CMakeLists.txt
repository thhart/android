# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

cmake_minimum_required(VERSION 3.22.1)

project("screensharing")

set(${CMAKE_C_FLAGS}, "${CMAKE_C_FLAGS}")

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++20 -Wall -Wno-unknown-pragmas")

add_library(
        screen-sharing-agent

        SHARED

        accessors/audio_manager.cc
        accessors/clipboard_manager.cc
        accessors/device_state_manager.cc
        accessors/display_control.cc
        accessors/display_info.cc
        accessors/display_listener_dispatcher.cc
        accessors/display_manager.cc
        accessors/input_manager.cc
        accessors/key_character_map.cc
        accessors/key_event.cc
        accessors/motion_event.cc
        accessors/pointer_helper.cc
        accessors/service_manager.cc
        accessors/surface_control.cc
        accessors/virtual_display.cc
        accessors/window_manager.cc
        accessors/xr_simulated_input_event_manager.cc
        accessors/xr_simulated_input_manager.cc
        agent.cc
        audio_streamer.cc
        base128_input_stream.cc
        base128_output_stream.cc
        codec_output_buffer.cc
        concurrent_list.cc
        control_messages.cc
        controller.cc
        device_state.cc
        display_streamer.cc
        io_exception.cc
        jvm.cc
        log.cc
        main.cc
        scoped_setting.cc
        shell_command.cc
        shell_command_executor.cc
        session_environment.cc
        settings.cc
        socket_reader.cc
        socket_writer.cc
        string_printf.cc
        string_util.cc
        surface.cc
        token_iterator.cc
        ui_settings.cc
        ui_settings_state.cc
        video_packet_header.cc
        virtual_input_device.cc
)

target_include_directories(screen-sharing-agent PRIVATE .)

find_library(aaudio aaudio)
find_library(log log)
find_library(mediandk mediandk)

target_link_libraries(
        screen-sharing-agent
        -landroid

        ${aaudio}
        ${log}
        ${mediandk}
)