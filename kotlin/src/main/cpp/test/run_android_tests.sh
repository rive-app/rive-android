#!/bin/sh
set -e

cd /data/local/tmp
export LD_LIBRARY_PATH="$PWD"
./example_test_suite
cd -
exit 0
