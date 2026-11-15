#!/usr/bin/env bash
# Installs the release APK, launches it straight into John 1 and checks that the chapter is shown as flowing USFM text,
# then searches "jhn1" and checks the search screen stays responsive.
set -u

APP_ID="com.github.danzuep.yeshua.android"
APP_NAMESPACE="com.github.danzuep.yeshua.android"

# Taps the center of the node whose content-desc contains $1 in ui.xml; returns 1 if there is none.
tap_desc() {
  local xy
  xy=$(DESC="$1" python3 - << 'PY'
import os, re
xml = open("ui.xml", encoding="utf-8").read()
node = re.search(r'<node[^>]*content-desc="[^"]*' + os.environ["DESC"] + r'[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if node:
    x1, y1, x2, y2 = map(int, node.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
PY
)
  [ -n "$xy" ] && adb shell input tap $xy
}

dump_ui() {
  rm -f ui.xml
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml > /dev/null 2>&1
}

# Opens /sdcard/Download/$1 in the app through a MediaStore content URI, like a file manager would (apps cannot read file:// URIs).
open_usfm() {
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Download/$1" > /dev/null
  local id=""
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    id=$(adb shell content query --uri content://media/external/file --projection _id --where "_display_name='$1'" | grep -o '_id=[0-9]*' | head -n 1 | cut -d= -f2)
    [ -n "$id" ] && break
    sleep 1
  done
  echo "MediaStore id of $1: ${id:-none}"
  adb shell am start -W -a android.intent.action.VIEW --grant-read-uri-permission -n "$APP_ID/$APP_NAMESPACE.translations.TranslationsActivity" -d "content://media/external/file/$id" > /dev/null
}

adb install app/build/outputs/apk/release/app-release.apk
adb logcat -c
# Open Genesis first (importing the bundled translation takes a while), then jump to another book. A page must
# never be left on the loading spinner after the jump.
adb shell am start -W --activity-single-top -n "$APP_ID/$APP_NAMESPACE.LauncherActivity" --ei "$APP_NAMESPACE.KEY_OPEN_BOOK" 0
sleep 20
# Open straight to the requested book (0-based index, default 42 = John) instead of navigating the UI.
adb shell am start -W --activity-single-top -n "$APP_ID/$APP_NAMESPACE.LauncherActivity" --ei "$APP_NAMESPACE.KEY_OPEN_BOOK" "${SMOKE_BOOK_INDEX:-42}"

# The bundled translation is imported on first launch, so poll for the text instead of sleeping a fixed time.
found=0
for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
  dump_ui
  if grep -q "In the beginning was the Word" ui.xml 2> /dev/null; then
    found=1
    break
  fi
  sleep 2
done

status=0
if [ "$found" -ne 1 ]; then
  echo "FAIL: John 1 text was not shown"
  # Nothing else can work without the chapter, so stop here instead of waiting through the remaining steps.
  adb logcat -d > logcat.txt
  adb exec-out screencap -p > screen.png
  grep -A 20 "FATAL EXCEPTION" logcat.txt | head -40
  exit 1
fi
# The paragraph / heading markers must never leak into the UI.
if grep -qP '\x{2028}|\x{2029}' ui.xml 2> /dev/null; then
  echo "FAIL: USFM structure markers leaked into the UI"
  status=1
fi
# The chapter flows in paragraphs rather than one row per verse, so verse 5 shares a view with verse 1.
if ! grep -q "The light shines in the darkness" ui.xml 2> /dev/null; then
  echo "FAIL: expected flowing paragraph text (John 1:1-5 not in one paragraph)"
  status=1
fi
# Capture the ruby added to verse 2: the reading is drawn above its base, while the text stays plain.
adb exec-out screencap -p > screen-ruby.png
if ! grep -q "Ruby" ui.xml 2> /dev/null; then
  echo "FAIL: ruby base text was not shown"
  status=1
fi
# Bookmark the first verse (tap it, then tap Bookmark) and capture the screen: the heart is stacked under the verse number.
verse_xy=$(python3 - << 'PY'
import re
xml = open("ui.xml", encoding="utf-8").read()
node = re.search(r'<node[^>]*text="[^"]*In the beginning was the Word[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if node:
    x1, y1, x2, y2 = map(int, node.groups())
    print((x1 + x2) // 2, y1 + 40)
PY
)
if [ -n "$verse_xy" ]; then
  adb shell input tap $verse_xy
  sleep 2
  dump_ui
  if tap_desc "Bookmark"; then
    sleep 2
    adb exec-out screencap -p > screen-bookmarked.png
  else
    echo "FAIL: could not tap the Bookmark icon"
    status=1
  fi
  adb shell input keyevent KEYCODE_BACK
  sleep 1
else
  echo "FAIL: could not find a verse to tap"
  status=1
fi
# Scroll to the start of John 2 and capture it: the chapter starts inline with a bold "¶2" instead of a heading and break.
chapter_found=0
# The back press above can leave the reader, so reopen John first.
adb shell am start -W --activity-single-top -n "$APP_ID/$APP_NAMESPACE.LauncherActivity" --ei "$APP_NAMESPACE.KEY_OPEN_BOOK" "${SMOKE_BOOK_INDEX:-42}" > /dev/null
sleep 5
# The emulator screen is only 320x640, so scroll relative to its size rather than with fixed coordinates.
read -r width height < <(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -n 1 | tr 'x' ' ')
for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do
  adb shell input swipe $((width / 2)) $((height * 3 / 4)) $((width / 2)) $((height / 4)) 300
  sleep 1
  dump_ui
  if grep -q "¶2" ui.xml 2> /dev/null; then
    chapter_found=1
    break
  fi
done
adb exec-out screencap -p > screen-chapter.png
if [ "$chapter_found" -ne 1 ]; then
  echo "FAIL: John 2 did not start with the chapter mark"
  status=1
fi
# The copyright notice is stored when the translation is imported, so it must show with no connection.
adb shell am start -W --activity-single-top -n "$APP_ID/$APP_NAMESPACE.LauncherActivity" --ei "$APP_NAMESPACE.KEY_OPEN_BOOK" "${SMOKE_BOOK_INDEX:-42}" > /dev/null
sleep 5
adb shell svc wifi disable
adb shell svc data disable
# The toolbar hides while scrolling down, so scroll back up to show it.
for _ in 1 2 3; do
  adb shell input swipe $((width / 2)) $((height / 4)) $((width / 2)) $((height * 3 / 4)) 200
done
sleep 1
dump_ui
cp ui.xml ui-copyright.xml
adb exec-out screencap -p > screen-copyright.png
if tap_desc "Copyright"; then
  sleep 3
  dump_ui
  adb exec-out screencap -p > screen-copyright.png
  cp ui.xml ui-copyright.xml
  if ! grep -q 'engwebp' ui-copyright.xml 2> /dev/null; then
    echo "FAIL: the copyright notice was not shown in the app"
    status=1
  fi
  adb shell input keyevent KEYCODE_BACK
  sleep 1
else
  echo "FAIL: could not tap the Copyright icon"
  status=1
fi
adb shell svc wifi enable
adb shell svc data enable

# Opening a .usfm file from another app (here, a VIEW intent with no MIME type) must offer to import it.
printf '\\id GEN\n\\h Smoke\n\\c 1\n\\p\n\\v 1 Smoke verse\n' > smoke.usfm
adb push smoke.usfm /sdcard/Download/smoke.usfm > /dev/null
open_usfm smoke.usfm
sleep 3
dump_ui
cp ui.xml ui-usfm.xml
adb exec-out screencap -p > screen-usfm.png
if ! grep -q "Abbreviation, e.g. KJV" ui-usfm.xml 2> /dev/null; then
  echo "FAIL: opening a .usfm file did not show the import dialog"
  status=1
fi
adb shell input keyevent KEYCODE_BACK
sleep 1

# A file that names its translation and language (\\id GEN EN_ULT en_English_ltr) is installed without the dialog.
printf '\\id GEN EN_ULT en_English_ltr\n\\h Smoke\n\\c 1\n\\p\n\\v 1 Smoke verse\n' > direct.usfm
adb push direct.usfm /sdcard/Download/direct.usfm > /dev/null
open_usfm direct.usfm
sleep 6
dump_ui
cp ui.xml ui-usfm-direct.xml
adb exec-out screencap -p > screen-usfm-direct.png
if grep -q "Abbreviation, e.g. KJV" ui-usfm-direct.xml 2> /dev/null; then
  echo "FAIL: a .usfm file that names its translation still showed the import dialog"
  status=1
fi
if ! grep -q "ULT" ui-usfm-direct.xml 2> /dev/null; then
  echo "FAIL: the translation from the .usfm file was not installed"
  status=1
fi
adb shell input keyevent KEYCODE_BACK
sleep 1

# Regression test: submitting "jhn1" used to relaunch SearchActivity in an endless loop, freezing the screen after the results showed.
adb shell am start -W -n "$APP_ID/$APP_NAMESPACE.search.SearchActivity" > /dev/null
sleep 3
adb shell input text jhn1
# Instant search waits for a typing pause before running.
sleep 5
adb shell input keyevent KEYCODE_ENTER
sleep 8
dump_ui
cp ui.xml ui-search.xml 2> /dev/null
if ! grep -q "In the beginning was the Word" ui-search.xml 2> /dev/null; then
  echo "FAIL: searching jhn1 did not show John 1 (or the UI dump timed out because the UI is frozen)"
  status=1
fi

# The clear button must still work, which fails if the main thread is stuck.
clear_xy=$(python3 - << 'PY'
import re
xml = open("ui-search.xml", encoding="utf-8").read()
node = re.search(r'<node[^>]*content-desc="Clear query"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if node:
    x1, y1, x2, y2 = map(int, node.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
PY
)
if [ -z "$clear_xy" ]; then
  echo "FAIL: no clear button in the search screen"
  status=1
else
  adb shell input tap $clear_xy
  sleep 3
  dump_ui
  if grep -q "In the beginning was the Word" ui.xml 2> /dev/null; then
    echo "FAIL: the clear button did not clear the search results"
    status=1
  fi
fi

adb shell input keyevent KEYCODE_BACK
sleep 2
adb shell input keyevent KEYCODE_BACK
sleep 3
if adb shell dumpsys activity activities | grep -E "mResumedActivity|ResumedActivity" | grep -q "SearchActivity"; then
  echo "FAIL: back did not leave the search screen"
  status=1
fi

adb logcat -d > logcat.txt
adb exec-out screencap -p > screen.png

# SearchActivity should be started once by us, plus at most one self-relaunch when the query is submitted.
search_starts=$(grep -c "START u0.*cmp=$APP_ID/$APP_NAMESPACE.search.SearchActivity" logcat.txt)
if [ "$search_starts" -gt 3 ]; then
  echo "FAIL: SearchActivity was started $search_starts times, it keeps relaunching itself"
  status=1
fi
if grep -qE "ANR in $APP_ID|Application Not Responding" logcat.txt; then
  echo "FAIL: the app is not responding"
  status=1
fi
if grep -q "FATAL EXCEPTION" logcat.txt; then
  echo "FAIL: the app crashed"
  grep -A 20 "FATAL EXCEPTION" logcat.txt | head -40
  status=1
fi
[ "$status" -eq 0 ] && echo "PASS"
exit "$status"
