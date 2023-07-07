#!/bin/bash

set -e
./build.rive.for.sh -c -a x86 
./build.rive.for.sh -c -a x86_64
./build.rive.for.sh -c -a arm64-v8a
./build.rive.for.sh -c -a armeabi-v7a