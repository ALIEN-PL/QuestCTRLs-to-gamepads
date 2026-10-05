cmake_minimum_required(VERSION 3.22.1)
project("questgamepad_uinput")

add_library(questgamepad_uinput SHARED
    uinput_jni.cpp
)

target_link_libraries(questgamepad_uinput
    log
)
