#!/usr/bin/env bash
# Runs the instrumented tests, then copies the screenshots they took (OpenFileTest) next to the reports.
# $1 optionally limits the run to one test class, e.g. io.github.danzuep.usfmbible.translations.OpenFileTest.
# The full WEB zip left by the bundle-web action supplies a real Haggai file and the whole Bible (WEBF.zip), which the tests open as another app would.
mkdir -p test-assets
unzip -o -j -q full-engwebp.zip '*HAG*' -d test-assets 2> /dev/null
for f in test-assets/*HAG*; do mv "$f" test-assets/HAG.usfm; done
cp full-engwebp.zip test-assets/WEBF.zip 2> /dev/null
filter=()
[ -n "${1:-}" ] && filter=("-Pandroid.testInstrumentationRunnerArguments.class=$1")
./gradlew connectedDebugAndroidTest -PbundledAssetsDir="$PWD/bundled-assets" -PtestAssetsDir="$PWD/test-assets" "${filter[@]}"
status=$?
mkdir -p shots
adb logcat -d -t 20000 > shots/logcat.txt
adb pull /sdcard/Download/shots ./shots > /dev/null 2>&1
exit "$status"
