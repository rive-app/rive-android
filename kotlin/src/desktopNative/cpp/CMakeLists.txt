cmake_minimum_required(VERSION 3.22)
project(rive-jvm LANGUAGES CXX)

# Desktop JVM build of the Rive command queue: the same JNI bindings as the
# Android library, rendering offscreen through Vulkan (MoltenVK on macOS).
# Consumed by the jvm() target's resources and the rive-preview artifact.

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if (NOT CMAKE_BUILD_TYPE)
    # Release keeps rive_vk_bootstrap's debug validation callbacks off; the
    # debug callback aborts on benign MoltenVK portability warnings.
    set(CMAKE_BUILD_TYPE Release)
endif ()

set(MAIN_CPP ${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp)
get_filename_component(REPO_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../.. ABSOLUTE)
set(RIVE_RUNTIME_DIR ${REPO_ROOT}/submodules/rive-runtime)

# Rive architecture from the host processor
if (CMAKE_SYSTEM_PROCESSOR MATCHES "arm64|aarch64")
    set(RIVE_ARCH "arm64")
else ()
    set(RIVE_ARCH "x64")
endif ()

if (CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CONFIG "debug")
else ()
    set(CONFIG "release")
endif ()

# Generated artifacts (premake output and fetched dependencies) go under the
# Gradle build directory, mirroring the Android CMakeLists.
set(RIVE_NATIVE_BUILD_DIR ${REPO_ROOT}/kotlin/build/rive-native-desktop)
set(RIVE_PREMAKE_OUT_DIR ${RIVE_NATIVE_BUILD_DIR}/out/${RIVE_ARCH}_${CONFIG})
set(RIVE_DEPENDENCIES_DIR ${RIVE_NATIVE_BUILD_DIR}/dependencies)
set(ENV{RIVE_RUNTIME_DIR} ${RIVE_RUNTIME_DIR})
set(ENV{RIVE_OUT} out/${RIVE_ARCH}_${CONFIG})
set(ENV{DEPENDENCIES} ${RIVE_DEPENDENCIES_DIR})
# dependency.lua only creates the dependencies dir when the override is unset.
file(MAKE_DIRECTORY ${RIVE_DEPENDENCIES_DIR})
file(MAKE_DIRECTORY ${RIVE_NATIVE_BUILD_DIR})

# Build the Rive C++ runtime and its dependencies as static .a libs.
# --no_metal: Vulkan-only build; no Metal toolchain required.
execute_process(
        COMMAND bash ${RIVE_RUNTIME_DIR}/build/build_rive.sh --file=${MAIN_CPP}/premake5_cpp_runtime.lua ninja ${CONFIG} ${RIVE_ARCH} --with_vulkan --with_rive_audio=disabled --with_rive_scripting --no_metal -- rive_cpp_runtime
        WORKING_DIRECTORY ${RIVE_NATIVE_BUILD_DIR}
        RESULT_VARIABLE SCRIPT_RESULT
        OUTPUT_VARIABLE SCRIPT_OUTPUT
        ECHO_OUTPUT_VARIABLE
)
if (NOT SCRIPT_RESULT EQUAL "0")
    message(FATAL_ERROR "Script returned with error: '${SCRIPT_OUTPUT}' - '${SCRIPT_RESULT}'")
endif ()

function(find_single_dependency_dir OUT_VAR GLOB_PATTERN DESCRIPTION)
    file(GLOB MATCHES ${GLOB_PATTERN})
    list(LENGTH MATCHES COUNT)
    if (NOT COUNT EQUAL 1)
        message(FATAL_ERROR "Expected exactly one ${DESCRIPTION} directory for '${GLOB_PATTERN}', found ${COUNT}")
    endif ()
    list(GET MATCHES 0 MATCH)
    set(${OUT_VAR} ${MATCH} PARENT_SCOPE)
endfunction()

find_single_dependency_dir(
        VULKAN_HEADERS_DIR
        "${RIVE_DEPENDENCIES_DIR}/KhronosGroup_Vulkan-Headers_*"
        "Vulkan Headers")
find_single_dependency_dir(
        VULKAN_MEMORY_ALLOCATOR_DIR
        "${RIVE_DEPENDENCIES_DIR}/GPUOpen-LibrariesAndSDKs_VulkanMemoryAllocator_*"
        "Vulkan Memory Allocator")

# Only jni.h is needed (no AWT), so resolve headers straight from JAVA_HOME
# rather than FindJNI, which insists on the AWT libraries.
if (NOT DEFINED ENV{JAVA_HOME})
    execute_process(
            COMMAND /usr/libexec/java_home
            OUTPUT_VARIABLE JAVA_HOME
            OUTPUT_STRIP_TRAILING_WHITESPACE)
else ()
    set(JAVA_HOME $ENV{JAVA_HOME})
endif ()
if (APPLE)
    set(JNI_PLATFORM_INCLUDE ${JAVA_HOME}/include/darwin)
else ()
    set(JNI_PLATFORM_INCLUDE ${JAVA_HOME}/include/linux)
endif ()
set(JNI_INCLUDE_DIRS ${JAVA_HOME}/include ${JNI_PLATFORM_INCLUDE})

add_library(rive-jvm SHARED
        # Command queue JNI bindings shared with Android (Android-only
        # sections are #ifdef __ANDROID__ guarded).
        ${MAIN_CPP}/src/bindings/bindings_command_queue.cpp
        ${MAIN_CPP}/src/bindings/bindings_render_context.cpp
        ${MAIN_CPP}/src/bindings/bindings_audio.cpp
        ${MAIN_CPP}/src/bindings/bindings_init.cpp
        # Platform-neutral helpers
        ${MAIN_CPP}/src/helpers/conversions.cpp
        ${MAIN_CPP}/src/helpers/jni_resource.cpp
        ${MAIN_CPP}/src/helpers/jni_exception_handler.cpp
        ${MAIN_CPP}/src/helpers/rive_log.cpp
        ${MAIN_CPP}/src/helpers/audio_engine.cpp
        # Vulkan render context and surfaces (offscreen only on desktop)
        ${MAIN_CPP}/src/models/render_context_vulkan.cpp
        ${MAIN_CPP}/src/models/render_surface_vulkan.cpp
        ${MAIN_CPP}/src/models/render_surface.cpp
        # Desktop-specific replacements
        src/desktop_general.cpp
        src/desktop_image_decode.cpp
        # Vulkan bootstrap glue from the Rive runtime
        ${RIVE_RUNTIME_DIR}/renderer/rive_vk_bootstrap/src/vulkan_debug_callbacks.cpp
        ${RIVE_RUNTIME_DIR}/renderer/rive_vk_bootstrap/src/vulkan_device.cpp
        ${RIVE_RUNTIME_DIR}/renderer/rive_vk_bootstrap/src/vulkan_frame_synchronizer.cpp
        ${RIVE_RUNTIME_DIR}/renderer/rive_vk_bootstrap/src/vulkan_headless_frame_synchronizer.cpp
        ${RIVE_RUNTIME_DIR}/renderer/rive_vk_bootstrap/src/vulkan_instance.cpp
        ${RIVE_RUNTIME_DIR}/renderer/rive_vk_bootstrap/src/vulkan_library.cpp
        ${RIVE_RUNTIME_DIR}/renderer/rive_vk_bootstrap/src/vulkan_swapchain.cpp
)

target_compile_options(rive-jvm PRIVATE -fno-exceptions -fno-rtti)

target_compile_definitions(rive-jvm PRIVATE
        RIVE_VULKAN
        VK_NO_PROTOTYPES
        VMA_STATIC_VULKAN_FUNCTIONS=0
        VMA_DYNAMIC_VULKAN_FUNCTIONS=1
        WITH_RIVE_TEXT
        WITH_RIVE_LAYOUT
        # Keep JNI translation units ABI-compatible with the static runtime.
        WITH_RIVE_SCRIPTING
)

target_include_directories(rive-jvm PRIVATE
        ${MAIN_CPP}/include
        ${RIVE_RUNTIME_DIR}/include
        ${RIVE_RUNTIME_DIR}/renderer/include
        ${RIVE_RUNTIME_DIR}/decoders/include
        ${RIVE_RUNTIME_DIR}/renderer/src
        ${RIVE_RUNTIME_DIR}/renderer/rive_vk_bootstrap/include
        ${VULKAN_HEADERS_DIR}/include
        ${VULKAN_MEMORY_ALLOCATOR_DIR}/include
        ${JNI_INCLUDE_DIRS}
)

# Link the static libraries built by Premake
foreach (X IN ITEMS rive rive_pls_renderer rive_decoders libpng libjpeg libwebp zlib rive_harfbuzz rive_sheenbidi rive_yoga luau_vm)
    set(LIB_TARGET "${X}-lib")
    add_library(${LIB_TARGET} STATIC IMPORTED)
    set_target_properties(${LIB_TARGET}
            PROPERTIES IMPORTED_LOCATION
            ${RIVE_PREMAKE_OUT_DIR}/lib${X}.a
    )
    target_link_libraries(rive-jvm ${LIB_TARGET})
endforeach ()

if (APPLE)
    # CoreText for harfbuzz's coretext shaper; ImageIO/CoreGraphics for
    # rive_decoders' CG fallback path.
    target_link_libraries(rive-jvm
            "-framework CoreFoundation"
            "-framework CoreText"
            "-framework CoreGraphics"
            "-framework ImageIO"
    )
endif ()
