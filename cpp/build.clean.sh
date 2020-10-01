#!/bin/sh

set -e

pushd $PWD/../submodules/rive-cpp/build
make clean
popd
make clean
