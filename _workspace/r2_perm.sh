#!/bin/bash
# R2: permission fixes (F-B2-1 dead "allow" button, stale deniedOnce) on Fold5 via adb
A=/Users/bbqman/Library/Android/sdk/platform-tools/adb
P=com.timemoredot.app
W=/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/_workspace
PERMS="android.permission.BLUETOOTH_SCAN android.permission.BLUETOOTH_CONNECT"
ui() { $A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $A shell cat /sdcard/ui.xml; }
texts() { ui | grep -o 'text="[^"]*"' | sed 's/text=//' | grep -v '""' | tr '\n' ' '; echo; }
tap_text() { # tap first node whose text contains $1
  b=$(ui | tr '>' '\n' | grep "text=\"[^\"]*$1" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
  set -- $b; [ -z "$1" ] && { echo "  (no node: $1)"; return 1; }
  x=$(( ($1+$3)/2 )); y=$(( ($2+$4)/2 )); $A shell input tap $x $y; echo "  tap ($x,$y)"; }
prefs() { $A shell run-as $P cat shared_prefs/permission.xml 2>&1 | grep -iE "denied|No such" | head -3; }

echo "== case 1: system 'don't ask again' (user-fixed), app has no deniedOnce"
$A shell am force-stop $P
for p in $PERMS; do $A shell pm revoke $P $p; $A shell pm set-permission-flags $P $p user-set user-fixed; done
$A shell run-as $P rm -f shared_prefs/permission.xml
$A shell am start -n $P/.MainActivity >/dev/null; sleep 3
echo "screen:"; texts
tap_text "허용"; sleep 2
echo "after tap:"; texts
$A shell dumpsys window | grep -m1 -E "mCurrentFocus" 
echo "prefs:"; prefs

echo "== case 2: grant from Settings (simulate with pm grant), return to app"
for p in $PERMS; do $A shell pm clear-permission-flags $P $p user-set user-fixed; $A shell pm grant $P $p; done
$A shell input keyevent KEYCODE_HOME; sleep 1
$A shell am start -n $P/.MainActivity >/dev/null; sleep 3
echo "screen:"; texts
echo "prefs:"; prefs
$A shell dumpsys package $P | grep -E "BLUETOOTH_(SCAN|CONNECT): granted" | head -2
