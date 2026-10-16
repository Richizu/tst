cmake_minimum_required(VERSION 3.22.1)
project(rkn_native C)

file(GLOB BYEDPI_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/byedpi/*.c)
list(REMOVE_ITEM BYEDPI_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/byedpi/win_service.c)

add_library(byedpi SHARED ${BYEDPI_SOURCES} bridge/byedpi_jni.c)
target_include_directories(byedpi PRIVATE byedpi)
# byedpi's entry point is renamed so the bridge can call it like a library function.
target_compile_definitions(byedpi PRIVATE ANDROID_APP main=byedpi_main)
target_compile_options(byedpi PRIVATE -std=c99 -O2 -Wall -Wno-unused -Wno-unused-parameter)
target_link_libraries(byedpi PRIVATE android log)
