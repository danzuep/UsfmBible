#!/usr/bin/env bash
# Runs the instrumented tests, then copies the screenshots they took (OpenFileTest) next to the reports.
./gradlew connectedDebugAndroidTest -PbundledAssetsDir="$PWD/bundled-assets"
status=$?
mkdir -p shots
adb logcat -d -t 20000 > shots/logcat.txt
adb pull /sdcard/Download/shots ./shots > /dev/null 2>&1
exit "$status"
