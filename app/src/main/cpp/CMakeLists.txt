cmake_minimum_required(VERSION 3.22.1)

project("oboe_audio_engine")

find_package(oboe REQUIRED CONFIG)

add_library(
    oboe_audio_engine
    SHARED
    oboe_audio_engine.cpp
)

target_link_libraries(
    oboe_audio_engine
    oboe::oboe
    android
    log
)
