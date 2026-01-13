cmake_minimum_required(VERSION 3.18.1)

project(rive-android VERSION 1.0.0 LANGUAGES CXX)

# Compile detail will be in rive-android/kotlin/.cxx/Debug/<hash>/<ABI>/compile_commands.json
#   e.g: kotlin/.cxx/Debug/4o1b5h48/arm64-v8a/compile_commands.json
set(CMAKE_EXPORT_COMPILE_COMMANDS ON CACHE INTERNAL "")
set(CMAKE_VERBOSE_MAKEFILE ON)

# Find all source files
file(GLOB SOURCES CONFIGURE_DEPENDS
        src/*.cpp
        src/bindings/*.cpp
        src/helpers/*.cpp
        src/models/*.cpp
)

# Create the rive-android library target with the given sources
add_library(rive-android SHARED ${SOURCES})

# List of static libraries
set(static_libs
        rive
        rive_harfbuzz
        rive_sheenbidi
        rive_yoga
        rive_pls_renderer
)

# Enable C++17
target_compile_features(rive-android PUBLIC cxx_std_17)
target_compile_options(rive-android PRIVATE
        -Wall # Enable all warnings
        -fno-exceptions # Disable C++ exceptions
        -fno-rtti # Disable C++ RTTI
        $<$<CONFIG:Release>:-Oz>) # Optimize for size with -Oz in release
target_compile_definitions(rive-android PRIVATE
        RIVE_ANDROID=
        YOGA_EXPORT=
        $<$<CONFIG:Debug>:DEBUG>)

# Configure for monorepo vs. rive-android repo
if (EXISTS "${PROJECT_SOURCE_DIR}/../../../../submodules/rive-runtime")
    set(PACKAGES_DIR "${PROJECT_SOURCE_DIR}/../../../../")
    set(RIVE_RUNTIME_DIR "${PACKAGES_DIR}/submodules/rive-runtime")
else ()
    set(PACKAGES_DIR "${PROJECT_SOURCE_DIR}/../../../../..")
    set(RIVE_RUNTIME_DIR "${PACKAGES_DIR}/runtime")
endif ()
# Pass the resolved runtime directory to the environment for Premake
set(ENV{RIVE_RUNTIME_DIR} "${RIVE_RUNTIME_DIR}")

# Add an option to remove miniaudio
# Passed from Gradle with -DWITH_RIVE_AUDIO=ON|OFF
option(WITH_RIVE_AUDIO "Enable Rive audio support" ON)
if (WITH_RIVE_AUDIO)
    # Compile flag picked up by JNI C++
    target_compile_definitions(rive-android PRIVATE
            WITH_RIVE_AUDIO
            MA_NO_RESOURCE_MANAGER)
    # Value for Premake --with_rive_audio
    set(RIVE_AUDIO_ARG "system")
    # Add to the list of static libraries
    list(APPEND static_libs miniaudio)
else ()
    # Value for Premake --with_rive_audio
    set(RIVE_AUDIO_ARG "disabled")
endif ()

# Add an option to remove scripting
# Passed from Gradle with -DWITH_SCRIPTING=ON|OFF
option(WITH_SCRIPTING "Enable Rive scripting support" ON)
if (WITH_SCRIPTING)
    # Flag for Premake
    set(RIVE_SCRIPTING_ARG "--with_rive_scripting")
    # Add to the list of static libraries
    list(APPEND static_libs luau_vm)
endif ()

# Add an option to enable ASAN
# Passed from Gradle with -DENABLE_ASAN=ON|OFF
option(ENABLE_ASAN "Enable Address Sanitizer" OFF)
if (ENABLE_ASAN)
    # Pass to Premake to enable ASAN on librive and its dependencies
    set(RIVE_ASAN_FLAG "--with-asan")

    # Add ASAN flags to rive-android as well
    target_compile_options(rive-android PRIVATE
            -fsanitize=address
            -fno-omit-frame-pointer)
    target_link_options(rive-android PRIVATE
            -fsanitize=address
            -Wl,--export-dynamic
            -Wl,--no-undefined)
endif ()

# Debug/release configuration
message("librive-android build type: ${CMAKE_BUILD_TYPE}")
#[[ CMake adds these flags depending on CMAKE_BUILD_TYPE
    1. Debug: `-O0 -g`
    2. Release: `-O3 -DNDEBUG`
    3. RelWithDebInfo: `-O2 -g -DNDEBUG`
    4. MinSizeRel: `-Os -DNDEBUG`
]]
if (CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CONFIG "debug")
elseif (CMAKE_BUILD_TYPE STREQUAL "Release" OR
        CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo" OR
        CMAKE_BUILD_TYPE STREQUAL "MinSizeRel")
    set(CONFIG "release")
endif ()

# Translate Android ABI to Rive architecture
if (CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    set(RIVE_ARCH "arm64")
elseif (CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
    set(RIVE_ARCH "arm")
elseif (CMAKE_ANDROID_ARCH_ABI STREQUAL "x86_64")
    set(RIVE_ARCH "x64")
elseif (CMAKE_ANDROID_ARCH_ABI STREQUAL "x86")
    set(RIVE_ARCH "x86")
else ()
    message(FATAL_ERROR "Unsupported ABI: ${CMAKE_ANDROID_ARCH_ABI}")
endif ()

# Promote our ${ANDROID_NDK} to an environment var so Premake uses the same one.
set(ENV{ANDROID_NDK} "${ANDROID_NDK}")

# Build the Rive C++ runtime and its dependencies as static .a libs
execute_process(
        COMMAND bash ${RIVE_RUNTIME_DIR}/build/build_rive.sh --file=premake5_cpp_runtime.lua android ninja ${CONFIG} ${RIVE_ARCH} --no-rive-decoders --with_rive_audio=${RIVE_AUDIO_ARG} ${RIVE_SCRIPTING_ARG} ${RIVE_ASAN_FLAG} -- rive_cpp_runtime
        WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
        RESULT_VARIABLE SCRIPT_RESULT
        OUTPUT_VARIABLE SCRIPT_OUTPUT
        ECHO_OUTPUT_VARIABLE
)
if (NOT SCRIPT_RESULT EQUAL "0")
    message(FATAL_ERROR "Script returned with error: '${SCRIPT_OUTPUT}' - '${SCRIPT_RESULT}'")
endif ()

# Add header files
target_include_directories(rive-android PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${RIVE_RUNTIME_DIR}/include
        ${RIVE_RUNTIME_DIR}/renderer/include)

# Link each static library
foreach (X IN LISTS static_libs)
    set(LIB_TARGET "${X}-lib")
    add_library(${LIB_TARGET} STATIC IMPORTED)
    # Find the .a outputs from Premake
    set_target_properties(${LIB_TARGET}
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_CURRENT_SOURCE_DIR}/out/android_${RIVE_ARCH}_${CONFIG}/lib${X}.a
    )
    # Link
    target_link_libraries(rive-android ${LIB_TARGET})
endforeach ()

# Find additional libraries
find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles-lib GLESv3)

# Link additional libraries
target_link_libraries(rive-android
        ${log-lib}
        ${android-lib}
        ${egl-lib}
        ${gles-lib}
        jnigraphics
)
