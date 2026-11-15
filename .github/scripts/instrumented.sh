#!/usr/bin/env bash
# Runs the instrumented tests, then copies the screenshots they took (OpenFileTest) next to the reports.
./gradlew connectedDebugAndroidTest -PbundledAssetsDir="$PWD/bundled-assets"
status=$?
adb pull /sdcard/Download/shots ./shots > /dev/null 2>&1
exit "$status"
