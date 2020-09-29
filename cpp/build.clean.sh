#!/bin/sh

set -e

if [[ -z ${RIVE_CPP_ROOT+x} ]]; then
    echo "RIVE_CPP_ROOT is unset"
    exit 1
fi

pushd $RIVE_CPP_ROOT/build
make clean
popd
make clean
