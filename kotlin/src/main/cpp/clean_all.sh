#!/bin/bash
set -ex

SCRIPT_HOME="$(cd "$(dirname "$0")" && pwd)"
if [ "$PWD" != "$SCRIPT_HOME" ]; then
  echo "Error: This script must be called from its own directory: $SCRIPT_HOME" >&2
  exit 1
fi

RUNTIME_ANDROID_HOME=$PWD/../../../..

if [ -z "${RIVE_RUNTIME_DIR}" ]; then
  echo "RIVE_RUNTIME_DIR is not set"
  if [ -d "${RUNTIME_ANDROID_HOME}/submodules/rive-runtime" ]; then
    export RIVE_RUNTIME_DIR="$RUNTIME_ANDROID_HOME/submodules/rive-runtime"
  else
    export RIVE_RUNTIME_DIR="$RUNTIME_ANDROID_HOME/../runtime"
  fi
else
  echo "RIVE_RUNTIME_DIR already set: $RIVE_RUNTIME_DIR"
fi

# Android Studio build things.
rm -rf "$RUNTIME_ANDROID_HOME"/kotlin/build/
rm -rf "$RUNTIME_ANDROID_HOME"/kotlin/.cxx/
rm -rf "$RUNTIME_ANDROID_HOME"/kotlin/src/main/cpp/build

# librive
rm -rf ./build
pushd "$RIVE_RUNTIME_DIR"
./build.sh clean
rm -rf ./build/android
popd

# Rive Renderer
pushd "$RIVE_RUNTIME_DIR"/skia/renderer/
./build.sh clean
rm -rf ./build/android
popd

# PLS
pushd "$RIVE_RUNTIME_DIR"/renderer
rm -rf ./out
rm -rf ./android
rm -rf ./dependencies
rm -rf ./obj
popd

# Skia
pushd "$RIVE_RUNTIME_DIR"/skia/dependencies/
rm -rf ./skia ./skia_debug
popd
