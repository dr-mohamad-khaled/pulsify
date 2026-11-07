#!/usr/bin/env bash
# Pulsify emulator smoke test: scripted walk through the app, a monkey run, the live
# wallpaper preview, then crash logs, frame stats and screenshots into ci-out/.
set -u
PKG=app.pulsify.v3
OUT=ci-out
mkdir -p $OUT
shot() { adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || true; }
ui() { python3 ci/ui.py "$@" || true; }

adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r -g pulsify-v3.apk > $OUT/install.txt 2>&1
adb logcat -c

echo "== launch"
adb shell am start -W -n $PKG/app.pulsify.MainActivity > $OUT/launch.txt 2>&1
sleep 12; shot 01_home
adb shell dumpsys gfxinfo $PKG reset > /dev/null

echo "== studio"
ui tap "Open Studio"; sleep 6; shot 02_studio
ui tap "Hide"; sleep 3
# Tap the creature, hold it, swipe across it.
adb shell input tap 540 1700; sleep 2; shot 03_tap
adb shell input swipe 540 1650 545 1655 1500; sleep 2; shot 04_hold
adb shell input swipe 150 1600 950 1600 120; sleep 3
adb shell input swipe 900 600 902 602 1200; sleep 5; shot 05_sky_hold
ui tap "Show controls"; sleep 2
for t in Look Tricks Mood Music; do ui tap "$t"; sleep 2; shot "06_tab_$t"; done
ui tap "Mood"; sleep 1; ui switch "Pretend it's charging"; sleep 1; ui tap "Hide"; sleep 6; shot 07_charging
ui tap "Show controls"; sleep 1; ui switch "Pretend it's charging"; ui switch "Pretend battery is low"; ui tap "Hide"; sleep 5; shot 08_low
ui tap "Show controls"; sleep 1; ui switch "Pretend battery is low"; ui tap "Night"; ui tap "Hide"; sleep 6; shot 09_night
ui tap "Show controls"; sleep 1; ui tap "Auto"; ui tap "Music"; ui switch "Demo groove"; ui tap "Hide"; sleep 8; shot 10_music
adb shell dumpsys gfxinfo $PKG > $OUT/gfxinfo_studio.txt

echo "== every creature and environment"
ui tap "Show controls"; sleep 1; ui tap "Look"; sleep 1
ui all_creatures $OUT

adb shell input keyevent KEYCODE_BACK; sleep 3; shot 20_home_again

echo "== monkey"
adb shell monkey -p $PKG --pct-syskeys 0 --pct-appswitch 0 --throttle 200 -s 7 -v 1500 > $OUT/monkey.txt 2>&1 || true
sleep 3; shot 21_after_monkey

echo "== live wallpaper"
adb shell am force-stop $PKG
adb shell am start -a android.service.wallpaper.CHANGE_LIVE_WALLPAPER \
  --ecn android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT $PKG/app.pulsify.wallpaper.PulsifyWallpaperService > $OUT/wallpaper.txt 2>&1
sleep 12; shot 30_wallpaper_preview
adb shell input tap 540 1700; sleep 2
adb shell input swipe 540 1650 545 1655 1500; sleep 3; shot 31_wallpaper_touch
adb shell dumpsys gfxinfo $PKG > $OUT/gfxinfo_wallpaper.txt
adb shell dumpsys meminfo $PKG > $OUT/meminfo.txt

echo "== logs"
adb logcat -d -b crash > $OUT/crash.txt
adb logcat -d '*:E' > $OUT/errors.txt
adb logcat -d -s Pulsify:V AndroidRuntime:E libEGL:V OpenGLRenderer:W > $OUT/pulsify.txt
grep -c "FATAL EXCEPTION\|Fatal signal" $OUT/crash.txt > $OUT/crash_count.txt || true
echo done
