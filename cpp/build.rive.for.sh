#!/bin/sh

set -e

ARCH_X86=x86
ARCH_X64=x86_64
ARCH_ARM=armeabi-v7a
ARCH_ARM64=arm64-v8a

NEEDS_CLEAN='false'

function usage
{
   echo "Usage: $0 -a arch [-c]"
   echo "\t-a Specify an architecture (i.e. '$ARCH_X86', '$ARCH_X64', '$ARCH_ARM', or '$ARCH_ARM64')"
   echo "\t-c Clean previous builds"
   exit 1 # Exit script after printing help
}

if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    HOST_TAG=linux-x86_64
elif [[ "$OSTYPE" == "darwin"* ]]; then
    HOST_TAG=darwin-x86_64
fi

if [ -z "$HOST_TAG" ]
then
   echo "Unkown host tag for OS: $OSTYPE"
   exit 1
fi

while getopts "a:c" opt
do
   case "$opt" in
      a ) ARCH_NAME="$OPTARG" ;;
      c ) NEEDS_CLEAN="true" ;;
      ? ) usage ;; # Print usage in case parameter is non-existent
   esac
done

if [ -z "$ARCH_NAME" ]
then
   echo "No architecture specified";
   usage
fi

# NDK_PATH must be set
if [[ -z ${NDK_PATH+x} ]]; then
    echo "NDK_PATH is unset, should be somewhere like /Users/<username>/Library/Android/sdk/ndk/21.0.6113669"
    exit 1
fi

# Common variables.
TOOLCHAIN=$NDK_PATH/toolchains/llvm/prebuilt/$HOST_TAG
export LIBRIVE=$PWD/../submodules/rive-cpp
export SYSROOT=$TOOLCHAIN/sysroot
export INCLUDE=$SYSROOT/usr/include
export INCLUDE_CXX=$INCLUDE/c++/v1
export CXXFLAGS="-std=c++17 -Wall -fno-exceptions -fno-rtti -Iinclude -fPIC -Oz -g"

function buildFor()
{
    pushd $LIBRIVE
    if ${NEEDS_CLEAN}; then
        # echo 'cleaning!'
        ./build.sh clean
    fi
    ./build.sh
    popd

    mkdir -p $BUILD_DIR
    if ${NEEDS_CLEAN}; then
        # echo 'cleaning!'
        make clean
    fi

    cp $LIBRIVE/build/bin/debug/librive.a $BUILD_DIR

    mkdir -p $BUILD_DIR/obj
    make -j7

    JNI_DEST=../kotlin/src/main/jniLibs/$ARCH_NAME
    mkdir -p $JNI_DEST
    cp  $BUILD_DIR/libjnirivebridge.so $JNI_DEST
    # echo "cp  $LIBCXX/libc++_shared.so $JNI_DEST"
    cp  $LIBCXX/libc++_shared.so $JNI_DEST
}

API=23

if [ "$ARCH_NAME" = "$ARCH_X86" ]; then
    echo "Strings are equal."
    echo "==== x86 ===="
    ARCH=i686
    export BUILD_DIR=$PWD/build/$ARCH_NAME
    export AR=$TOOLCHAIN/bin/$ARCH-linux-android-ar
    export CC=$TOOLCHAIN/bin/$ARCH-linux-android$API-clang
    export CXX=$TOOLCHAIN/bin/$ARCH-linux-android$API-clang++
    LIBCXX=$SYSROOT/usr/lib/$ARCH-linux-android
elif [ "$ARCH_NAME" = "$ARCH_X64" ]; then
    echo "==== x86_64 ===="
    ARCH=x86_64
    export BUILD_DIR=$PWD/build/$ARCH_NAME
    export AR=$TOOLCHAIN/bin/$ARCH-linux-android-ar
    export CXX=$TOOLCHAIN/bin/$ARCH-linux-android$API-clang++
    export CC=$TOOLCHAIN/bin/$ARCH-linux-android$API-clang
    LIBCXX=$SYSROOT/usr/lib/$ARCH-linux-androideabi
elif [ "$ARCH_NAME" = "$ARCH_ARM" ]; then
    echo "==== ARMv7 ===="
    ARCH=arm
    ARCH_PREFIX=armv7a
    export BUILD_DIR=$PWD/build/$ARCH_NAME
    export AR=$TOOLCHAIN/bin/$ARCH-linux-androideabi-ar
    export CXX=$TOOLCHAIN/bin/$ARCH_PREFIX-linux-androideabi$API-clang++
    export CC=$TOOLCHAIN/bin/$ARCH_PREFIX-linux-androideabi$API-clang
    LIBCXX=$SYSROOT/usr/lib/$ARCH-linux-androideabi
elif [ "$ARCH_NAME" = "$ARCH_ARM64" ]; then
    echo "==== ARM64 ===="
    ARCH=aarch64
    export BUILD_DIR=$PWD/build/$ARCH_NAME
    export AR=$TOOLCHAIN/bin/$ARCH-linux-android-ar
    export CXX=$TOOLCHAIN/bin/$ARCH-linux-android$API-clang++
    export CC=$TOOLCHAIN/bin/$ARCH-linux-android$API-clang
    LIBCXX=$SYSROOT/usr/lib/$ARCH-linux-android
else
   echo "Invalid architecture specified: '$ARCH_NAME'"
   usage
fi

buildFor
