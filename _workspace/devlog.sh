#!/bin/bash
# usage: devlog.sh <scenario>  — clear logcat, then stream TimemoreBle + crashes to _workspace/04_device_b_<scenario>.log
A=/Users/bbqman/Library/Android/sdk/platform-tools/adb
W=/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/_workspace
$A logcat -c
exec $A logcat -v epoch -s TimemoreBle:V AndroidRuntime:E > "$W/04_device_b_$1.log"
