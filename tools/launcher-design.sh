#!/usr/bin/env bash
# Design loop for PhoneSaar Launcher: push a design.json and see it on the phone at once.
# Usage: tools/launcher-design.sh [-s serial] <push|watch|screenshot|preset|photo|grant|home|reload> [arg]
PKG=dev.pallav.phonesaar.launcher
REMOTE=/sdcard/Android/data/$PKG/files/design.json
ACTION=$PKG.RELOAD

ADB=${ADB:-$(command -v adb || true)}
[ -z "$ADB" ] && [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ] && ADB="$HOME/Library/Android/sdk/platform-tools/adb"
[ -z "$ADB" ] && [ -n "$ANDROID_HOME" ] && ADB="$ANDROID_HOME/platform-tools/adb"
[ -z "$ADB" ] && { echo "adb not found; set ADB or ANDROID_HOME" >&2; exit 1; }

SERIAL=${ANDROID_SERIAL:-}
if [ "$1" = "-s" ]; then SERIAL=$2; shift 2; fi
adb() { if [ -n "$SERIAL" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi; }

reload() { adb shell am broadcast -a "$ACTION" -p "$PKG" "$@" >/dev/null; }

push() {
  [ -f "$1" ] || { echo "no such file: $1" >&2; return 1; }
  # Android 11+ blocks adb push into Android/data, so the app's shell-only content provider takes the file first.
  out=$(adb shell "content write --uri content://$PKG.design/design.json" < "$1" 2>&1)
  if [ -z "$out" ] || ! echo "$out" | grep -qiE "error|exception"; then
    reload; echo "pushed $1"; return 0
  fi
  adb shell mkdir -p "/sdcard/Android/data/$PKG/files" 2>/dev/null
  if adb push "$1" "$REMOTE" >/dev/null 2>&1; then reload; echo "pushed $1 (adb push)"; return 0; fi
  echo "could not push: is the launcher installed and has it run once? ($out)" >&2
  return 1
}

sum() { cksum < "$1"; }

case "$1" in
  push) push "$2" ;;
  watch)
    [ -f "$2" ] || { echo "usage: $0 watch <file>" >&2; exit 1; }
    echo "watching $2 (Ctrl-C to stop)"
    last=""
    while true; do
      now=$(sum "$2" 2>/dev/null)
      if [ "$now" != "$last" ]; then last=$now; push "$2"; fi
      sleep 0.5
    done ;;
  screenshot)
    out=${2:-screen.png}
    adb exec-out screencap -p > "$out" && echo "saved $out" ;;
  preset)
    [ -n "$2" ] || { echo "usage: $0 preset <mono-cards|default|minimal|neon|paper>" >&2; exit 1; }
    reload --es preset "$2" ;;
  photo)
    [ -f "$2" ] || { echo "usage: $0 photo <image.jpg>" >&2; exit 1; }
    adb shell "content write --uri content://$PKG.design/photo.jpg" < "$2" && reload && echo "photo set" ;;
  reload) reload ;;
  grant)
    adb shell appops set $PKG GET_USAGE_STATS allow
    adb shell cmd notification allow_listener "$PKG/$PKG.NotifService"
    for p in android.permission.RECORD_AUDIO android.permission.READ_CONTACTS android.permission.READ_CALENDAR android.permission.ACCESS_COARSE_LOCATION; do
      adb shell pm grant $PKG $p 2>/dev/null
    done
    echo "granted usage access, notification access and runtime permissions" ;;
  home)
    sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
    [ "${sdk:-0}" -ge 29 ] && adb shell cmd role add-role-holder android.app.role.HOME $PKG >/dev/null 2>&1
    sleep 1
    if [ "${sdk:-0}" -ge 29 ] && adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | grep -q "$PKG"; then
      echo "set as the Home app"
    else
      echo "could not set the role from the shell; opening Home settings"
      adb shell am start -a android.settings.HOME_SETTINGS >/dev/null
    fi ;;
  *)
    sed -n '2,3p' "$0"; exit 1 ;;
esac
