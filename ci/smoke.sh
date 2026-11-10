#!/usr/bin/env bash
# Pulsify emulator smoke test: scripted walk through the app, a monkey run, the live
# wallpaper preview, then crash logs, frame stats and screenshots into ci-out/.
set -u
PKG=app.pulsify.v3
OUT=ci-out
mkdir -p $OUT
shot() { timeout 30 adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || true; }
ui() { timeout 60 python3 ci/ui.py "$@" || true; }
logs() {  # $1 = suffix; safe to call many times
  timeout 60 adb logcat -d -b crash > $OUT/crash$1.txt 2>/dev/null
  timeout 60 adb logcat -d '*:E' > $OUT/errors$1.txt 2>/dev/null
  timeout 60 adb logcat -d -s Pulsify:V AndroidRuntime:E libEGL:V > $OUT/pulsify$1.txt 2>/dev/null
  grep -c "FATAL EXCEPTION\|Fatal signal" $OUT/crash$1.txt > $OUT/crash_count.txt 2>/dev/null || true
  timeout 30 adb shell dumpsys activity exit-info $PKG > $OUT/exit_info$1.txt 2>/dev/null
  timeout 60 adb shell 'cat /data/anr/* 2>/dev/null' | head -600 > $OUT/anr$1.txt
  timeout 30 adb shell run-as $PKG cat files/last_crash.txt > $OUT/app_crash$1.txt 2>/dev/null || true
  timeout 30 adb shell run-as $PKG cat files/gl_notes.txt > $OUT/gl_notes$1.txt 2>/dev/null || true
}

adb wait-for-device
adb root > /dev/null 2>&1; sleep 3; adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r -g pulsify-v3.apk > $OUT/install.txt 2>&1
adb logcat -c

echo "== set as live wallpaper for real, then live with it"
adb shell am start -a android.service.wallpaper.CHANGE_LIVE_WALLPAPER \
  --ecn android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT $PKG/app.pulsify.wallpaper.PulsifyWallpaperService > $OUT/wallpaper.txt 2>&1
sleep 12; shot 30_wallpaper_preview
ui tap "Set wallpaper"; sleep 3; shot 32_set_dialog
ui tap "Home and lock screens" ; ui tap "Home screen and lock screen"; sleep 8
timeout 30 adb shell dumpsys wallpaper | grep -i -m3 "pulsify\|component" > $OUT/wallpaper_set.txt
adb shell input keyevent KEYCODE_HOME; sleep 10; shot 33_home_live
adb shell input tap 540 1600; sleep 2; adb shell input swipe 540 1650 545 1655 1500; sleep 2; shot 34_home_touch
adb shell input swipe 900 1200 150 1200 300; sleep 3; adb shell input swipe 150 1200 900 1200 300; sleep 3
adb shell input keyevent KEYCODE_SLEEP; sleep 5; adb shell input keyevent KEYCODE_WAKEUP; sleep 3
adb shell wm dismiss-keyguard; sleep 5; shot 35_after_unlock
echo "== change things in the app while the wallpaper is live"
adb shell am start -n $PKG/app.pulsify.MainActivity > /dev/null 2>&1; sleep 8; shot 36_app_with_live
ui tap "Open Studio"; sleep 5
for n in Kalboob Kitty Mochi Kalboob; do ui tap "$n"; sleep 3; done
ui tap "Mood"; sleep 1; ui switch "Pretend it's charging"; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 10; shot 37_home_after_changes
adb shell input tap 540 1600; sleep 3
adb shell am force-stop $PKG; sleep 10; shot 38_after_force_stop
logs _w

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
timeout 60 adb shell dumpsys gfxinfo $PKG > $OUT/gfxinfo_studio.txt

echo "== every creature and environment"
ui tap "Show controls"; sleep 1; ui tap "Look"; sleep 1
timeout 600 python3 ci/ui.py all_creatures $OUT || true
logs _a

adb shell input keyevent KEYCODE_BACK; sleep 3; shot 20_home_again

echo "== monkey"
timeout 300 adb shell monkey -p $PKG --pct-syskeys 0 --pct-appswitch 0 --throttle 250 -s 7 -v 400 > $OUT/monkey.txt 2>&1 || true
sleep 3; shot 21_after_monkey

echo "== kitty laser dot"
adb shell am start -n $PKG/app.pulsify.MainActivity > /dev/null 2>&1; sleep 6
ui tap "Kitty"; sleep 4; ui tap "Hide"; sleep 3
adb shell input swipe 200 1400 900 1500 1500; sleep 1
adb shell input swipe 900 1500 860 1520 1800; sleep 3; shot 40_kitty_laser
adb shell input swipe 540 1700 545 1705 1800; sleep 2; shot 41_kitty_purr

echo "== logs"
logs ""
echo done
