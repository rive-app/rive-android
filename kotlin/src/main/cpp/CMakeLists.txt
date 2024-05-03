cmake_minimum_required(VERSION 3.18.1)

project(rive-android VERSION 1.0.0 LANGUAGES CXX)

# Compile detail will be in rive-android/kotlin/.cxx/Debug/<hash>/<ABI>/compile_commands.json
#   e.g: kotlin/.cxx/Debug/4o1b5h48/arm64-v8a/compile_commands.json
set(CMAKE_EXPORT_COMPILE_COMMANDS ON CACHE INTERNAL "")

#set(CMAKE_CXX_STANDARD 17)
#set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS "-std=c++17 -Wall -fno-exceptions -fno-rtti")
set(CMAKE_CXX_FLAGS_RELEASE "-Oz")
set(CMAKE_VERBOSE_MAKEFILE ON)

# Define RIVE_RUNTIME_DIR as an env variable so that `build.rive.for.sh` can also use it.
if (EXISTS "${PROJECT_SOURCE_DIR}/../../../../submodules/rive-cpp")
    set(PACKAGES_DIR "${PROJECT_SOURCE_DIR}/../../../../")
    set(ENV{RIVE_RUNTIME_DIR} "${PACKAGES_DIR}/submodules/rive-cpp")
    set(PLS_DIR "${PACKAGES_DIR}/submodules/pls")
else ()
    set(PACKAGES_DIR "${PROJECT_SOURCE_DIR}/../../../../..")
    set(ENV{RIVE_RUNTIME_DIR} "${PACKAGES_DIR}/runtime")
    set(PLS_DIR "${PACKAGES_DIR}/pls")
endif ()

message("BUILD TYPE: ${CMAKE_BUILD_TYPE}")

#[[ CMake adds these flags depending on CMAKE_BUILD_TYPE
    1. Release: `-O3 -DNDEBUG`
    2. Debug: `-O0 -g`
    3. RelWithDebInfo: `-O2 -g -DNDEBUG`
    4. MinSizeRel: `-Os -DNDEBUG`
]]

add_definitions(-DSK_GL)
add_definitions(-DRIVE_GLES)
# Provide this manually so Android Studio plays nicely with C++.
add_definitions(-DWITH_RIVE_AUDIO -DMA_NO_RESOURCE_MANAGER)

if (CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(SKIA_DIR_NAME "skia_debug")
    set(DEBUG_FLAG "-d")
    set(CONFIG "debug")
    add_definitions(-DDEBUG)
endif ()

if (CMAKE_BUILD_TYPE STREQUAL "Release" OR
        CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo" OR
        CMAKE_BUILD_TYPE STREQUAL "MinSizeRel")
    set(SKIA_DIR_NAME "skia")
    set(CONFIG "release")
    add_definitions(-DNDEBUG)
endif ()

message("Skia location: ${SKIA_DIR_NAME}")

# Build dependencies.
execute_process(
        COMMAND bash ./build.rive.for.sh -a ${CMAKE_ANDROID_ARCH_ABI} -b ${DEBUG_FLAG}
        OUTPUT_FILE ${CMAKE_SOURCE_DIR}/rive_build_${CMAKE_ANDROID_ARCH_ABI}.log
        WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
        RESULT_VARIABLE SCRIPT_RESULT
        OUTPUT_VARIABLE SCRIPT_OUTPUT
)

if (NOT SCRIPT_RESULT EQUAL "0")
    message(FATAL_ERROR "Script returned with error: '${SCRIPT_OUTPUT}' - '${SCRIPT_RESULT}'")
endif ()
include_directories(
        include
        $ENV{RIVE_RUNTIME_DIR}/include
        ${PLS_DIR}/include
        $ENV{RIVE_RUNTIME_DIR}/renderer/library/include
        $ENV{RIVE_RUNTIME_DIR}/skia/dependencies/${SKIA_DIR_NAME}/
        $ENV{RIVE_RUNTIME_DIR}/skia/dependencies/${SKIA_DIR_NAME}/include/core
        $ENV{RIVE_RUNTIME_DIR}/skia/dependencies/${SKIA_DIR_NAME}/include/effects
        $ENV{RIVE_RUNTIME_DIR}/skia/dependencies/${SKIA_DIR_NAME}/include/gpu
        $ENV{RIVE_RUNTIME_DIR}/skia/dependencies/${SKIA_DIR_NAME}/include/config
        $ENV{RIVE_RUNTIME_DIR}/skia/renderer/include
)

file(GLOB SOURCES CONFIGURE_DEPENDS
        src/*.cpp
        src/bindings/*.cpp
        src/helpers/*.cpp
        src/models/*.cpp
        )

add_library(rive-android SHARED ${SOURCES})

set(static_libs
        rive
        rive_harfbuzz
        rive_sheenbidi
        rive_skia_renderer
        rive_pls_renderer
        skia
        )

foreach (X IN LISTS static_libs)
    add_library(${X}-lib STATIC IMPORTED)
    set_target_properties(${X}-lib
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_CURRENT_SOURCE_DIR}/build/${CONFIG}/${CMAKE_ANDROID_ARCH_ABI}/lib${X}.a
            )
endforeach ()

target_include_directories(rive-android PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles-lib GLESv3)

target_link_libraries(rive-android
        rive-lib
        rive_harfbuzz-lib
        rive_sheenbidi-lib
        rive_skia_renderer-lib
        rive_pls_renderer-lib
        skia-lib

        ${log-lib}
        ${android-lib}
        ${egl-lib}
        ${gles-lib}
        jnigraphics
        )
