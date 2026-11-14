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
echo "== home-screen friends: open, pin a widget, wake it, swipe"
adb shell input swipe 540 1700 540 900 400; sleep 2
ui tap "Home-screen friends"; sleep 6; shot 60_friends
adb shell input keyevent KEYCODE_BACK; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 4
for c in 4 1; do
  adb shell am start -n $PKG/app.pulsify.MainActivity --ei pin $c > /dev/null 2>&1; sleep 5; shot 62_pin_dialog_$c
  ui tap "Add to home screen"; ui tap "Add automatically"; ui tap "ADD AUTOMATICALLY"; sleep 3
  adb shell input keyevent KEYCODE_HOME; sleep 2
done
timeout 30 adb shell dumpsys appwidget | grep -i -B2 -A6 pulsify > $OUT/appwidget.txt 2>&1
adb shell input keyevent KEYCODE_HOME; sleep 6; shot 63_home_widgets
timeout 30 adb shell dumpsys wallpaper | grep -i -m2 "pulsify" > $OUT/wallpaper_at_widgets.txt
ui tapid ":id/root" $OUT/widgets.txt; sleep 2; shot 64_widgets_woken
sleep 3; shot 65_widgets_later
adb shell input swipe 950 1200 100 1200 250; sleep 1; shot 66_page2
adb shell input swipe 100 1200 950 1200 250; sleep 0.6; shot 67_back_a; sleep 0.8; shot 68_back_b; sleep 1.2; shot 69_back_c
sleep 8; shot 70_play_a; sleep 2; shot 71_play_b; sleep 2; shot 72_play_c
ui tapid ":id/root"; sleep 0.8; shot 73_poke
adb shell am start -n $PKG/app.pulsify.MainActivity > /dev/null 2>&1; sleep 3
adb shell input keyevent KEYCODE_BACK; sleep 3
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
portrait c7 --ei char 7 --ei env 0 --ei coat 0
portrait loopswitch --ei video 0 --ei video2 8
# Home-screen friends playground: many shots over ~24 s to catch swipe reactions and games.
friends() {  # name, picks
  local name=$1; shift
  adb shell am force-stop $PKG; sleep 1
  adb shell am start -W -n $PKG/app.pulsify.MainActivity --ei portrait 1 --ei char 1 --ei env 1 --es friends "$1" > /dev/null 2>&1
  for t in 01 02 03 04 05 06 07 08 09 10 11 12 13 14; do sleep 1.2; timeout 30 adb exec-out screencap -p > "$OUT/portraits/${name}_$t.png" 2>/dev/null || true; done
}
friends fa "1,4,7"
friends fb "8,2,9"
friends fc "3,6,5"
logs _p
adb shell dumpsys battery reset
