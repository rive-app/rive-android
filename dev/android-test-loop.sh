#!/bin/bash

: <<'DESCRIPTION'
Features:

Runs tests N times (default 10, configurable)
Handles multiple connected devices with interactive selection
Collects artifacts on failure:

Gradle logs (test_run_*.log)
Logcat output (logcat_run_*_failed.txt)
Tombstones if device is rooted (tombstones_run_*/)
Bugreport as fallback (bugreport_run_*.zip)


Reports pass/fail statistics and success rate
Exits with non-zero code if any run fails

Usage:
./android-test-loop.sh        # Run 10 times
./android-test-loop.sh 50     # Run 50 times
./android-test-loop.sh clean  # Clean old test runs
DESCRIPTION

# Handle cleanup command
if [ "$1" == "clean" ]; then
    echo "Cleaning old test runs..."
    find . -name "test_run_*" -type d -mtime +7 -exec rm -rf {} \; 2>/dev/null
    echo "Removed test runs older than 7 days"
    exit 0
fi

# Configuration
NUM_RUNS=${1:-10} # Default to 10 runs, or pass as first argument

# Environment setup (adjust paths as needed)
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="/Users/$USER/Library/Android/sdk"
export ANDROID_NDK="/Users/$USER/Library/Android/sdk/ndk/27.2.12479018"

# Device selection
DEVICES=()
while IFS= read -r device; do
    DEVICES+=("$device")
done < <(adb devices | grep -v "List" | grep "device$" | awk '{print $1}')
DEVICE_COUNT=${#DEVICES[@]}

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "❌ No devices connected"
    exit 1
elif [ "$DEVICE_COUNT" -eq 1 ]; then
    SELECTED_DEVICE="${DEVICES[0]}"
    echo "Using device: $SELECTED_DEVICE"
else
    echo "Multiple devices found:"
    for i in "${!DEVICES[@]}"; do
        DEVICE_NAME=$(adb -s "${DEVICES[$i]}" shell getprop ro.product.model | tr -d '\r')
        echo "  $((i + 1)). ${DEVICES[$i]} - $DEVICE_NAME"
    done
    echo -n "Select device (1-$DEVICE_COUNT): "
    read -r SELECTION
    SELECTED_DEVICE="${DEVICES[$((SELECTION - 1))]}"
    echo "Using device: $SELECTED_DEVICE"
fi

# Get device ABI using selected device
DEVICE_ABI=$(adb -s "$SELECTED_DEVICE" shell getprop ro.product.cpu.abi | tr -d '\r')

# Export ANDROID_SERIAL so all adb commands use this device
export ANDROID_SERIAL="$SELECTED_DEVICE"

# Create timestamped directory for this test session
TEST_SESSION="test_run_$(date +'%Y-%m-%d_%H-%M-%S')"
mkdir -p "$TEST_SESSION"
cd "$TEST_SESSION" || exit 1
echo "Test outputs will be saved in: dev/$TEST_SESSION/"

# Counters
PASS_COUNT=0
FAIL_COUNT=0
RUNS=()

echo "Running Android tests $NUM_RUNS times..."
echo "Device ABI: $DEVICE_ABI"
echo "---"

# Clean a few things up.
../../gradlew -p ../.. clean >/dev/null 2>&1
# Clean native code once before all runs
pushd ../../kotlin/src/main/cpp || exit 1
./clean_all.sh
popd || exit 1

for i in $(seq 1 "$NUM_RUNS"); do
    echo "Run $i/$NUM_RUNS - $(date '+%Y-%m-%d %H:%M:%S')"

    # Clean before each run
    # ../../gradlew -p ../.. clean >/dev/null 2>&1

    # echo "  Force-stopping package on device..."
    # adb shell am force-stop "app.rive.runtime.example"
    echo "  Uninstalling app packages from device..."
    ../../gradlew -p ../.. uninstallAll >"test_run_${i}_uninstall.log" 2>&1

    # Clear logcat
    adb logcat -c

    # Run tests
    START_TIME=$(date +%s)
    if ../../gradlew -p ../.. \
        -PabiFilters="$DEVICE_ABI" \
        kotlin:connectedDebugAndroidTest \
        app:connectedDebugAndroidTest \
        --no-daemon \
        >"test_run_$i.log" 2>&1; then
        END_TIME=$(date +%s)
        DURATION=$((END_TIME - START_TIME))
        echo "✅ PASS (${DURATION}s)"
        PASS_COUNT=$((PASS_COUNT + 1))
        RUNS+=("PASS")
    else
        END_TIME=$(date +%s)
        DURATION=$((END_TIME - START_TIME))
        echo "❌ FAIL (${DURATION}s)"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        RUNS+=("FAIL")

        # Save logcat for failed runs
        adb logcat -d >"logcat_run_${i}_failed.log"
        echo "  Saved logcat to logcat_run_${i}_failed.log"
    fi

    echo "---"
done

# Summary
echo "Test Summary:"
echo "Total runs: $NUM_RUNS"
echo "Passed: $PASS_COUNT"
echo "Failed: $FAIL_COUNT"
echo "Success rate: $((PASS_COUNT * 100 / NUM_RUNS))%"
echo ""
echo -n "Run history: "
printf '%s ' "${RUNS[@]}"
echo ""

# Save summary to history file
echo "$(date +%Y-%m-%d_%H:%M:%S),$NUM_RUNS,$PASS_COUNT,$FAIL_COUNT,$((PASS_COUNT * 100 / NUM_RUNS))" >>../test_history.csv

# Collect artifacts if any tests failed
if [ "$FAIL_COUNT" -gt 0 ]; then
    echo ""
    echo "Collecting crash artifacts..."

    # Try to collect tombstones (only works on rooted devices)
    if adb pull /data/tombstones tombstones/ 2>/dev/null; then
        echo "✓ Tombstones collected in tombstones/"
    else
        echo "✗ Could not collect tombstones (device may not be rooted)"
        # Try bugreport as fallback
        echo "Generating bugreport instead..."
        if adb bugreport bugreport.zip 2>/dev/null; then
            echo "✓ Bugreport saved as bugreport.zip"
        else
            echo "✗ Bugreport generation also failed"
        fi
    fi

    echo ""
    echo "⚠️  Some test runs failed. Check the following for details:"
    echo "  - dev/$TEST_SESSION/test_run_*.log (gradle output)"
    echo "  - dev/$TEST_SESSION/logcat_run_*_failed.log (device logs)"
    echo "  - dev/$TEST_SESSION/tombstones/ (crash dumps, if available)"
    echo "  - dev/$TEST_SESSION/bugreport.zip (full device state, if tombstones unavailable)"
    exit 1
else
    echo ""
    echo "✅ All tests passed! Logs saved in dev/$TEST_SESSION/"
fi
