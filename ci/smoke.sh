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
ui tap "Customize"; sleep 5
for n in Kalboob Kitty Mochi Kalboob; do ui tap "$n"; sleep 3; done
ui tap "Time"; sleep 1; ui tap "Pretend charging"; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 10; shot 37_home_after_changes
adb shell input tap 540 1600; sleep 3
adb shell am force-stop $PKG; sleep 10; shot 38_after_force_stop
logs _w

echo "== launch"
adb shell am start -W -n $PKG/app.pulsify.MainActivity > $OUT/launch.txt 2>&1
sleep 12; shot 01_home
adb shell dumpsys gfxinfo $PKG reset > /dev/null

echo "== studio"
ui tap "Customize"; sleep 6; shot 02_studio
ui tap "Hide controls"; sleep 3
# Tap the creature, hold it, swipe across it.
adb shell input tap 540 1700; sleep 2; shot 03_tap
adb shell input swipe 540 1650 545 1655 1500; sleep 2; shot 04_hold
adb shell input swipe 150 1600 950 1600 120; sleep 3
adb shell input swipe 900 600 902 602 1200; sleep 5; shot 05_sky_hold
ui tap "Show controls"; sleep 2
for t in Look World Tricks Time Sound; do ui tap "$t"; sleep 2; shot "06_tab_$t"; done
ui tap "Time"; sleep 1; ui tap "Pretend charging"; sleep 1; ui tap "Hide controls"; sleep 6; shot 07_charging
ui tap "Show controls"; sleep 1; ui tap "Pretend charging"; ui tap "Pretend low battery"; ui tap "Hide controls"; sleep 5; shot 08_low
ui tap "Show controls"; sleep 1; ui tap "Pretend low battery"; ui tap "Hide controls"; sleep 6; shot 09_night
ui tap "Show controls"; sleep 1; ui tap "Sound"; ui tap "Demo beat"; ui tap "Hide controls"; sleep 8; shot 10_music
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
ui tap "Kitty"; sleep 4; ui tap "Hide controls"; sleep 3
adb shell input swipe 200 1400 900 1500 1500; sleep 1
adb shell input swipe 900 1500 860 1520 1800; sleep 3; shot 40_kitty_laser
adb shell input swipe 540 1700 545 1705 1800; sleep 2; shot 41_kitty_purr

echo "== kiwi"
ui tap "Show controls"; sleep 1; ui tap "Look"; sleep 1; ui tap "Kiwi"; sleep 4
ui tap "Tricks"; sleep 2; shot 50_kiwi_tricks
for t in "Trick: Squawk!" "Trick: Say something" "Trick: Fly a lap" "Trick: Upside-down" "Trick: Headbang" "Trick: Give a seed"; do ui tap "$t"; sleep 3; done
shot 51_kiwi_after_tricks
ui tap "Kiwi word"; sleep 1; adb shell input text "Hello%sDr"; sleep 1; ui tap "Teach"; sleep 1; shot 52_kiwi_talks
ui tap "Hide controls"; sleep 2
adb shell input tap 540 1500; sleep 1; adb shell input swipe 200 900 205 905 1200; sleep 4; shot 53_kiwi_seed
for i in 1 2 3 4; do adb shell input tap 900 600; sleep 0.3; done; sleep 3; shot 54_kiwi_copy
adb shell input keyevent KEYCODE_HOME; sleep 6; shot 55_home_kiwi_live
logs _k
echo "== logs"
logs ""
echo done

echo "== portraits (clean full-screen shots of every wallpaper)"
mkdir -p $OUT/portraits
# Not charging, healthy battery: the creatures' everyday look.
adb shell dumpsys battery unplug; adb shell dumpsys battery set level 80
portrait() {  # name, extras...
  local name=$1; shift
  adb shell am force-stop $PKG; sleep 1
  adb shell am start -W -n $PKG/app.pulsify.MainActivity --ei portrait 1 "$@" > /dev/null 2>&1
  sleep 3
  for t in 1 2 3; do sleep 4; timeout 30 adb exec-out screencap -p > "$OUT/portraits/${name}_$t.png" 2>/dev/null || true; done
}
portrait mochi    --ei char 1 --ei env 1 --ei coat 0
portrait kalboob  --ei char 2 --ei env 0 --ei coat 0
portrait kitty    --ei char 3 --ei env 0 --ei coat 0
portrait kiwi     --ei char 4 --ei env 0 --ei coat 0
portrait glowy    --ei char 5 --ei env 1 --ei coat 0
portrait gromble  --ei char 6 --ei env 1 --ei coat 0
portrait c7 --ei char 7 --ei env 0 --ei coat 0
portrait c8 --ei char 8 --ei env 0 --ei coat 0
portrait c9 --ei char 9 --ei env 0 --ei coat 2
for v in 0 1 2; do portrait sky$v --ei char 0 --ei variant $v; done
for g in 1 2 3; do portrait fish$g --ei char 5 --ei env 1 --ei coat $((g+3)); done
portrait loop0 --ei video 0
portrait kalboob_night --ei char 2 --ei env 0 --es mood night
portrait kalboob_charge --ei char 2 --ei env 0 --ez charging true
logs _p
adb shell dumpsys battery reset
