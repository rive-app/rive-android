#!/bin/bash
set -ex

ARCH_X86=x86
ARCH_X64=x86_64
ARCH_ARM=armeabi-v7a
ARCH_ARM64=arm64-v8a

ONLY_DEPS='true'
NEEDS_CLEAN='false'
FLAGS="-flto=full -DNDEBUG"
# we default to release
CONFIG="release"

CONFIG_SKIA_REPO="https://github.com/rive-app/skia"
CONFIG_SKIA_BRANCH="rive"
CONFIG_SKIA_DIR_NAME="skia"

usage() {
    printf "Usage: %s -a arch [-c]" "$0"
    printf "\t-a Specify an architecture (i.e. '%s', '%s', '%s', '%s')", $ARCH_X86 $ARCH_X64 $ARCH_ARM $ARCH_ARM64
    printf "\t-c Clean previous builds\n"
    printf "\t-d To build for debug (which also enables logging)\n"
    printf "\t-l To enable logging\n"
    exit 1 # Exit script after printing help
}

if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    HOST_TAG=linux-x86_64
elif [[ "$OSTYPE" == "darwin"* ]]; then
    HOST_TAG=darwin-x86_64
fi

if [ -z "$HOST_TAG" ]; then
    echo "Unkown host tag for OS: $OSTYPE"
    exit 1
fi

while getopts "a:cbdl" opt; do
    case "$opt" in
    a) ARCH_NAME="$OPTARG" ;;
    c) NEEDS_CLEAN="true" ;;
    b) ONLY_DEPS="false" ;;
    d)
        CONFIG="debug"
        FLAGS="-DDEBUG -g"
        # Use full skia for debug.
        CONFIG_SKIA_REPO="https://github.com/google/skia"
        CONFIG_SKIA_BRANCH="chrome/m105"
        CONFIG_SKIA_DIR_NAME="skia_debug"
        ;;
    l)
        FLAGS="-DLOG"
        ;;
    \?) usage ;; # Print usage in case parameter is non-existent
    esac
done

if [ -z "$ARCH_NAME" ]; then
    echo "No architecture specified"
    ATTACHED=$(eval "adb shell getprop ro.product.cpu.abi")
    if [ -n "$ATTACHED" ]; then
        echo "Device attached is: $ATTACHED"
    fi
    usage
fi

if [[ "$OSTYPE" == "darwin"* ]]; then
    EXPECTED_NDK_VERSION=$(tr <.ndk_version -d " \t\n\r")
else
    EXPECTED_NDK_VERSION=$(tr <.ndk_version.bots -d " \t\n\r")
fi

# NDK_PATH must be set
if [[ -z ${NDK_PATH+x} ]]; then
    echo "NDK_PATH is unset, should be somewhere like /Users/<username>/Library/Android/sdk/ndk/${EXPECTED_NDK_VERSION}"
    exit 1
# Check NDK version
elif [[ ${NDK_PATH} != *${EXPECTED_NDK_VERSION}* ]]; then
    echo "Wrong NDK version"
    echo "Expected: /Users/<username>/Library/Android/sdk/ndk/${EXPECTED_NDK_VERSION}"
    echo "          For bot builds, googles NDK distros"
    echo "          we are currently using: https://github.com/android/ndk/wiki/Unsupported-Downloads, you should be able to get android-ndk-r25b-darwin"
    echo "          For human builds"
    echo "              - open android studio"
    echo "              - settings search for android sdk, then SDK tools"
    echo "              - check "show package details" at the bottom"
    echo "              - select 25.1.8937393 in NDK (Side by Side)"
    echo "Found ${NDK_PATH}"
    exit 1
fi

# Common variables.
TOOLCHAIN="$NDK_PATH/toolchains/llvm/prebuilt/$HOST_TAG"

if [ -z "${RIVE_RUNTIME_DIR}" ]; then
    echo "RIVE_RUNTIME_DIR is not set"
    if [ -d "$PWD/../../../../submodules/rive-cpp" ]; then
        export RIVE_RUNTIME_DIR="$PWD/../../../../submodules/rive-cpp"
    else
        export RIVE_RUNTIME_DIR="$PWD/../../../../../runtime"
    fi
else
    echo "RIVE_RUNTIME_DIR already set: ${RIVE_RUNTIME_DIR}"
fi

export SYSROOT="$TOOLCHAIN/sysroot"
export INCLUDE="$SYSROOT/usr/include"
export INCLUDE_CXX="$INCLUDE/c++/v1"
export CXXFLAGS="-std=c++17 -Wall -fno-exceptions -fno-rtti -fPIC -Oz ${FLAGS}"
# export CXXFLAGS="-std=c++17 -Wall -fno-exceptions -fno-rtti -fsanitize=hwaddress -fno-omit-frame-pointer -Iinclude -fPIC -O1 ${FLAGS}"
export AR="$TOOLCHAIN/bin/llvm-ar"

export SKIA_REPO=$CONFIG_SKIA_REPO
export SKIA_BRANCH=$CONFIG_SKIA_BRANCH
export SKIA_DIR_NAME=$CONFIG_SKIA_DIR_NAME

export COMPILE_TARGET="android_${EXPECTED_NDK_VERSION}_$ARCH_NAME"
export CACHE_NAME="rive_skia_android_$EXPECTED_NDK_VERSION"
export MAKE_SKIA_FILE="make_skia_android.sh"

API=21
SKIA_ARCH=
NCPU=$(getconf _NPROCESSORS_ONLN 2>/dev/null || sysctl -n hw.ncpu)

# Cleans the build for the specified architecture
cleanFor() {
    echo "Cleaning everything!"
    # Skia
    pushd "$RIVE_RUNTIME_DIR"/skia/dependencies/"$SKIA_DIR_NAME"
    if [ -d ./bin ]; then
        # Skia clone: let's clean the build
        bin/gn clean ./out/"$CONFIG"/"$SKIA_ARCH"
    else
        # Skia download.
        rm archive_contents
    fi
    popd
    # PLS
    pushd "$RIVE_RUNTIME_DIR"/pls/out
    make config=$CONFIG clean
    popd
    # rive_skia_renderer
    pushd "$RIVE_RUNTIME_DIR"/skia/renderer
    ./build.sh -p android."$SKIA_ARCH" clean
    popd
    # Android lib
    make clean
}

buildFor() {
    # Build skia
    pushd "$RIVE_RUNTIME_DIR"/skia/dependencies
    ./make_skia_android.sh "$SKIA_ARCH" "$CONFIG"
    popd

    # Build librive_pls_renderer.
    pushd "$RIVE_RUNTIME_DIR"/pls
    python3 -m venv build_env
    source build_env/bin/activate
    pip3 install ply
    premake5 --file=premake5_pls_renderer.lua --out=out/android/"$SKIA_ARCH"/"$CONFIG" --config="$CONFIG" --scripts="$RIVE_RUNTIME_DIR"/build --no-rive-decoders --os=android --arch="$SKIA_ARCH" gmake2
    make -C out/android/"$SKIA_ARCH"/"$CONFIG" -j20 rive_pls_renderer
    deactivate
    popd

    # Build librive_skia_renderer (internally builds librive)
    pushd "$RIVE_RUNTIME_DIR"/skia/renderer
    ./build.sh -p android."$SKIA_ARCH" "$CONFIG"
    popd

    # Cleanup our android build location.
    mkdir -p "$BUILD_DIR"

    # copy in newly built rive/skia/skia_renderer files.
    cp "$RIVE_RUNTIME_DIR"/build/android/"$SKIA_ARCH"/bin/"${CONFIG}"/librive.a "$BUILD_DIR"
    cp "$RIVE_RUNTIME_DIR"/dependencies/android/cache/"$SKIA_ARCH"/bin/"${CONFIG}"/librive_harfbuzz.a "$BUILD_DIR"
    cp "$RIVE_RUNTIME_DIR"/dependencies/android/cache/"$SKIA_ARCH"/bin/"${CONFIG}"/librive_sheenbidi.a "$BUILD_DIR"
    cp "$RIVE_RUNTIME_DIR"/dependencies/android/cache/"$SKIA_ARCH"/bin/"${CONFIG}"/librive_yoga.a "$BUILD_DIR"
    cp "$RIVE_RUNTIME_DIR"/skia/renderer/build/android/"$SKIA_ARCH"/bin/${CONFIG}/librive_skia_renderer.a "$BUILD_DIR"
    cp "$RIVE_RUNTIME_DIR/pls/out/android/"$SKIA_ARCH"/"$CONFIG"/librive_pls_renderer.a" "$BUILD_DIR"
    cp "$RIVE_RUNTIME_DIR"/skia/dependencies/"$SKIA_DIR_NAME"/out/"${CONFIG}"/"$SKIA_ARCH"/libskia.a "$BUILD_DIR"

    if ! ${ONLY_DEPS}; then
        # Skip building the library.
        echo "ONLY DEPS!"
        exit 0
    fi

    # build the android .so!
    mkdir -p "$BUILD_DIR"/obj
    make -j20

    JNI_DEST=../jniLibs/$ARCH_NAME
    mkdir -p "$JNI_DEST"
    cp "$BUILD_DIR"/libjnirivebridge.so "$JNI_DEST"
}

if [ "$ARCH_NAME" = "$ARCH_X86" ]; then
    echo "==== x86 ===="
    SKIA_ARCH=x86
    ARCH=i686
    export TARGET_ARCH="$ARCH-linux-android$API"
    export BUILD_DIR=$PWD/build/$CONFIG/$ARCH_NAME
    export CXX=$TOOLCHAIN/bin/$ARCH-linux-android$API-clang++
    export CC=$TOOLCHAIN/bin/$ARCH-linux-android$API-clang
    LIBCXX=$SYSROOT/usr/lib/$ARCH-linux-android
elif [ "$ARCH_NAME" = "$ARCH_X64" ]; then
    echo "==== x86_64 ===="
    ARCH=x86_64
    SKIA_ARCH=x64
    export TARGET_ARCH="$ARCH-linux-android$API"
    export BUILD_DIR=$PWD/build/$CONFIG/$ARCH_NAME
    export CXX=$TOOLCHAIN/bin/$ARCH-linux-android$API-clang++
    export CC=$TOOLCHAIN/bin/$ARCH-linux-android$API-clang
    LIBCXX=$SYSROOT/usr/lib/$ARCH-linux-android
elif [ "$ARCH_NAME" = "$ARCH_ARM" ]; then
    echo "==== ARMv7 ===="
    ARCH=arm
    ARCH_PREFIX=armv7a
    SKIA_ARCH=arm
    export TARGET_ARCH="$ARCH_PREFIX-linux-androideabi$API"
    export BUILD_DIR=$PWD/build/$CONFIG/$ARCH_NAME
    export CXX=$TOOLCHAIN/bin/$ARCH_PREFIX-linux-androideabi$API-clang++
    export CC=$TOOLCHAIN/bin/$ARCH_PREFIX-linux-androideabi$API-clang
    LIBCXX=$SYSROOT/usr/lib/$ARCH-linux-androideabi
elif [ "$ARCH_NAME" = "$ARCH_ARM64" ]; then
    echo "==== ARM64 ===="
    ARCH=aarch64
    SKIA_ARCH=arm64
    export TARGET_ARCH="$ARCH-linux-android$API"
    export BUILD_DIR=$PWD/build/$CONFIG/$ARCH_NAME
    export CXX=$TOOLCHAIN/bin/$ARCH-linux-android$API-clang++
    export CC=$TOOLCHAIN/bin/$ARCH-linux-android$API-clang
    LIBCXX=$SYSROOT/usr/lib/$ARCH-linux-android
else
    echo "Invalid architecture specified: '$ARCH_NAME'"
    usage
fi

if ${NEEDS_CLEAN}; then
    cleanFor
    exit 1
fi
buildFor
