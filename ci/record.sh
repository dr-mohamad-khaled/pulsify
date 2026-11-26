#!/usr/bin/env bash
# Records a short clip of every super wallpaper (statue looks) for the website.
set -u
PKG=app.pulsify.v3
OUT=rec-out
mkdir -p $OUT
adb wait-for-device
adb root > /dev/null 2>&1; sleep 3; adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r -g pulsify-v3.apk > $OUT/install.txt 2>&1
adb shell dumpsys battery unplug; adb shell dumpsys battery set level 80
names=(tutankhamun nefertiti anubis horus motmot solar-queen bastet temple-guardian moon crow ringneck cockatiel budgie)
for i in "${!names[@]}"; do
  n=${names[$i]}
  echo "== $i $n"
  adb shell am force-stop $PKG; sleep 1
  adb shell am start -W -n $PKG/app.pulsify.MainActivity --ei portrait 1 --ei char 14 --ei style $i > /dev/null 2>&1
  sleep 10
  adb shell screenrecord --time-limit 16 --size 720x1600 --bit-rate 8000000 /sdcard/$n.mp4 &
  rec=$!
  sleep 3; adb shell input swipe 900 1200 150 1200 450
  sleep 3; adb shell input tap 540 1100
  sleep 2; adb shell input swipe 150 1300 950 1300 450
  sleep 3; adb shell input swipe 540 1700 545 600 500
  wait $rec
  sleep 1
  adb pull /sdcard/$n.mp4 $OUT/$n.mp4 > /dev/null 2>&1 || true
  timeout 30 adb exec-out screencap -p > "$OUT/$n.png" 2>/dev/null || true
  adb shell rm /sdcard/$n.mp4
done
adb logcat -d -b crash > $OUT/crash.txt 2>/dev/null || true
ls -la $OUT
