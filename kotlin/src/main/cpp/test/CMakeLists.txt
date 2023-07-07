cmake_minimum_required(VERSION 3.18.1)

project(example LANGUAGES CXX VERSION 0.0.1)

include(FetchContent)

FetchContent_Declare(
  Catch2
  GIT_REPOSITORY https://github.com/catchorg/Catch2.git
  GIT_TAG        v3.3.2
)

FetchContent_MakeAvailable(Catch2)

set(CMAKE_CXX_FLAGS "-std=c++17 -Wall -fno-exceptions -fno-rtti -Oz")
set(CMAKE_CXX_FLAGS_DEBUG "-g")
set(CMAKE_CXX_FLAGS_RELEASE "-Oz")
set(CMAKE_VERBOSE_MAKEFILE ON)

set(SKIA_DIR_NAME "skia")
set(RIVE_ANDROID_CPP_DIR "${PROJECT_SOURCE_DIR}/../")
set(RIVE_RUNTIME_DIR "${PROJECT_SOURCE_DIR}/../../../runtime")

add_library(rive-android-lib SHARED IMPORTED)
set_target_properties(rive-android-lib
  PROPERTIES IMPORTED_LOCATION
  ${CMAKE_CURRENT_SOURCE_DIR}/output/ninja/arm64-v8a/librive-android.so
)
add_executable(example_test_suite first_test.cpp)
target_link_libraries(example_test_suite PRIVATE 
  rive-android-lib
  Catch2::Catch2WithMain 
)

list(APPEND CMAKE_MODULE_PATH ${catch2_SOURCE_DIR}/extras)

include_directories(
        ${RIVE_ANDROID_CPP_DIR}/include
        ${RIVE_RUNTIME_DIR}/include
        ${RIVE_RUNTIME_DIR}/renderer/library/include
        ${RIVE_RUNTIME_DIR}/skia/dependencies/${SKIA_DIR_NAME}/
        ${RIVE_RUNTIME_DIR}/skia/dependencies/${SKIA_DIR_NAME}/include/core
        ${RIVE_RUNTIME_DIR}/skia/dependencies/${SKIA_DIR_NAME}/include/effects
        ${RIVE_RUNTIME_DIR}/skia/dependencies/${SKIA_DIR_NAME}/include/gpu
        ${RIVE_RUNTIME_DIR}/skia/dependencies/${SKIA_DIR_NAME}/include/config
        ${RIVE_RUNTIME_DIR}/skia/renderer/include
)
