#!/usr/bin/env bash
# CI: WorldPulse im Emulator starten, Kartendaten laden lassen, Screenshots aller Ansichten (adb screencap).
set -u
OUT=${1:-../shots}
mkdir -p "$OUT"
PKG=de.worldpulse.app
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell wm size 1080x2340
adb shell wm density 420
adb shell settings put system screen_off_timeout 1800000
adb shell am start -n $PKG/.MainActivity
for i in $(seq 1 120); do
  if adb shell ls /sdcard/Android/data/$PKG/files/tiles/ 2>/dev/null | grep -qx "heilbronn.pmtiles"; then echo "Kartendaten da nach $((i*5)) s"; break; fi
  sleep 5
done
shot() { # name tab [cam]
  adb shell am force-stop $PKG
  if [ -n "${3:-}" ]; then adb shell am start -n $PKG/.MainActivity --es tab "$2" --es cam "$3"; else adb shell am start -n $PKG/.MainActivity --es tab "$2"; fi
  sleep 18
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "Screenshot $1: $(stat -c %s "$OUT/$1.png") Bytes"
}
shot 1_welt KARTE 30,15,1.3
shot 2_europa KARTE 49.5,14,3.8
shot 3_bawue KARTE 48.9,9.3,7.5
shot 4_heilbronn KARTE 49.15,9.21,12
shot 5_newsroom NEWSROOM
shot 6_analyse ANALYSE
