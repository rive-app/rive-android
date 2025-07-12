cmake_minimum_required(VERSION 3.18.1)

project(rive-android VERSION 1.0.0 LANGUAGES CXX)

# Compile detail will be in rive-android/kotlin/.cxx/Debug/<hash>/<ABI>/compile_commands.json
#   e.g: kotlin/.cxx/Debug/4o1b5h48/arm64-v8a/compile_commands.json
set(CMAKE_EXPORT_COMPILE_COMMANDS ON CACHE INTERNAL "")

#set(CMAKE_CXX_STANDARD 17)
#set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS "-std=c++17 -Wall -fno-exceptions -fno-rtti -DYOGA_EXPORT=")
set(CMAKE_CXX_FLAGS_RELEASE "-Oz -DYOGA_EXPORT=")
set(CMAKE_VERBOSE_MAKEFILE ON)

if (EXISTS "${PROJECT_SOURCE_DIR}/../../../../submodules/rive-runtime")
    set(PACKAGES_DIR "${PROJECT_SOURCE_DIR}/../../../../")
    set(RIVE_RUNTIME_DIR "${PACKAGES_DIR}/submodules/rive-runtime")
else ()
    set(PACKAGES_DIR "${PROJECT_SOURCE_DIR}/../../../../..")
    set(RIVE_RUNTIME_DIR "${PACKAGES_DIR}/runtime")
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

# Add an option to remove miniaudio
option(WITH_RIVE_AUDIO "Enable Rive audio support" ON)
if (WITH_RIVE_AUDIO)
    # Compile flag picked up by JNI C++
    add_definitions(-DWITH_RIVE_AUDIO -DMA_NO_RESOURCE_MANAGER)
    # Value for Premake --with_rive_audio
    set(RIVE_AUDIO_ARG "system")
else ()
    # Value for Premake --with_rive_audio
    set(RIVE_AUDIO_ARG "disabled")
endif ()

if (CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CONFIG "debug")
    add_definitions(-DDEBUG)
endif ()

if (CMAKE_BUILD_TYPE STREQUAL "Release" OR
        CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo" OR
        CMAKE_BUILD_TYPE STREQUAL "MinSizeRel")
    set(CONFIG "release")
    add_definitions(-DNDEBUG)
endif ()

if (CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    set(RIVE_ARCH "arm64")
elseif (CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
    set(RIVE_ARCH "arm")
elseif (CMAKE_ANDROID_ARCH_ABI STREQUAL "x86_64")
    set(RIVE_ARCH "x64")
elseif (CMAKE_ANDROID_ARCH_ABI STREQUAL "i686")
    set(RIVE_ARCH "x86")
else ()
    set(RIVE_ARCH "${CMAKE_ANDROID_ARCH_ABI}")
endif ()

# Promote our ${ANDROID_NDK} to an environment var so premake uses the same one.
set(ENV{ANDROID_NDK} "${ANDROID_NDK}")

# Build dependencies.
execute_process(
        COMMAND bash ${RIVE_RUNTIME_DIR}/build/build_rive.sh --file=premake5_cpp_runtime.lua android ninja ${CONFIG} ${RIVE_ARCH} --no-rive-decoders --with_rive_audio=${RIVE_AUDIO_ARG} -- rive_cpp_runtime
        WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
        RESULT_VARIABLE SCRIPT_RESULT
        OUTPUT_VARIABLE SCRIPT_OUTPUT
        ECHO_OUTPUT_VARIABLE
)
if (NOT SCRIPT_RESULT EQUAL "0")
    message(FATAL_ERROR "Script returned with error: '${SCRIPT_OUTPUT}' - '${SCRIPT_RESULT}'")
endif ()

include_directories(
        include
        ${RIVE_RUNTIME_DIR}/include
        ${RIVE_RUNTIME_DIR}/renderer/include
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
        rive_yoga
        rive_pls_renderer
)

# Conditionally add miniaudio
# Passed from Gradle with -DWITH_RIVE_AUDIO=ON|OFF
if (WITH_RIVE_AUDIO)
    list(APPEND static_libs miniaudio)
endif ()

foreach (X IN LISTS static_libs)
    add_library(${X}-lib STATIC IMPORTED)
    set_target_properties(${X}-lib
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_CURRENT_SOURCE_DIR}/out/android_${RIVE_ARCH}_${CONFIG}/lib${X}.a
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
        rive_yoga-lib
        rive_pls_renderer-lib

        ${log-lib}
        ${android-lib}
        ${egl-lib}
        ${gles-lib}
        jnigraphics
)

# Conditionally link miniaudio
if (WITH_RIVE_AUDIO)
    target_link_libraries(rive-android miniaudio-lib)
endif ()
