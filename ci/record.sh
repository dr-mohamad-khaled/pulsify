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
# Smaller logical display so the software GPU renders faster. The engine caps each
# frame step at 50 ms, so slow frames become slow motion that we re-time to 20 fps later.
adb shell wm size 720x1600; adb shell wm density 300
adb shell dumpsys battery unplug; adb shell dumpsys battery set level 80
names=(tutankhamun nefertiti anubis horus motmot solar-queen bastet temple-guardian moon crow ringneck cockatiel budgie)
# only the looks the website shows (the demo extra flies the camera through its moves)
for i in 0 1 2 8 9 10 11; do
  n=${names[$i]}
  echo "== $i $n"
  adb shell am force-stop $PKG; sleep 1
  adb shell am start -W -n $PKG/app.pulsify.MainActivity --ei portrait 1 --ei char 14 --ei style $i --ez demo true > /dev/null 2>&1
  sleep 10
  adb shell screenrecord --time-limit 75 --bit-rate 10000000 /sdcard/$n.mp4 &
  rec=$!
  sleep 30; adb shell input tap 360 733
  sleep 40
  wait $rec
  sleep 1
  adb pull /sdcard/$n.mp4 $OUT/$n.mp4 > /dev/null 2>&1 || true
  timeout 30 adb exec-out screencap -p > "$OUT/$n.png" 2>/dev/null || true
  adb shell rm /sdcard/$n.mp4
done
adb logcat -d -b crash > $OUT/crash.txt 2>/dev/null || true
ls -la $OUT
