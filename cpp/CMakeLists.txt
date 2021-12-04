cmake_minimum_required(VERSION 3.18.1)

# Declares and names the project.
project("rive_android")

set(CMAKE_EXPORT_COMPILE_COMMANDS ON CACHE INTERNAL "") # works
# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
add_library( # Sets the name of the library.
        rive_android
        # Sets the library as a shared library.
        SHARED
        # Provides a relative path to your source file(s).
        # TODO: use a CMakeLists.txt for each subfolder?
        src/jni_refs.cpp
        src/models/jni_renderer.cpp
        src/models/radial_gradient_builder.cpp
        src/models/jni_renderer_skia.cpp
        src/models/render_path.cpp
        src/models/linear_gradient_builder.cpp
        src/models/render_paint.cpp
        src/models/dimensions_helper.cpp
        src/jni_refs.cpp
        src/bindings/bindings_file.cpp
        src/bindings/bindings_artboard.cpp
        src/bindings/bindings_state_machine_input.cpp
        src/bindings/bindings_state_machine_instance.cpp
        src/bindings/bindings_init.cpp
        src/bindings/bindings_linear_animation.cpp
        src/bindings/bindings_state_machine_input_instance.cpp
        src/bindings/bindings_linear_animation_instance.cpp
        src/bindings/bindings_renderer.cpp
        src/bindings/bindings_state_machine.cpp
        src/bindings/bindings_rive_texture_view.cpp
        src/bindings/bindings_layer_state.cpp
        src/bindings/bindings_aabb.cpp
        src/bindings/bindings_helper.cpp
        src/helpers/general.cpp
        src/helpers/thread.cpp
        src/helpers/egl_thread_state.cpp
        src/helpers/settings.cpp
        )

# -I$(LIBRIVE)/include \
#		-I$(LIBRIVE)/renderer/library/include \
#		-I${LIBRIVE}/skia/dependencies/skia/ \
#		-I${LIBRIVE}/skia/dependencies/skia/include/core \
#		-I${LIBRIVE}/skia/dependencies/skia/include/effects \
#		-I${LIBRIVE}/skia/dependencies/skia/include/gpu \
#		-I${LIBRIVE}/skia/dependencies/skia/include/config \
#		-I${LIBRIVE}/skia/renderer/include \

set(LIBRIVE ../submodules/rive-cpp)

include_directories(rive_android
        include
        ${LIBRIVE}/include
        ${LIBRIVE}/renderer/library/include
        ${LIBRIVE}/skia/renderer/include
        ${LIBRIVE}/skia/dependencies/skia/
        ${LIBRIVE}/skia/dependencies/skia/include/core
        ${LIBRIVE}/skia/dependencies/skia/include/effects
        ${LIBRIVE}/skia/dependencies/skia/include/gpu
        ${LIBRIVE}/skia/dependencies/skia/include/config
)

add_definitions(-DSK_GL)

# From: https://stackoverflow.com/questions/41892544/android-cmake-and-static-linking
add_library(rive-lib STATIC IMPORTED)
set_target_properties(rive-lib
        PROPERTIES
        IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/build/${CMAKE_ANDROID_ARCH_ABI}/librive.a
        )

add_library(rive-renderer-lib STATIC IMPORTED)
set_target_properties(rive-renderer-lib
        PROPERTIES
        IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/build/${CMAKE_ANDROID_ARCH_ABI}/librive_renderer.a
        )

add_library(skia-lib STATIC IMPORTED)
set_target_properties(skia-lib
        PROPERTIES
        IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/build/${CMAKE_ANDROID_ARCH_ABI}/libskia.a
        )

add_library(rive-skia-renderer-lib STATIC IMPORTED)
set_target_properties(rive-skia-renderer-lib
        PROPERTIES
        IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/build/${CMAKE_ANDROID_ARCH_ABI}/librive_skia_renderer.a
        )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.
find_library( # Sets the name of the path variable.
        log-lib
        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

find_library(android-lib android)
find_library(EGL-lib EGL)
find_library(GLESv3-lib GLESv3)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.
target_link_libraries( # Specifies the target library.
        rive_android

        rive-lib
        rive-renderer-lib
        skia-lib
        rive-skia-renderer-lib
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib}
        ${android-lib}
        ${EGL-lib}
        ${GLESv3-lib}
        )