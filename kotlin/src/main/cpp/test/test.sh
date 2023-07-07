#!/bin/bash
set -ex

CONNECTED_ABI=$(adb shell getprop ro.product.cpu.abi)

if [ ! "$CONNECTED_ABI" ]; then
  echo "No devices connected?"
  exit 1
fi

if [[ "$OSTYPE" == "linux-gnu"* ]]; then
  HOST_TAG=linux-x86_64
elif [[ "$OSTYPE" == "darwin"* ]]; then
  HOST_TAG=darwin-x86_64
fi

if [[ "$OSTYPE" == "darwin"* ]]; then
  EXPECTED_NDK_VERSION=$(tr <../.ndk_version -d " \t\n\r")
else
  EXPECTED_NDK_VERSION=$(tr <../.ndk_version.bots -d " \t\n\r")
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
  echo "              - select ${EXPECTED_NDK_VERSION} in NDK (Side by Side)"
  echo "Found ${NDK_PATH}"
  exit 1
fi

# Build our Android Library with CMake
CMAKE_BUILD_TREE=${PWD}/build
LIB_OUTPUT="${PWD}/output/ninja/${CONNECTED_ABI}"

mkdir -p "$LIB_OUTPUT"
mkdir -p build

pushd build

"${ANDROID_HOME}"/cmake/3.22.1/bin/cmake \
  -H/Users/umbertosonnino/Projects/rive/packages/runtime_android/cpp \
  -DCMAKE_SYSTEM_NAME=Android \
  -DCMAKE_EXPORT_COMPILE_COMMANDS=ON \
  -DCMAKE_SYSTEM_VERSION=21 \
  -DANDROID_PLATFORM=android-21 \
  -DANDROID_ABI="${CONNECTED_ABI}" \
  -DCMAKE_ANDROID_ARCH_ABI="${CONNECTED_ABI}" \
  -DANDROID_NDK="$NDK_PATH" \
  -DCMAKE_ANDROID_NDK="$NDK_PATH" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK_PATH"/build/cmake/android.toolchain.cmake \
  -DCMAKE_MAKE_PROGRAM="${ANDROID_HOME}"/cmake/3.22.1/bin/ninja \
  -DCMAKE_LIBRARY_OUTPUT_DIRECTORY="${LIB_OUTPUT}" \
  -DCMAKE_RUNTIME_OUTPUT_DIRECTORY="${LIB_OUTPUT}" \
  -DCMAKE_BUILD_TYPE=Debug \
  -B"${CMAKE_BUILD_TREE}" \
  -GNinja \
  -DCMAKE_VERBOSE_MAKEFILE=1 \
  -DANDROID_ALLOW_UNDEFINED_SYMBOLS=ON \
  -DANDROID_CPP_FEATURES="no-exceptions no-rtti" \
  -DANDROID_STL=c++_shared

pushd "${CMAKE_BUILD_TREE}"
cmake --build .
popd

popd

TEST_BUILD=build_catch_tests
mkdir -p ${TEST_BUILD}

"${ANDROID_HOME}"/cmake/3.22.1/bin/cmake \
  -H. \
  -DCMAKE_SYSTEM_NAME=Android \
  -DCMAKE_EXPORT_COMPILE_COMMANDS=ON \
  -DCMAKE_SYSTEM_VERSION=21 \
  -DANDROID_PLATFORM=android-21 \
  -DANDROID_ABI="${CONNECTED_ABI}" \
  -DCMAKE_ANDROID_ARCH_ABI="${CONNECTED_ABI}" \
  -DANDROID_NDK="$NDK_PATH" \
  -DCMAKE_ANDROID_NDK="$NDK_PATH" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK_PATH"/build/cmake/android.toolchain.cmake \
  -DCMAKE_MAKE_PROGRAM="${ANDROID_HOME}"/cmake/3.22.1/bin/ninja \
  -DCMAKE_LIBRARY_OUTPUT_DIRECTORY="${TEST_BUILD}" \
  -DCMAKE_RUNTIME_OUTPUT_DIRECTORY="${TEST_BUILD}" \
  -DCMAKE_BUILD_TYPE=Debug \
  -B"${TEST_BUILD}" \
  -GNinja \
  -DCMAKE_VERBOSE_MAKEFILE=1 \
  -DANDROID_ALLOW_UNDEFINED_SYMBOLS=ON \
  -DANDROID_CPP_FEATURES="no-exceptions no-rtti" \
  -DANDROID_STL=c++_shared

pushd ${TEST_BUILD}
cmake --build .
popd

SYSROOT="$NDK_PATH/toolchains/llvm/prebuilt/$HOST_TAG/sysroot"
LIBCXX=

case "${CONNECTED_ABI}" in
"x86")
  echo "Lithuanian"
  LIBCXX=$SYSROOT/usr/lib/i686-linux-android
  ;;
"x86_64")
  LIBCXX=$SYSROOT/usr/lib/x86_64-linux-android
  echo "Romanian"
  ;;
"armeabi-v7a")
  LIBCXX=$SYSROOT/usr/lib/arm-linux-androideabi
  echo "Italian"
  ;;
"arm64-v8a")
  LIBCXX=$SYSROOT/usr/lib/aarch64-linux-android
  echo "Italian"
  ;;
*)
  echo "Unknown ABI"
  exit 1
  ;;
esac

adb push "${LIBCXX}"/libc++_shared.so /data/local/tmp
adb push "${LIB_OUTPUT}"/librive-android.so /data/local/tmp
adb push build_catch_tests/build_catch_tests/example_test_suite /data/local/tmp
adb push run_android_tests.sh /data/local/tmp/
adb shell "cd /data/local/tmp && ./run_android_tests.sh"
