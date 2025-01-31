#!/bin/bash
set -ex

RUNTIME_ANDROID_HOME=$PWD/../../../..

# Android Studio build things.
rm -rf "$RUNTIME_ANDROID_HOME"/kotlin/build/
rm -rf "$RUNTIME_ANDROID_HOME"/kotlin/.cxx/
rm -rf "$RUNTIME_ANDROID_HOME"/kotlin/src/main/cpp/build

# librive_cpp_runtime.
rm -rf ./out
