#!/usr/bin/env bash
# Frame-by-frame capture of super wallpapers (fixed time step), encoded to smooth 30 fps videos.
set -u
PKG=app.pulsify.v3
OUT=cap-out
CAP=/sdcard/Android/data/$PKG/files/cap
mkdir -p $OUT
adb wait-for-device
adb root > /dev/null 2>&1; sleep 3; adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put global hide_error_dialogs 1
adb install -r -g pulsify-v3.apk > $OUT/install.txt 2>&1
adb shell wm size 720x1600; adb shell wm density 300
adb shell dumpsys battery unplug; adb shell dumpsys battery set level 80
# name style frames script
looks=(
  "asteroids 13 360 250:next"
  "nebula 14 330 100:next;170:tap;235:next"
  "tutankhamun 0 240 95:next;170:next"
  "ringneck 10 240 100:next;175:next"
  "anubis 2 240 95:next;170:next"
  "moon 8 240 95:next;170:next"
  "nefertiti 1 240 95:next;170:next"
  "cockatiel 11 240 100:next;175:next"
)
for spec in "${looks[@]}"; do
  read -r n i frames script <<< "$spec"
  echo "== $n (style $i, $frames frames)"
  adb shell am force-stop $PKG; sleep 1
  adb shell rm -rf $CAP
  adb shell am start -W -n $PKG/app.pulsify.MainActivity --ei portrait 1 --ei char 14 --ei style $i \
    --ei capture $frames --ei capfps 30 --es capscript "'$script'" > /dev/null 2>&1
  t0=$(date +%s)
  until adb shell ls $CAP/done > /dev/null 2>&1; do
    sleep 10
    if [ $(( $(date +%s) - t0 )) -gt 900 ]; then echo "timeout on $n"; break; fi
  done
  echo "   took $(( $(date +%s) - t0 ))s"
  rm -rf frames; mkdir frames
  adb pull $CAP/. frames > /dev/null 2>&1 || true
  ffmpeg -v error -y -framerate 30 -i frames/f%05d.jpg -vf "vflip,format=yuv420p" -c:v libx264 -preset slow -crf 17 -movflags +faststart $OUT/$n.mp4 || true
  cp frames/f00120.jpg $OUT/$n.jpg 2>/dev/null || true
  adb shell am force-stop $PKG
done
adb logcat -d -b crash > $OUT/crash.txt 2>/dev/null || true
ls -la $OUT
